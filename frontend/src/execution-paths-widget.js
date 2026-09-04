const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

const STYLE_ID = 'execution-paths-widget-style'
const PANEL_ID = 'execution-paths-widget-panel'

function addStyles() {
  if (document.getElementById(STYLE_ID)) return
  const style = document.createElement('style')
  style.id = STYLE_ID
  style.textContent = `
    #${PANEL_ID}{margin-top:20px;padding:18px;border-radius:16px;background:rgba(0,0,0,.12);border:1px solid rgba(121,168,255,.12);color:#e8eef7}
    #${PANEL_ID} .ep-head{display:flex;justify-content:space-between;align-items:center;gap:16px;margin-bottom:14px}
    #${PANEL_ID} .ep-title{display:grid;gap:4px}
    #${PANEL_ID} .ep-title strong{font-size:16px}
    #${PANEL_ID} .ep-title span,#${PANEL_ID} .ep-muted{color:#72869c;font-size:12px}
    #${PANEL_ID} .ep-refresh{border:1px solid rgba(121,168,255,.28);border-radius:9px;padding:8px 11px;color:#dce8f7;background:rgba(121,168,255,.08);font-weight:700;cursor:pointer}
    #${PANEL_ID} .ep-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin:12px 0 16px}
    #${PANEL_ID} .ep-metric{padding:12px;border-radius:10px;background:rgba(255,255,255,.035);display:grid;gap:3px}
    #${PANEL_ID} .ep-metric strong{font-size:22px}
    #${PANEL_ID} .ep-metric span{color:#8295ab;font-size:11px}
    #${PANEL_ID} .ep-entry{padding:11px;border:1px solid rgba(255,255,255,.06);border-radius:10px;background:rgba(255,255,255,.02);margin-bottom:8px}
    #${PANEL_ID} .ep-entry-head{display:flex;justify-content:space-between;gap:12px;align-items:center}
    #${PANEL_ID} .ep-badge{padding:5px 8px;border-radius:999px;background:rgba(121,168,255,.09);border:1px solid rgba(121,168,255,.18);color:#bcd2ef;font-size:10px;font-weight:800}
    #${PANEL_ID} .ep-entry code,#${PANEL_ID} .ep-path code{color:#cfe0f3}
    #${PANEL_ID} .ep-endpoints{margin-top:8px;display:grid;gap:4px;color:#8295ab;font-size:11px}
    #${PANEL_ID} .ep-paths{display:grid;gap:8px}
    #${PANEL_ID} .ep-path{padding:12px;border:1px solid rgba(255,255,255,.06);border-radius:10px;background:rgba(255,255,255,.025)}
    #${PANEL_ID} .ep-flow{font-weight:700;line-height:1.5}
    #${PANEL_ID} .ep-meta{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}
    #${PANEL_ID} .ep-meta span{padding:5px 7px;border-radius:7px;background:rgba(255,255,255,.04);color:#8ea0b5;font-size:10px}
    #${PANEL_ID} .ep-layers{margin-top:8px;color:#7f93aa;font-size:11px}
    #${PANEL_ID} .ep-error{padding:11px;border-radius:10px;background:rgba(255,90,90,.06);border:1px solid rgba(255,90,90,.14);color:#ffbdbd}
    @media (max-width:820px){#${PANEL_ID} .ep-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}#${PANEL_ID} .ep-head{align-items:flex-start;flex-direction:column}}
  `
  document.head.appendChild(style)
}

function esc(value) {
  return String(value ?? '').replace(/[&<>\"]/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[ch]))
}

function projectIdFromDom() {
  const spans = [...document.querySelectorAll('.result-title span')]
  const value = spans.map(s => s.textContent.trim()).find(text => /^[0-9a-f-]{16,}$/i.test(text))
  return value || null
}

function panelMarkup(data) {
  const entries = (data.entryPoints || []).map(entry => `
    <div class="ep-entry">
      <div class="ep-entry-head"><strong>${esc(entry.name)}</strong><span class="ep-badge">${esc(entry.layer || 'API')}</span></div>
      <code>${esc(entry.qualifiedName)}</code>
      ${(entry.endpointMethods || []).length ? `<div class="ep-endpoints">${entry.endpointMethods.map(e => `<span>${esc(e)}</span>`).join('')}</div>` : '<div class="ep-muted">No HTTP mapping annotation was resolved on the indexed methods.</div>'}
    </div>`).join('')

  const paths = (data.paths || []).map(path => `
    <div class="ep-path">
      <div class="ep-flow">${esc(path.flow)}</div>
      <div class="ep-meta"><span>${path.hopCount} hops</span><span>${esc(path.terminalLayer || 'END')}</span><span>${path.edges?.length || 0} dependency edges</span></div>
      <div class="ep-layers">${(path.layers || []).map(esc).join(' → ')}</div>
      <div class="ep-muted">${(path.edges || []).slice(0,6).map(e => `${esc(e.relationshipType)} · line ${e.sourceLine}${e.sourceMember ? ` · ${esc(e.sourceMember)}` : ''}`).join(' &nbsp;|&nbsp; ')}</div>
    </div>`).join('')

  return `
    <div class="ep-head">
      <div class="ep-title"><strong>Execution paths</strong><span>Evidence-backed request flow from API entry points toward repositories.</span></div>
      <button class="ep-refresh" type="button" data-ep-refresh>Refresh</button>
    </div>
    <div class="ep-metrics">
      <div class="ep-metric"><strong>${data.entryPoints?.length ?? 0}</strong><span>API entry points</span></div>
      <div class="ep-metric"><strong>${data.pathCount ?? 0}</strong><span>Discovered paths</span></div>
      <div class="ep-metric"><strong>${data.repositoryPaths ?? 0}</strong><span>Paths to repositories</span></div>
      <div class="ep-metric"><strong>${data.servicePaths ?? 0}</strong><span>Paths through services</span></div>
    </div>
    ${entries ? `<div><div class="ep-muted" style="margin-bottom:8px">Entry points</div>${entries}</div>` : ''}
    <div style="margin-top:14px"><div class="ep-muted" style="margin-bottom:8px">Resolved flows</div>${paths || '<div class="ep-muted">No controller-to-repository path was found in the current dependency graph.</div>'}</div>
  `
}

async function load(projectId) {
  const panel = document.getElementById(PANEL_ID)
  if (!panel) return
  panel.innerHTML = '<div class="ep-muted">Analyzing execution paths…</div>'
  try {
    const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/analysis/execution-paths?maxPaths=25`)
    const data = await response.json()
    if (!response.ok) throw new Error(data.error || `Execution path analysis failed (HTTP ${response.status}).`)
    panel.innerHTML = panelMarkup(data)
    panel.querySelector('[data-ep-refresh]')?.addEventListener('click', () => load(projectId))
  } catch (error) {
    panel.innerHTML = `<div class="ep-error">${esc(error.message || 'Could not load execution paths.')}</div>`
  }
}

let lastProjectId = null

function mount() {
  addStyles()
  const projectId = projectIdFromDom()
  const anchor = document.querySelector('.class-browser') || document.querySelector('.analysis-card.risk-dashboard') || document.querySelector('.graph-card')
  if (!anchor) return
  let panel = document.getElementById(PANEL_ID)
  if (!projectId) {
    panel?.remove()
    lastProjectId = null
    return
  }
  if (!panel) {
    panel = document.createElement('section')
    panel.id = PANEL_ID
    anchor.parentElement?.insertBefore(panel, anchor.nextSibling)
  }
  if (projectId !== lastProjectId) {
    lastProjectId = projectId
    load(projectId)
  }
}

const observer = new MutationObserver(() => mount())
observer.observe(document.documentElement, { childList: true, subtree: true })
window.addEventListener('load', mount)
setTimeout(mount, 500)
