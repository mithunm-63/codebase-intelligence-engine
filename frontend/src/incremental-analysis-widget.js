const state = { projectId: null, loading: false };
const originalFetch = window.fetch.bind(window);
const mount = document.getElementById('codeintel-incremental-widget');
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || window.__CODEINT_API_BASE_URL__ || '').replace(/\/$/, '');

function esc(value) {
  return String(value ?? '').replace(/[&<>\'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','\'':'&#39;','"':'&quot;'}[c]));
}
function statusLabel(status) {
  return ({
    NO_CHANGES: 'Up to date',
    NO_JAVA_CHANGES: 'No Java rebuild needed',
    BASELINE_INITIALIZED: 'Baseline initialized',
    JAVA_CHANGES_REANALYZED: 'Java changes re-analyzed',
    FULL_REBUILD_REQUIRED: 'Full rebuild completed'
  })[status] || status || 'Ready';
}
function renderShell() {
  if (!mount) return;
  mount.innerHTML = `
    <section class="incremental-card">
      <div class="ia-head">
        <div><span class="ia-eyebrow">REVISION AWARE ANALYSIS</span><h2>Update code intelligence</h2><p>Check the repository head and avoid unnecessary analysis when nothing relevant changed.</p></div>
        <button id="ia-refresh" class="ia-btn">Check for changes</button>
      </div>
      <div id="ia-status" class="ia-status"></div>
      <div id="ia-content"><div class="ia-empty">Analyze a GitHub repository above to enable revision-aware updates.</div></div>
    </section>`;
  document.getElementById('ia-refresh')?.addEventListener('click', refresh);
}
function setStatus(message, kind = 'info') {
  const node = document.getElementById('ia-status');
  if (!node) return;
  node.className = `ia-status ${kind}`;
  node.textContent = message || '';
}
function render(report) {
  const content = document.getElementById('ia-content');
  if (!content) return;
  const files = (report.changedFiles || []).map(path => `<li><code>${esc(path)}</code></li>`).join('');
  content.innerHTML = `
    <div class="ia-summary">
      <div><b>${esc(statusLabel(report.status))}</b><span>${esc(report.status)}</span></div>
      <div><b>${Number(report.commitsSinceLastAnalysis || 0).toLocaleString()}</b><span>commits since analysis</span></div>
      <div><b>${Number((report.changedFiles || []).length).toLocaleString()}</b><span>changed files sampled</span></div>
      <div><b>${esc(report.latestCommit || '—')}</b><span>latest revision</span></div>
    </div>
    <p class="ia-message">${esc(report.message || '')}</p>
    ${files ? `<div class="ia-section-title">Changed files</div><ul class="ia-files">${files}</ul>` : '<div class="ia-no-files">No changed files were returned for this update.</div>'}`;
}
async function refresh() {
  if (!state.projectId || state.loading) return;
  state.loading = true;
  const button = document.getElementById('ia-refresh');
  if (button) button.disabled = true;
  setStatus('Checking GitHub head and comparing revisions…', 'info');
  try {
    const response = await originalFetch(`${API_BASE_URL}/api/projects/${encodeURIComponent(state.projectId)}/analysis/incremental/refresh`, {
      method: 'POST', headers: { 'Accept': 'application/json' }
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || data.message || 'Incremental analysis failed.');
    render(data);
    const kind = data.reanalyzed ? 'success' : 'info';
    setStatus(data.reanalyzed ? 'Analysis updated successfully.' : 'Existing code intelligence was preserved.', kind);
  } catch (error) {
    const content = document.getElementById('ia-content');
    if (content) content.innerHTML = `<div class="ia-error">${esc(error.message || 'Could not update code intelligence.')}</div>`;
    setStatus('Revision check failed.', 'error');
  } finally {
    state.loading = false;
    if (button) button.disabled = false;
  }
}
window.fetch = async (...args) => {
  const response = await originalFetch(...args);
  try {
    const input = args[0];
    const url = typeof input === 'string' ? input : input?.url || '';
    if (/\/api\/projects\/[^/]+\/ingest(?:\/[^/?#]+)?(?:\?|$)/.test(url) && response.ok) {
      const match = url.match(/\/api\/projects\/([^/]+)\/ingest/);
      if (match) { state.projectId = decodeURIComponent(match[1]); setTimeout(() => renderShell(), 250); }
    }
  } catch (_) {}
  return response;
};
window.__CODEINT_INCREMENTAL_LOAD__ = refresh;
renderShell();
if (window.__CODEINT_ACTIVE_PROJECT_ID__) { state.projectId = window.__CODEINT_ACTIVE_PROJECT_ID__; }
