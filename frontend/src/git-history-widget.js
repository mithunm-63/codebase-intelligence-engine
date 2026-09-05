const state = { projectId: null, mounted: false, loading: false };
const originalFetch = window.fetch.bind(window);
const mount = document.getElementById('codeintel-history-widget');

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
      <div class="history-head"><div><span class="eyebrow">GIT INTELLIGENCE</span><h2>Repository history</h2><p>Connect code structure with the changes that created it.</p></div>
      <button id="history-refresh" class="history-btn">Refresh</button></div>
      <div id="history-content"><div class="history-empty">Analyze a GitHub repository above to load commit intelligence.</div></div>
    </section>`;
  document.getElementById('history-refresh')?.addEventListener('click', () => load(true));
}
function render(report) {
  const content = document.getElementById('history-content');
  if (!content) return;
  const hotspotRows = (report.hotspots || []).map(f => `
    <tr><td><code>${esc(f.path)}</code></td><td>${compact(f.commits)}</td><td>${compact(f.additions)}</td><td>${compact(f.deletions)}</td><td><strong>${compact(f.churn)}</strong></td><td>${esc(f.lastAuthor)}</td></tr>`).join('');
  const commitRows = (report.recentCommits || []).map(c => `
    <a class="history-commit" href="${esc(c.url)}" target="_blank" rel="noreferrer"><div><strong>${esc(c.message || 'Untitled commit')}</strong><span>${esc(c.sha)} · ${esc(c.author)} · ${fmtDate(c.date)}</span></div><div class="commit-stats">+${compact(c.additions)} −${compact(c.deletions)} · ${compact(c.filesChanged)} files</div></a>`).join('');
  const activity = (report.activity || []).map(a => `<span class="activity-pill"><b>${compact(a.commits)}</b> ${esc(a.date)}</span>`).join('');
  content.innerHTML = `
    <div class="history-repo">${esc(report.repository)} <span>• ${compact(report.commitsAnalyzed)} commits sampled</span></div>
    <div class="history-metrics">
      <div><b>${compact(report.commitsAnalyzed)}</b><span>commits</span></div><div><b>${compact(report.authorCount)}</b><span>authors</span></div><div><b>${compact(report.filesChanged)}</b><span>files touched</span></div><div><b>${compact(report.churn)}</b><span>lines changed</span></div>
    </div>
    <div class="history-section"><div class="history-section-title">Change hotspots <span>files with the most churn in the sampled history</span></div>
      <div class="history-table-wrap"><table><thead><tr><th>File</th><th>Commits</th><th>+</th><th>−</th><th>Churn</th><th>Last author</th></tr></thead><tbody>${hotspotRows || '<tr><td colspan="6">No file-level history returned.</td></tr>'}</tbody></table></div>
    </div>
    <div class="history-columns">
      <div class="history-section"><div class="history-section-title">Recent commits</div><div class="history-commits">${commitRows || '<div class="history-empty">No commits returned.</div>'}</div></div>
      <div class="history-section"><div class="history-section-title">Recent activity</div><div class="activity-list">${activity || '<div class="history-empty">No dated activity returned.</div>'}</div></div>
    </div>`;
}
async function load(force = false) {
  if (!state.projectId || state.loading) return;
  state.loading = true;
  const content = document.getElementById('history-content');
  if (content && !force) content.innerHTML = '<div class="history-loading">Loading Git history…</div>';
  try {
    const response = await originalFetch(`${window.__CODEINT_API_BASE__ || ''}/api/projects/${encodeURIComponent(state.projectId)}/history?commits=25`, { headers: { 'Accept': 'application/json' } });
    if (!response.ok) throw new Error((await response.text()) || `History request failed (${response.status})`);
    render(await response.json());
  } catch (error) {
    if (content) content.innerHTML = `<div class="history-error">${esc(error.message || 'Could not load repository history.')}</div>`;
  } finally { state.loading = false; }
}
window.fetch = async (...args) => {
  const response = await originalFetch(...args);
  try {
    const input = args[0];
    const url = typeof input === 'string' ? input : input?.url || '';
    if (/\/api\/projects\/[^/]+\/ingest\/?(?:\?|$)/.test(url) && response.ok) {
      const match = url.match(/\/api\/projects\/([^/]+)\/ingest/);
      if (match) { state.projectId = decodeURIComponent(match[1]); setTimeout(() => load(false), 400); }
    }
  } catch (_) {}
  return response;
};
window.__CODEINT_HISTORY_LOAD__ = load;
renderShell();
if (window.__CODEINT_ACTIVE_PROJECT_ID__) { state.projectId = window.__CODEINT_ACTIVE_PROJECT_ID__; load(false); }
