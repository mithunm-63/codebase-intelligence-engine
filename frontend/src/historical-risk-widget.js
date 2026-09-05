const state = { projectId: null, loading: false };
const originalFetch = window.fetch.bind(window);
const mount = document.getElementById('codeintel-historical-risk-widget');
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || window.__CODEINT_API_BASE_URL__ || '').replace(/\/$/, '');

function esc(value) {
  return String(value ?? '').replace(/[&<>\'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','\'':'&#39;','"':'&quot;'}[c]));
}
function compact(n) { return new Intl.NumberFormat().format(Number(n || 0)); }
function badge(value) { return `<span class="hr-badge ${String(value || '').toLowerCase()}">${esc(value)}</span>`; }

function renderShell() {
  if (!mount) return;
  mount.innerHTML = `
    <section class="historical-risk-card">
      <div class="hr-head">
        <div><span class="eyebrow">HISTORICAL RISK INTELLIGENCE</span><h2>Risk + change pressure</h2><p>Combine structural risk with real repository change history to prioritize what deserves attention first.</p></div>
        <button id="hr-refresh" class="hr-btn">Refresh</button>
      </div>
      <div id="hr-status" class="hr-status"></div>
      <div id="hr-content"><div class="hr-empty">Analyze a GitHub repository above to calculate historical risk.</div></div>
    </section>`;
  document.getElementById('hr-refresh')?.addEventListener('click', () => load());
}
function setStatus(message, kind = 'info') {
  const n = document.getElementById('hr-status');
  if (!n) return;
  n.className = `hr-status ${kind}`;
  n.textContent = message || '';
}
function render(report) {
  const content = document.getElementById('hr-content');
  if (!content) return;
  const rows = (report.hotspots || []).map((h, i) => `
    <div class="hr-row">
      <div class="hr-rank">${i + 1}</div>
      <div class="hr-main"><div><strong>${esc(h.name)}</strong> ${badge(h.priority)} <span class="hr-trend ${String(h.trend || '').toLowerCase()}">${esc(h.trend)}</span></div><code>${esc(h.qualifiedName)}</code><small>${esc(h.factors?.join(' · ') || 'No dominant signal')}</small></div>
      <div class="hr-score"><strong>${compact(h.historicalRiskScore)}</strong><span>/100</span></div>
      <div class="hr-mini"><span>Current <b>${compact(h.currentRiskScore)}</b></span><span>Change <b>${compact(h.changePressure)}</b></span><span>${compact(h.commits)} commits</span><span>${compact(h.churn)} churn</span></div>
    </div>`).join('');
  content.innerHTML = `
    <div class="hr-summary"><div><b>${compact(report.commitsAnalyzed)}</b><span>commits analyzed</span></div><div><b>${compact(report.filesTouched)}</b><span>files touched</span></div><div><b>${compact(report.criticalClasses)}</b><span>critical</span></div><div><b>${compact(report.highPriorityClasses)}</b><span>high priority</span></div><div><b>${compact(report.risingClasses)}</b><span>rising</span></div><div><b>${compact(report.averageHistoricalRiskScore)}</b><span>avg historical risk</span></div></div>
    <div class="hr-section-title">Top priority classes <span>Current static risk is blended with historical change pressure.</span></div>
    <div class="hr-list">${rows || '<div class="hr-empty">No matched Java classes were touched by the stored Git history yet. Sync GitHub history first.</div>'}</div>`;
}
async function load() {
  if (!state.projectId || state.loading) return;
  state.loading = true;
  setStatus('Calculating historical risk…', 'info');
  try {
    const response = await originalFetch(`${API_BASE_URL}/api/projects/${encodeURIComponent(state.projectId)}/analysis/historical-risk`, { headers: { 'Accept': 'application/json' } });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || 'Historical risk analysis failed.');
    render(data);
    setStatus(`${compact(data.classesMatched)} classes matched against Git history.`, 'success');
  } catch (error) {
    const content = document.getElementById('hr-content');
    if (content) content.innerHTML = `<div class="hr-error">${esc(error.message || 'Could not calculate historical risk.')}</div>`;
    setStatus('Historical risk analysis failed.', 'error');
  } finally { state.loading = false; }
}
window.fetch = async (...args) => {
  const response = await originalFetch(...args);
  try {
    const input = args[0];
    const url = typeof input === 'string' ? input : input?.url || '';
    if (/\/api\/projects\/[^/]+\/ingest(?:\/[^/?#]+)?(?:\?|$)/.test(url) && response.ok) {
      const match = url.match(/\/api\/projects\/([^/]+)\/ingest/);
      if (match) { state.projectId = decodeURIComponent(match[1]); setTimeout(() => load(), 600); }
    }
  } catch (_) {}
  return response;
};
window.__CODEINT_HISTORICAL_RISK_LOAD__ = load;
renderShell();
if (window.__CODEINT_ACTIVE_PROJECT_ID__) { state.projectId = window.__CODEINT_ACTIVE_PROJECT_ID__; load(); }
