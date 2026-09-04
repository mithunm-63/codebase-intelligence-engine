const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

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
          renderSearch()
        }
      }
    }
  } catch (_) {
    // Search is an optional navigation layer; never interfere with the main app.
  }
  return response
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>\"]/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '\"': '&quot;' }[char]))
}

function mount() {
  if (document.getElementById('codeintel-search-widget')) return
  const root = document.createElement('section')
  root.id = 'codeintel-search-widget'
  root.className = 'search-widget-host'
  document.body.appendChild(root)
  renderSearch()
}

function renderSearch() {
  const root = document.getElementById('codeintel-search-widget')
  if (!root) return
  root.innerHTML = `
    <div class="search-widget-card">
      <div class="search-widget-heading">
        <div>
          <strong>Codebase Search</strong>
          <span>Find classes, methods, endpoints, packages, and dependency relationships.</span>
        </div>
        ${activeProjectId ? '<span class="search-live">PROJECT READY</span>' : '<span class="search-muted">Analyze a repository first</span>'}
      </div>
      <div class="search-controls">
        <input id="codeintel-search-query" placeholder="Search PaymentService, /payments, Repository..." ${activeProjectId ? '' : 'disabled'} />
        <select id="codeintel-search-type" ${activeProjectId ? '' : 'disabled'}>
          <option value="ALL">Everything</option>
          <option value="CLASS">Classes</option>
          <option value="METHOD">Methods</option>
          <option value="ENDPOINT">Endpoints</option>
          <option value="PACKAGE">Packages</option>
          <option value="DEPENDENCY">Dependencies</option>
        </select>
        <button id="codeintel-search-button" class="search-primary" ${activeProjectId ? '' : 'disabled'}>Search</button>
      </div>
      <div id="codeintel-search-status" class="search-status"></div>
      <div id="codeintel-search-results" class="search-results"></div>
    </div>
  `

  if (!activeProjectId) return
  const input = root.querySelector('#codeintel-search-query')
  const button = root.querySelector('#codeintel-search-button')
  input.addEventListener('keydown', (event) => { if (event.key === 'Enter') search() })
  button.addEventListener('click', search)
  input.focus()
}

async function search() {
  const root = document.getElementById('codeintel-search-widget')
  const input = root.querySelector('#codeintel-search-query')
  const type = root.querySelector('#codeintel-search-type').value
  const status = root.querySelector('#codeintel-search-status')
  const results = root.querySelector('#codeintel-search-results')
  const query = input.value.trim()
  if (!query) {
    status.textContent = 'Enter a class, method, endpoint, package, or dependency term.'
    results.innerHTML = ''
    return
  }

  status.textContent = 'Searching analyzed codebase…'
  results.innerHTML = ''
  try {
    const response = await originalFetch(`${API_BASE_URL}/api/projects/${activeProjectId}/analysis/search?q=${encodeURIComponent(query)}&type=${encodeURIComponent(type)}&limit=30`)
    const data = await response.json()
    if (!response.ok) throw new Error(data.error || 'Search failed.')
    status.textContent = `${data.resultCount} result${data.resultCount === 1 ? '' : 's'} · ${data.query}`
    if (!data.results?.length) {
      results.innerHTML = '<div class="search-empty">No matching symbols or relationships.</div>'
      return
    }
    results.innerHTML = data.results.map((item) => `
      <article class="search-result">
        <div class="search-result-top">
          <span class="search-kind">${escapeHtml(item.kind)}</span>
          <strong>${escapeHtml(item.name)}</strong>
          <span class="search-score">${escapeHtml(item.score)}/100</span>
        </div>
        <code>${escapeHtml(item.qualifiedName || item.signature || '')}</code>
        <div class="search-result-meta">
          <span>${escapeHtml(item.sourcePath || '')}</span>
          ${item.relationshipType ? `<span>${escapeHtml(item.relationshipType)}</span>` : ''}
          ${item.sourceLine ? `<span>line ${escapeHtml(item.sourceLine)}</span>` : ''}
          ${item.sourceMember ? `<span>${escapeHtml(item.sourceMember)}</span>` : ''}
        </div>
        ${item.classId ? `<button class="search-open" data-class-id="${escapeHtml(item.classId)}">Open class detail</button>` : ''}
      </article>
    `).join('')

    results.querySelectorAll('.search-open').forEach((button) => {
      button.addEventListener('click', () => openClass(button.dataset.classId))
    })
  } catch (error) {
    status.textContent = error.message || 'Search failed.'
  }
}

async function openClass(classId) {
  const root = document.getElementById('codeintel-search-widget')
  const results = root.querySelector('#codeintel-search-results')
  const card = document.createElement('div')
  card.className = 'search-detail-overlay'
  card.innerHTML = '<div class="search-detail-panel"><strong>Loading class detail…</strong></div>'
  root.appendChild(card)
  try {
    const response = await originalFetch(`${API_BASE_URL}/api/projects/${activeProjectId}/analysis/classes/${encodeURIComponent(classId)}`)
    const data = await response.json()
    if (!response.ok) throw new Error(data.error || 'Could not load class detail.')
    card.innerHTML = `
      <div class="search-detail-panel">
        <div class="search-detail-heading">
          <div><strong>${escapeHtml(data.name)}</strong><span>${escapeHtml(data.kind)}</span></div>
          <button class="search-close">Close</button>
        </div>
        <code>${escapeHtml(data.qualifiedName)}</code>
        <div class="search-detail-grid">
          <div><span>Source</span><strong>${escapeHtml(data.sourcePath)}</strong></div>
          <div><span>Lines</span><strong>${escapeHtml(data.startLine)}–${escapeHtml(data.endLine)}</strong></div>
          <div><span>Fields</span><strong>${escapeHtml(data.fields?.length || 0)}</strong></div>
          <div><span>Methods</span><strong>${escapeHtml(data.methods?.length || 0)}</strong></div>
        </div>
        <h4>Methods</h4>
        ${(data.methods || []).slice(0, 12).map((m) => `<div class="search-detail-row"><code>${escapeHtml(m.signature || m.name)}</code><span>${escapeHtml(m.startLine)}–${escapeHtml(m.endLine)}</span></div>`).join('') || '<p class="search-empty">No methods indexed.</p>'}
        <h4>Depends on</h4>
        ${(data.dependencies || []).slice(0, 12).map((d) => `<div class="search-detail-row"><code>${escapeHtml(d.className)}</code><span>${escapeHtml(d.type)} · line ${escapeHtml(d.sourceLine)}</span></div>`).join('') || '<p class="search-empty">No project dependencies.</p>'}
      </div>
    `
    card.querySelector('.search-close').addEventListener('click', () => card.remove())
  } catch (error) {
    card.innerHTML = `<div class="search-detail-panel"><strong>Could not load class detail.</strong><p>${escapeHtml(error.message)}</p><button class="search-close">Close</button></div>`
    card.querySelector('.search-close').addEventListener('click', () => card.remove())
  }
}

window.addEventListener('DOMContentLoaded', mount)
