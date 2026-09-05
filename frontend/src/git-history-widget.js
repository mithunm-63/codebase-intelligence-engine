const state = { projectId: null, mounted: false, loading: false };
const originalFetch = window.fetch.bind(window);
const mount = document.getElementById('codeintel-history-widget');
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || window.__CODEINT_API_BASE_URL__ || '').replace(/\/$/, '');

function esc(value) {
  return String(value ?? '').replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','\"':'&quot;'}[c]));
}
function fmtDate(value) {
  if (!value) return '—';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}
function compact(n) { return new Intl.NumberFormat().format(Number(n || 0)); }
function renderShell() {
  if (!mount) return;
  mount.innerHTML = `
    <section class="history-card">
      <div class="history-head">
        <div><span class="eyebrow">GIT INTELLIGENCE</span><h2>Repository history</h2><p>Persist repository history and incrementally analyze only new commits.</p></div>
        <button id="history-sync" class="history-btn">Sync GitHub</button>
      </div>
      <div id="history-status" class="history-status"></div>
      <div id="history-content"><div class="history-empty">Analyze a GitHub repository above to load commit intelligence.</div></div>
    </section>`;
  document.getElementById('history-sync')?.addEventListener('click', () => sync());
}
function setStatus(message, kind = 'info') {
  const node = document.getElementById('history-status');
  if (!node) return;
  node.className = `history-status ${kind}`;
  node.textContent = message || '';
}
function render(report, source = 'persistent') {
  const content = document.getElementById('history-content');
  if (!content) return;
  const hotspotRows = (report.hotspots || []).map(f => `
    <tr><td><code>${esc(f.path)}</code></td><td>${compact(f.commits)}</td><td>${compact(f.additions)}</td><td>${compact(f.deletions)}</td><td><strong>${compact(f.churn)}</strong></td><td>${esc(f.lastAuthor)}</td></tr>`).join('');
  const commitRows = (report.recentCommits || []).map(c => `
    <a class="history-commit" href="${esc(c.url)}" target="_blank" rel="noreferrer"><div><strong>${esc(c.message || 'Untitled commit')}</strong><span>${esc(c.sha)} · ${esc(c.author)} · ${fmtDate(c.date)}</span></div><div class="commit-stats">+${compact(c.additions)} −${compact(c.deletions)} · ${compact(c.filesChanged)} files</div></a>`).join('');
  const activity = (report.activity || []).map(a => `<span class="activity-pill"><b>${compact(a.commits)}</b> ${esc(a.date)}</span>`).join('');
  const syncMeta = source === 'persistent' && report.persisted
    ? `<div class="history-sync-meta"><span>${compact(report.newCommits || 0)} new commits this sync</span><span>${compact(report.totalStoredCommits || 0)} commits stored</span><span>Last sync ${fmtDate(report.lastSyncedAt)}</span></div>`
    : '';
  content.innerHTML = `
    <div class="history-repo">${esc(report.repository)} <span>• ${compact(report.commitsAnalyzed)} commits shown</span></div>
    ${syncMeta}
    <div class="history-metrics">
      <div><b>${compact(report.commitsAnalyzed)}</b><span>commits shown</span></div><div><b>${compact(report.authorCount)}</b><span>authors</span></div><div><b>${compact(report.filesChanged)}</b><span>files touched</span></div><div><b>${compact(report.churn)}</b><span>lines changed</span></div>
    </div>
    <div class="history-section"><div class="history-section-title">Change hotspots <span>files with the most churn in the stored history window</span></div>
      <div class="history-table-wrap"><table><thead><tr><th>File</th><th>Commits</th><th>+</th><th>−</th><th>Churn</th><th>Last author</th></tr></thead><tbody>${hotspotRows || '<tr><td colspan="6">No file-level history returned.</td></tr>'}</tbody></table></div>
    </div>
    <div class="history-columns">
      <div class="history-section"><div class="history-section-title">Recent commits</div><div class="history-commits">${commitRows || '<div class="history-empty">No commits returned.</div>'}</div></div>
      <div class="history-section"><div class="history-section-title">Recent activity</div><div class="activity-list">${activity || '<div class="history-empty">No dated activity returned.</div>'}</div></div>
    </div>`;
}
async function fetchStored() {
  const response = await originalFetch(`${API_BASE_URL}/api/projects/${encodeURIComponent(state.projectId)}/history/persistent?commits=25`, { headers: { 'Accept': 'application/json' } });
  if (!response.ok) throw new Error((await response.text()) || `History request failed (${response.status})`);
  return response.json();
}
async function sync() {
  if (!state.projectId || state.loading) return;
  state.loading = true;
  const button = document.getElementById('history-sync');
  if (button) button.disabled = true;
  setStatus('Syncing GitHub history…', 'info');
  try {
    const response = await originalFetch(`${API_BASE_URL}/api/projects/${encodeURIComponent(state.projectId)}/history/sync?commits=25`, { method: 'POST', headers: { 'Accept': 'application/json' } });
    if (response.ok) {
      const report = await response.json();
      render(report, 'persistent');
      setStatus(`${compact(report.newCommits || 0)} new commit(s) imported. ${compact(report.totalStoredCommits || 0)} total commits stored.`, 'success');
      return;
    }
    const body = await response.text();
    if (response.status === 400) {
      const fallback = await originalFetch(`${API_BASE_URL}/api/projects/${encodeURIComponent(state.projectId)}/history?commits=25`, { headers: { 'Accept': 'application/json' } });
      if (!fallback.ok) throw new Error(body || `History request failed (${response.status})`);
      render(await fallback.json(), 'ephemeral');
      setStatus('This source is not a GitHub repository, so history is shown without persistence.', 'info');
      return;
    }
    throw new Error(body || `History sync failed (${response.status})`);
  } catch (error) {
    const content = document.getElementById('history-content');
    if (content) content.innerHTML = `<div class="history-error">${esc(error.message || 'Could not sync repository history.')}</div>`;
    setStatus('History sync failed.', 'error');
  } finally {
    state.loading = false;
    if (button) button.disabled = false;
  }
}
async function load() {
  if (!state.projectId || state.loading) return;
  try {
    const report = await fetchStored();
    if (report.persisted) {
      render(report, 'persistent');
      setStatus(`${compact(report.totalStoredCommits || 0)} commits stored.`, 'success');
    } else {
      await sync();
    }
  } catch (_) {
    await sync();
  }
}
window.fetch = async (...args) => {
  const response = await originalFetch(...args);
  try {
    const input = args[0];
    const url = typeof input === 'string' ? input : input?.url || '';
    if (/\/api\/projects\/[^/]+\/ingest(?:\/[^/?#]+)?(?:\?|$)/.test(url) && response.ok) {
      const match = url.match(/\/api\/projects\/([^/]+)\/ingest/);
      if (match) { state.projectId = decodeURIComponent(match[1]); setTimeout(() => load(), 400); }
    }
  } catch (_) {}
  return response;
};
window.__CODEINT_HISTORY_LOAD__ = load;
window.__CODEINT_HISTORY_SYNC__ = sync;
renderShell();
if (window.__CODEINT_ACTIVE_PROJECT_ID__) { state.projectId = window.__CODEINT_ACTIVE_PROJECT_ID__; load(); }
