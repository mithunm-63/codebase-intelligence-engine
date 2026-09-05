const NAV_ID = 'codeintel-quick-nav'

const sectionConfig = [
  ['graph-card', 'Map', 'Architecture graph', false],
  ['risk-dashboard', 'Risk', 'Code hotspots & risk', false],
  ['architecture-dashboard', 'Rules', 'Architecture rules & drift', true],
  ['impact-card', 'Impact', 'Change impact analysis', false],
  ['class-browser', 'Types', 'Type index', true],
]

const widgetConfig = [
  ['execution-paths-widget-panel', 'Execution paths', true],
  ['codeintel-history-widget', 'Repository history', true],
  ['codeintel-historical-risk-widget', 'Historical risk', false],
  ['codeintel-ask-widget', 'Codebase assistant', false],
]

function scrollToId(id) {
  const node = document.getElementById(id)
  if (!node) return
  const nav = document.getElementById(NAV_ID)
  const offset = (nav?.getBoundingClientRect().height || 0) + 22
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
  const header = panel.querySelector('.ep-head, .history-head, .hr-head, .ask-head') || panel.firstElementChild
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

function enhanceSections() {
  sectionConfig.forEach(([className, label, fallbackId, collapsed]) => {
    const card = document.querySelector(`.${className}`)
    if (!card) return
    if (!card.id) card.id = fallbackId.toLowerCase().replace(/[^a-z0-9]+/g, '-')
    if (className !== 'graph-card') addSectionToggle(card, label, collapsed)
  })
  widgetConfig.forEach(([id, label, collapsed]) => {
    const panel = document.getElementById(id)
    if (panel) addWidgetToggle(panel, label, collapsed)
  })
}

function ensureNav() {
  const workspace = document.querySelector('.workspace-card')
  const result = document.querySelector('.result-card')
  if (!workspace || !result) return

  let nav = document.getElementById(NAV_ID)
  if (!nav) {
    if (!result.id) result.id = 'analysis-overview'
    nav = document.createElement('nav')
    nav.id = NAV_ID
    nav.className = 'ux-nav'
    nav.setAttribute('aria-label', 'Analysis sections')
    result.insertAdjacentElement('afterend', nav)
  }

  const items = [
    ['Overview', result.id],
    ['Map', 'architecture-graph'],
    ['Risk', 'code-hotspots-risk'],
    ['Rules', 'architecture-rules-drift'],
    ['Impact', 'change-impact-analysis'],
    ['Types', 'type-index'],
    ['Paths', 'execution-paths-widget-panel'],
    ['Assistant', 'codeintel-ask-widget'],
    ['History', 'codeintel-history-widget'],
    ['History risk', 'codeintel-historical-risk-widget'],
  ]

  nav.innerHTML = '<span class="ux-nav-label">Quick access</span>'
  items.forEach(([label, id]) => {
    if (!document.getElementById(id)) return
    const button = document.createElement('button')
    button.type = 'button'
    button.textContent = label
    button.dataset.target = id
    button.addEventListener('click', () => scrollToId(id))
    nav.appendChild(button)
  })
}

function setupBackToTop() {
  if (document.getElementById('ux-back-top')) return
  const button = document.createElement('button')
  button.id = 'ux-back-top'
  button.className = 'ux-back-top'
  button.type = 'button'
  button.setAttribute('aria-label', 'Back to top')
  button.textContent = '↑'
  button.addEventListener('click', () => window.scrollTo({ top: 0, behavior: 'smooth' }))
  document.body.appendChild(button)
  const update = () => button.classList.toggle('visible', window.scrollY > 500)
  window.addEventListener('scroll', update, { passive: true })
  update()
}

function enhance() {
  ensureNav()
  enhanceSections()
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
