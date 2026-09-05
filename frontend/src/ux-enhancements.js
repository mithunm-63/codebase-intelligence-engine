const NAV_ID = 'codeintel-quick-nav'

const navigationItems = [
  ['Overview', 'analysis-overview', 'Start here', 'Key results and repository status.'],
  ['Architecture', 'architecture-graph', 'Understand structure', 'Explore the dependency map and system relationships.'],
  ['Risk', 'code-hotspots-risk', 'Find danger areas', 'See the most risky classes and why they are risky.'],
  ['Impact', 'change-impact-analysis', 'Plan a change', 'See which classes could be affected by a change.'],
  ['Rules', 'architecture-rules-drift', 'Check boundaries', 'Find architecture rule violations and drift.'],
  ['Paths', 'execution-paths-widget-panel', 'Trace requests', 'Follow API requests through services and repositories.'],
  ['History', 'codeintel-history-widget', 'See what changed', 'Review commits, churn, authors and change hotspots.'],
  ['Historical risk', 'codeintel-historical-risk-widget', 'Find rising risk', 'Combine code risk with repository change pressure.'],
  ['Assistant', 'codeintel-ask-widget', 'Ask questions', 'Ask the analyzed codebase a grounded question.'],
  ['Search', 'codeintel-search-widget', 'Find code', 'Search classes, methods, endpoints and dependencies.'],
  ['Types', 'type-index', 'Inspect code', 'Open a class or type to inspect its members and relationships.'],
]

const collapsibleSections = [
  ['architecture-dashboard', 'Architecture rules', true],
  ['class-browser', 'Type index', true],
]

const collapsibleWidgets = [
  ['execution-paths-widget-panel', 'Execution paths', true],
  ['codeintel-history-widget', 'Repository history', true],
  ['codeintel-historical-risk-widget', 'Historical risk', false],
  ['codeintel-ask-widget', 'Codebase assistant', false],
]

function byId(id) { return document.getElementById(id) }

function scrollToId(id) {
  const node = byId(id)
  if (!node) return
  const nav = byId(NAV_ID)
  const offset = (nav?.getBoundingClientRect().height || 0) + 18
  const top = node.getBoundingClientRect().top + window.scrollY - offset
  window.scrollTo({ top: Math.max(0, top), behavior: 'smooth' })
}

function addSectionToggle(card, label, collapsed) {
  const title = card.querySelector('.section-title')
  if (!title || title.querySelector('.ux-collapse')) return
  const button = document.createElement('button')
  button.type = 'button'
  button.className = 'ux-collapse'
  button.textContent = collapsed ? 'Show' : 'Hide'
  button.setAttribute('aria-expanded', String(!collapsed))
  button.setAttribute('aria-label', `${collapsed ? 'Show' : 'Hide'} ${label}`)
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
  button.textContent = collapsed ? 'Show' : 'Hide'
  button.setAttribute('aria-expanded', String(!collapsed))
  button.setAttribute('aria-label', `${collapsed ? 'Show' : 'Hide'} ${label}`)
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

function ensureNav() {
  const result = document.querySelector('.result-card')
  if (!result) return
  result.id = result.id || 'analysis-overview'
  let nav = byId(NAV_ID)
  if (!nav) {
    nav = document.createElement('nav')
    nav.id = NAV_ID
    nav.className = 'ux-nav'
    nav.setAttribute('aria-label', 'Codebase navigation')
    result.insertAdjacentElement('afterend', nav)
  }

  // Do not rebuild the nav on every DOM mutation; rebuilding was causing the
  // clicked button's handler to be replaced before navigation completed.
  if (nav.dataset.ready === 'true') return

  nav.innerHTML = `
    <div class="ux-nav-heading">
      <div><strong>Explore your codebase</strong><span>Choose what you want to understand.</span></div>
    </div>
    <div class="ux-nav-grid" role="navigation"></div>`

  const grid = nav.querySelector('.ux-nav-grid')
  navigationItems.forEach(([label, id, kicker, description]) => {
    const button = document.createElement('button')
    button.type = 'button'
    button.className = 'ux-nav-item'
    button.dataset.target = id
    button.title = description
    button.innerHTML = `<b>${label}</b><span>${kicker}</span><small>${description}</small>`
    button.addEventListener('click', () => scrollToId(id))
    grid.appendChild(button)
  })
  nav.dataset.ready = 'true'
}

function highlightCurrentSection() {
  const nav = byId(NAV_ID)
  if (!nav) return
  const buttons = [...nav.querySelectorAll('.ux-nav-item')]
  let active = null
  let best = Number.POSITIVE_INFINITY
  buttons.forEach(button => {
    const node = byId(button.dataset.target)
    if (!node) return
    const distance = Math.abs(node.getBoundingClientRect().top - 150)
    if (distance < best) {
      best = distance
      active = button
    }
  })
  buttons.forEach(button => button.classList.toggle('active', button === active))
}

function enhance() {
  normalizeIds()
  ensureNav()
  enhanceSections()
}

function enhanceSections() {
  collapsibleSections.forEach(([className, label, collapsed]) => {
    const card = document.querySelector(`.${className}`)
    if (card) addSectionToggle(card, label, collapsed)
  })
  collapsibleWidgets.forEach(([id, label, collapsed]) => {
    const panel = byId(id)
    if (panel) addWidgetToggle(panel, label, collapsed)
  })
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
  const update = () => {
    button.classList.toggle('visible', window.scrollY > 500)
    highlightCurrentSection()
  }
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
  setTimeout(highlightCurrentSection, 250)
})
