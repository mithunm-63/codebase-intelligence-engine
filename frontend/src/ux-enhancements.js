const NAV_ID = 'codeintel-quick-nav'

const sectionConfig = [
  ['graph-card', 'Map', 'Architecture graph', false],
  ['risk-dashboard', 'Risk', 'Code hotspots & risk', false],
  ['architecture-dashboard', 'Rules', 'Architecture rules & drift', true],
  ['impact-card', 'Impact', 'Change impact analysis', false],
  ['class-browser', 'Types', 'Type index', true],
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

function enhanceSections() {
  sectionConfig.forEach(([className, label, fallbackId, collapsed]) => {
    const card = document.querySelector(`.${className}`)
    if (!card) return
    if (!card.id) card.id = fallbackId.toLowerCase().replace(/[^a-z0-9]+/g, '-')
    if (className !== 'graph-card') addSectionToggle(card, label, collapsed)
  })

  const extra = [
    ['execution-paths-widget-panel', 'Paths'],
    ['codeintel-ask-widget', 'Assistant'],
    ['codeintel-history-widget', 'History'],
    ['codeintel-search-widget', 'Search'],
    ['search-widget-panel', 'Search'],
  ]
  return extra.filter(([id]) => document.getElementById(id))
}

function ensureNav() {
  const workspace = document.querySelector('.workspace-card')
  const result = document.querySelector('.result-card')
  if (!workspace || !result || document.getElementById(NAV_ID)) return

  const nav = document.createElement('nav')
  nav.id = NAV_ID
  nav.className = 'ux-nav'
  nav.setAttribute('aria-label', 'Analysis sections')
  nav.innerHTML = '<span class="ux-nav-label">Quick access</span>'

  const items = [
    ['Overview', result.id || 'analysis-overview'],
    ['Map', 'architecture-graph'],
    ['Risk', 'code-hotspots-risk'],
    ['Rules', 'architecture-rules-drift'],
    ['Impact', 'change-impact-analysis'],
    ['Types', 'type-index'],
    ['Paths', 'execution-paths-widget-panel'],
    ['Assistant', 'codeintel-ask-widget'],
    ['History', 'codeintel-history-widget'],
  ]

  if (!result.id) result.id = 'analysis-overview'
  const available = items.filter(([, id]) => document.getElementById(id))
  available.forEach(([label, id]) => {
    const button = document.createElement('button')
    button.type = 'button'
    button.textContent = label
    button.dataset.target = id
    button.addEventListener('click', () => scrollToId(id))
    nav.appendChild(button)
  })

  result.insertAdjacentElement('afterend', nav)
}

function enhance() {
  ensureNav()
  enhanceSections()

  const history = document.getElementById('codeintel-history-widget')
  if (history) history.id = 'codeintel-history-widget'
  const assistant = document.getElementById('codeintel-ask-widget')
  if (assistant) assistant.id = 'codeintel-ask-widget'
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

const observer = new MutationObserver(() => enhance())
observer.observe(document.body, { childList: true, subtree: true })

requestAnimationFrame(() => {
  enhance()
  setupBackToTop()
})
