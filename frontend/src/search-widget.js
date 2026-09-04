const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

let activeProjectId = null
const originalFetch = window.fetch.bind(window)

function installStyles() {
  if (document.getElementById('codeintel-search-styles')) return
  const style = document.createElement('style')
  style.id = 'codeintel-search-styles'
  style.textContent = `
    #codeintel-search-widget { width:min(1080px, calc(100% - 56px)); margin:20px auto 40px; }
    .search-widget-card { padding:18px; border:1px solid rgba(255,255,255,.07); border-radius:16px; background:rgba(0,0,0,.12); box-shadow:0 10px 35px rgba(0,0,0,.12); }
    .search-widget-heading { display:flex; justify-content:space-between; align-items:center; gap:18px; margin-bottom:14px; }
    .search-widget-heading > div { display:grid; gap:4px; }
    .search-widget-heading strong { font-size:17px; }
    .search-widget-heading span { color:#72869c; font-size:12px; }
    .search-live, .search-muted { padding:6px 8px; border-radius:999px; background:rgba(255,255,255,.045); white-space:nowrap; }
    .search-live { color:#8fe0ae !important; }
    .search-controls { display:grid; grid-template-columns:minmax(0,1fr) 170px auto; gap:8px; }
    .search-controls input, .search-controls select { width:100%; padding:11px 12px; color:#edf3fb; border:1px solid rgba(255,255,255,.08); border-radius:10px; background:rgba(0,0,0,.16); outline:none; }
    .search-controls input:focus, .search-controls select:focus { border-color:rgba(121,168,255,.55); }
    .search-primary { border:1px solid rgba(121,168,255,.3); border-radius:10px; padding:10px 16px; color:#07111f; background:#a9c7ff; font-weight:800; }
    .search-primary:disabled { opacity:.5; cursor:not-allowed; }
    .search-status { min-height:18px; margin:10px 2px; color:#8295ab; font-size:12px; }
    .search-results { display:grid; gap:8px; }
    .search-result { padding:12px; border:1px solid rgba(255,255,255,.06); border-radius:11px; background:rgba(255,255,255,.025); }
    .search-result-top { display:flex; align-items:center; gap:8px; margin-bottom:5px; }
    .search-result-top strong { color:#dce8f7; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .search-kind { padding:4px 6px; border-radius:7px; background:rgba(121,168,255,.09); color:#a9c7ff; font-size:9px; font-weight:800; }
    .search-score { margin-left:auto; color:#72869c; font-size:10px; }
    .search-result code { display:block; color:#b9cce3; font-size:11px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .search-result-meta { display:flex; flex-wrap:wrap; gap:6px; margin-top:8px; }
    .search-result-meta span { padding:4px 6px; border-radius:6px; background:rgba(255,255,255,.035); color:#8295ab; font-size:9px; }
    .search-open, .search-close { border:1px solid rgba(121,168,255,.22); border-radius:8px; padding:6px 9px; color:#cfe0f3; background:rgba(121,168,255,.06); font-size:10px; }
    .search-open { margin-top:9px; }
    .search-empty { padding:14px; border:1px dashed rgba(255,255,255,.08); border-radius:10px; text-align:center; color:#72869c; font-size:12px; }
    .search-detail-overlay { position:fixed; inset:0; z-index:9999; display:grid; place-items:center; padding:20px; background:rgba(3,8,15,.72); backdrop-filter:blur(6px); }
    .search-detail-panel { width:min(760px,100%); max-height:85vh; overflow:auto; padding:20px; border:1px solid rgba(255,255,255,.1); border-radius:16px; background:#0c1a2c; box-shadow:0 24px 80px rgba(0,0,0,.45); }
    .search-detail-heading { display:flex; justify-content:space-between; align-items:flex-start; gap:14px; margin-bottom:8px; }
    .search-detail-heading > div { display:grid; gap:4px; }
    .search-detail-heading span { color:#72869c; font-size:11px; }
    .search-detail-panel > code { display:block; margin-bottom:14px; color:#a9c7ff; overflow:auto; white-space:nowrap; }
    .search-detail-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:8px; }
    .search-detail-grid > div { display:grid; gap:4px; padding:10px; border-radius:9px; background:rgba(255,255,255,.035); }
    .search-detail-grid span { color:#72869c; font-size:10px; }
    .search-detail-grid strong { color:#cfe0f3; font-size:11px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .search-detail-panel h4 { margin:16px 0 7px; color:#8295ab; font-size:11px; }
    .search-detail-row { display:flex; justify-content:space-between; gap:10px; padding:8px 0; border-bottom:1px solid rgba(255,255,255,.06); }
    .search-detail-row code { min-width:0; color:#cfe0f3; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .search-detail-row span { color:#72869c; font-size:9px; white-space:nowrap; }
    @media (max-width:720px) { #codeintel-search-widget { width:calc(100% - 28px); } .search-controls { grid-template-columns:1fr; } .search-widget-heading { align-items:flex-start; flex-direction:column; } .search-detail-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } }
  `
  document.head.appendChild(style)
}

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
  installStyles()
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
        <input id="codeintel-search-query" placeholder="Search PaymentService, payments, Repository..." ${activeProjectId ? '' : 'disabled'} />
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
