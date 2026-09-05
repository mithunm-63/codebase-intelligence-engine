const API_BASE_URL = (import.meta.env?.VITE_API_BASE_URL || window.__CODEINTEL_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
let activeProjectId = null
const originalFetch = window.fetch.bind(window)

window.fetch = async (...args) => {
  const response = await originalFetch(...args)
  try {
    const url = typeof args[0] === 'string' ? args[0] : args[0]?.url || ''
    if (url.includes('/api/projects/') && url.includes('/ingest/')) {
      const clone = response.clone()
      if (clone.ok) {
        const data = await clone.json()
        if (data?.projectId) {
          activeProjectId = data.projectId
          renderAsk()
        }
      }
    }
  } catch (_) {
    // The assistant is an optional layer; never interfere with the main analysis app.
  }
  return response
}

function esc(value) {
  return String(value ?? '').replace(/[&<>\"]/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '\"': '&quot;' }[char]))
}

function renderAsk() {
  const root = document.getElementById('codeintel-ask-widget')
  if (!root) return
  root.innerHTML = `
    <div class="ask-card">
      <div class="ask-heading">
        <div>
          <strong>Ask your codebase</strong>
          <span>Natural-language answers grounded in your analyzed dependency graph and metrics.</span>
        </div>
        ${activeProjectId ? '<span class="ask-live">ANALYSIS READY</span>' : '<span class="ask-muted">Analyze a repository first</span>'}
      </div>
      <div class="ask-examples">
        <button type="button" ${activeProjectId ? '' : 'disabled'} data-q="Why is PaymentService risky?">Why is PaymentService risky?</button>
        <button type="button" ${activeProjectId ? '' : 'disabled'} data-q="What breaks if I modify PaymentService?">What breaks if I modify PaymentService?</button>
        <button type="button" ${activeProjectId ? '' : 'disabled'} data-q="Where is the strongest coupling in this codebase?">Where is the strongest coupling?</button>
      </div>
      <div class="ask-controls">
        <textarea id="codeintel-ask-question" rows="3" maxlength="600" ${activeProjectId ? '' : 'disabled'} placeholder="Ask about dependencies, risk, cycles, architecture, or impact…"></textarea>
        <button id="codeintel-ask-button" class="ask-primary" ${activeProjectId ? '' : 'disabled'}>Ask</button>
      </div>
      <div id="codeintel-ask-status" class="ask-status"></div>
      <article id="codeintel-ask-answer" class="ask-answer" hidden></article>
    </div>
  `
  if (!activeProjectId) return
  const textarea = root.querySelector('#codeintel-ask-question')
  root.querySelector('#codeintel-ask-button').addEventListener('click', ask)
  textarea.addEventListener('keydown', (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') ask()
  })
  root.querySelectorAll('.ask-examples button').forEach((button) => {
    button.addEventListener('click', () => {
      textarea.value = button.dataset.q
      textarea.focus()
    })
  })
}

async function ask() {
  const root = document.getElementById('codeintel-ask-widget')
  const textarea = root.querySelector('#codeintel-ask-question')
  const button = root.querySelector('#codeintel-ask-button')
  const status = root.querySelector('#codeintel-ask-status')
  const answer = root.querySelector('#codeintel-ask-answer')
  const question = textarea.value.trim()
  if (!question) {
    status.textContent = 'Enter a question about the analyzed codebase.'
    answer.hidden = true
    return
  }
  button.disabled = true
  status.textContent = 'Building evidence from the code graph…'
  answer.hidden = true
  try {
    const response = await originalFetch(`${API_BASE_URL}/api/projects/${encodeURIComponent(activeProjectId)}/analysis/ask`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question }),
    })
    const data = await response.json()
    if (!response.ok) throw new Error(data.error || 'Could not answer the question.')
    status.textContent = `Answered using ${data.model || 'configured model'}`
    answer.innerHTML = `
      <div class="ask-answer-heading"><strong>Answer</strong><span>Grounded response</span></div>
      <div class="ask-answer-text">${esc(data.answer).replace(/\n/g, '<br/>')}</div>
      <div class="ask-evidence"><strong>Evidence used</strong>${(data.evidence || []).map((item) => `<code>${esc(item)}</code>`).join('') || '<span>No named symbol evidence returned.</span>'}</div>
    `
    answer.hidden = false
  } catch (error) {
    status.textContent = error.message || 'Could not answer the question.'
  } finally {
    button.disabled = false
  }
}

window.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('codeintel-ask-widget')
  if (root) renderAsk()
})
