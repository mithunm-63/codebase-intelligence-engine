const NAV_ID = 'codeintel-quick-nav'
const VIEW_KEY = 'codeintel-view'

const sectionConfig = [
  ['graph-card', 'Architecture map', false],
  ['risk-dashboard', 'Code risk', false],
  ['architecture-dashboard', 'Architecture rules', true],
  ['impact-card', 'Change impact', true],
  ['class-browser', 'Type index', true],
]

const widgetConfig = [
  ['execution-paths-widget-panel', 'Execution paths', true],
  ['codeintel-history-widget', 'Repository history', true],
  ['codeintel-historical-risk-widget', 'Historical risk', false],
  ['codeintel-ask-widget', 'Codebase assistant', false],
  ['codeintel-search-widget', 'Codebase search', true],
  ['search-widget-panel', 'Codebase search', true],
]

const views = {
  overview: new Set(['result-card','architecture-graph','code-hotspots-risk','codeintel-historical-risk-widget','codeintel-ask-widget']),
  architecture: new Set(['architecture-graph','circular-dependencies','architecture-rules-drift','change-impact-analysis']),
  risk: new Set(['code-hotspots-risk','codeintel-historical-risk-widget','change-impact-analysis']),
  changes: new Set(['codeintel-history-widget','codeintel-historical-risk-widget','incremental-analysis']),
  explore: null,
}

function byId(id) { return document.getElementById(id) }

function scrollToId(id) {
  const node = byId(id)
  if (!node) return
  const nav = byId(NAV_ID)
  const offset = (nav?.getBoundingClientRect().height || 0) + 20
  const top = node.getBoundingClientRect().top + window.scrollY - offset
  window.scrollTo({ top: Math.max(0, top), behavior: 'smooth' })
}

function addSectionToggle(card, label, collapsed) {
  const title = card.querySelector('.section-title')
  if (!title || title.querySelector('.ux-collapse')) return
  const button = document.createElement('button')
  button.type = 'button'
  button.className = 'ux-collapse'
  button.setAttribute('aria-expanded', String(!collapsed))
  button.textContent = collapsed ? 'Show' : 'Hide'
  button.addEventListener('click', () => {
    const isCollapsed = card.classList.toggle('ux-collapsed')
    button.textContent = isCollapsed ? 'Show' : 'Hide'
    button.setAttribute('aria-expanded', String(!isCollapsed))
  })
  title.appendChild(button)
  if (collapsed) card.classList.add('ux-collapsed')
  const summary = document.createElement('div')
  summary.className = 'ux-section-summary'
  summary.textContent = `Expand to view ${label.toLowerCase()}.`
  card.appendChild(summary)
}

function addWidgetToggle(panel, label, collapsed) {
  if (!panel || panel.querySelector('.ux-widget-toggle')) return
  panel.classList.add('ux-widget-card')
  const header = panel.querySelector('.ep-head, .history-head, .hr-head, .ask-head, .search-head') || panel.firstElementChild
  if (!header) return
  const button = document.createElement('button')
  button.type = 'button'
  button.className = 'ux-widget-toggle'
  button.setAttribute('aria-expanded', String(!collapsed))
  button.textContent = collapsed ? 'Show' : 'Hide'
  button.title = `${collapsed ? 'Show' : 'Hide'} ${label}`
  button.addEventListener('click', () => {
    const isCollapsed = panel.classList.toggle('ux-widget-collapsed')
    button.textContent = isCollapsed ? 'Show' : 'Hide'
    button.setAttribute('aria-expanded', String(!isCollapsed))
  })
  header.appendChild(button)
  if (collapsed) panel.classList.add('ux-widget-collapsed')
}

function normalizeIds() {
  const mappings = [
    ['graph-card', 'architecture-graph'],
    ['risk-dashboard', 'code-hotspots-risk'],
    ['architecture-dashboard', 'architecture-rules-drift'],
    ['impact-card', 'change-impact-analysis'],
    ['class-browser', 'type-index'],
  ]
  mappings.forEach(([className, id]) => {
    const node = document.querySelector(`.${className}`)
    if (node && !node.id) node.id = id
  })
  const cycle = [...document.querySelectorAll('.analysis-card')].find(node => /Circular dependencies/i.test(node.textContent || ''))
  if (cycle && !cycle.id) cycle.id = 'circular-dependencies'
}

function decorateOverview() {
  const result = byId('analysis-overview')
  if (!result || result.querySelector('.ux-next-actions')) return
  const wrap = document.createElement('div')
  wrap.className = 'ux-next-actions'
  wrap.innerHTML = `
    <div class="ux-next-heading"><strong>What should I look at?</strong><span>Start with the highest-signal findings, then drill down.</span></div>
    <div class="ux-action-grid">
      <button type="button" data-focus="risk"><b>Risk</b><span>Find code most likely to cause problems.</span></button>
      <button type="button" data-focus="architecture"><b>Architecture</b><span>Inspect dependencies, cycles and rule violations.</span></button>
      <button type="button" data-focus="changes"><b>Changes</b><span>See what has been changing and what is getting riskier.</span></button>
    </div>`
  result.appendChild(wrap)
  wrap.querySelectorAll('[data-focus]').forEach(button => button.addEventListener('click', () => setView(button.dataset.focus)))
}

function availableIds() {
  return new Set([
    'analysis-overview', 'architecture-graph', 'code-hotspots-risk', 'architecture-rules-drift',
    'change-impact-analysis', 'type-index', 'circular-dependencies', 'execution-paths-widget-panel',
    'codeintel-ask-widget', 'codeintel-history-widget', 'codeintel-historical-risk-widget',
    'codeintel-search-widget', 'search-widget-panel', 'incremental-analysis',
  ].filter(id => byId(id)))
}

function ensureNav() {
  const workspace = document.querySelector('.workspace-card')
  const result = document.querySelector('.result-card')
  if (!workspace || !result) return
  result.id = result.id || 'analysis-overview'

  let nav = byId(NAV_ID)
  if (!nav) {
    nav = document.createElement('nav')
    nav.id = NAV_ID
    nav.className = 'ux-nav'
    nav.setAttribute('aria-label', 'Analysis views')
    result.insertAdjacentElement('afterend', nav)
  }

  nav.innerHTML = `
    <span class="ux-nav-label">Focus</span>
    <div class="ux-view-switch" role="tablist" aria-label="Analysis focus">
      <button type="button" data-view="overview">Overview</button>
      <button type="button" data-view="architecture">Architecture</button>
      <button type="button" data-view="risk">Risk</button>
      <button type="button" data-view="changes">Changes</button>
      <button type="button" data-view="explore">Explore all</button>
    </div>
    <div class="ux-nav-spacer"></div>
    <button type="button" class="ux-top-action" data-scroll="analysis-overview">Back to overview</button>`

  const available = availableIds()
  nav.querySelectorAll('[data-view]').forEach(button => {
    const view = button.dataset.view
    button.hidden = view !== 'overview' && view !== 'explore' && ![...views[view] || []].some(id => available.has(id))
    button.addEventListener('click', () => setView(view))
  })
  nav.querySelector('[data-scroll]')?.addEventListener('click', () => scrollToId('analysis-overview'))
}

function classifyNodes() {
  return [
    'architecture-graph', 'circular-dependencies', 'code-hotspots-risk', 'architecture-rules-drift',
    'change-impact-analysis', 'type-index', 'execution-paths-widget-panel', 'codeintel-ask-widget',
    'codeintel-history-widget', 'codeintel-historical-risk-widget', 'codeintel-search-widget',
    'search-widget-panel', 'incremental-analysis',
  ].map(id => byId(id)).filter(Boolean)
}

function setView(view) {
  const selected = view || 'overview'
  sessionStorage.setItem(VIEW_KEY, selected)
  document.body.dataset.codeintelView = selected
  const allowed = views[selected]
  classifyNodes().forEach(node => {
    const show = selected === 'explore' || allowed?.has(node.id)
    node.classList.toggle('ux-view-hidden', !show)
  })
  const nav = byId(NAV_ID)
  nav?.querySelectorAll('[data-view]').forEach(button => {
    const active = button.dataset.view === selected
    button.classList.toggle('active', active)
    button.setAttribute('aria-selected', String(active))
  })
  if (selected !== 'overview') requestAnimationFrame(() => scrollToId([...views[selected] || []].find(id => byId(id))))
}

function enhanceSections() {
  sectionConfig.forEach(([className, label, collapsed]) => {
    const card = document.querySelector(`.${className}`)
    if (card) addSectionToggle(card, label, collapsed)
  })
  widgetConfig.forEach(([id, label, collapsed]) => {
    const panel = byId(id)
    if (panel) addWidgetToggle(panel, label, collapsed)
  })
}

function enhance() {
  normalizeIds()
  ensureNav()
  enhanceSections()
  decorateOverview()
  const current = sessionStorage.getItem(VIEW_KEY) || 'overview'
  setView(current)
}

function setupBackToTop() {
  if (byId('ux-back-top')) return
  const button = document.createElement('button')
  button.id = 'ux-back-top'
  button.className = 'ux-back-top'
  button.type = 'button'
  button.setAttribute('aria-label', 'Back to overview')
  button.textContent = '↑'
  button.addEventListener('click', () => scrollToId('analysis-overview'))
  document.body.appendChild(button)
  const update = () => button.classList.toggle('visible', window.scrollY > 500)
  window.addEventListener('scroll', update, { passive: true })
  update()
}

let enhanceQueued = false
const observer = new MutationObserver(() => {
  if (enhanceQueued) return
  enhanceQueued = true
  requestAnimationFrame(() => {
    enhanceQueued = false
    enhance()
  })
})
observer.observe(document.body, { childList: true, subtree: true })

requestAnimationFrame(() => {
  enhance()
  setupBackToTop()
})
