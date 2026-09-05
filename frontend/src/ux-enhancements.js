const NAV_ID = 'codeintel-top-nav'

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

const navItems = [
  ['Analyze', 'Start here', 'workspace-card'],
  ['Map', 'See dependencies', 'architecture-graph'],
  ['Risk', 'Find risky code', 'code-hotspots-risk'],
  ['Architecture', 'Check design rules', 'architecture-rules-drift'],
  ['Changes', 'Review Git history', 'codeintel-history-widget'],
  ['AI Assistant', 'Ask questions', 'codeintel-ask-widget'],
  ['Search', 'Find code quickly', 'codeintel-search-widget'],
]

function byId(id) { return document.getElementById(id) }

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

  const cycle = [...document.querySelectorAll('.analysis-card')]
    .find(node => /Circular dependencies/i.test(node.textContent || ''))
  if (cycle && !cycle.id) cycle.id = 'circular-dependencies'

  const workspace = document.querySelector('.workspace-card')
  if (workspace && !workspace.id) workspace.id = 'repository-analysis'
}

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
  button.setAttribute('aria-expanded', String(!collapsed))
  button.textContent = collapsed ? 'Show' : 'Hide'
  button.title = `${collapsed ? 'Show' : 'Hide'} ${label}`
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

function ensureTopNav() {
  const hero = document.querySelector('.hero')
  if (!hero) return

  let nav = byId(NAV_ID)
  if (!nav) {
    nav = document.createElement('nav')
    nav.id = NAV_ID
    nav.className = 'ux-top-nav'
    nav.setAttribute('aria-label', 'Codebase navigation')
    document.body.prepend(nav)
  }

  if (nav.dataset.ready === 'true') return

  nav.innerHTML = `
    <div class="ux-brand">
      <span class="ux-brand-mark">CI</span>
      <div><strong>Codebase Intelligence</strong><small>Navigate your analysis</small></div>
    </div>
    <div class="ux-nav-links" role="navigation"></div>`

  const links = nav.querySelector('.ux-nav-links')
  navItems.forEach(([label, description, targetId]) => {
    const button = document.createElement('button')
    button.type = 'button'
    button.className = 'ux-nav-item'
    button.dataset.target = targetId
    button.title = `${label}: ${description}`
    button.innerHTML = `<span>${label}</span><small>${description}</small>`
    button.addEventListener('click', () => scrollToId(targetId))
    links.appendChild(button)
  })

  const status = document.createElement('span')
  status.className = 'ux-nav-status'
  status.textContent = 'Analysis ready'
  nav.appendChild(status)
  nav.dataset.ready = 'true'
}

function setupActiveNavigation() {
  if (document.body.dataset.uxActiveObserver === 'true') return
  document.body.dataset.uxActiveObserver = 'true'

  const setActive = (id) => {
    const nav = byId(NAV_ID)
    if (!nav) return
    nav.querySelectorAll('.ux-nav-item').forEach(button => {
      const active = button.dataset.target === id
      button.classList.toggle('active', active)
      button.setAttribute('aria-current', active ? 'location' : 'false')
    })
  }

  const targets = navItems.map(([, , id]) => byId(id)).filter(Boolean)
  if (!targets.length || !('IntersectionObserver' in window)) return

  const observer = new IntersectionObserver((entries) => {
    const visible = entries
      .filter(entry => entry.isIntersecting)
      .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0]
    if (visible?.target?.id) setActive(visible.target.id)
  }, { rootMargin: '-90px 0px -60% 0px', threshold: [0.1, 0.35, 0.65] })

  targets.forEach(target => observer.observe(target))
  setActive(targets[0]?.id)
}

function setupBackToTop() {
  if (byId('ux-back-top')) return

  const button = document.createElement('button')
  button.id = 'ux-back-top'
  button.className = 'ux-back-top'
  button.type = 'button'
  button.setAttribute('aria-label', 'Back to analysis')
  button.textContent = '↑'
  button.addEventListener('click', () => scrollToId('repository-analysis'))
  document.body.appendChild(button)

  const update = () => button.classList.toggle('visible', window.scrollY > 500)
  window.addEventListener('scroll', update, { passive: true })
  update()
}

function enhance() {
  normalizeIds()
  ensureTopNav()

  sectionConfig.forEach(([className, label, collapsed]) => {
    const card = document.querySelector(`.${className}`)
    if (card) addSectionToggle(card, label, collapsed)
  })

  widgetConfig.forEach(([id, label, collapsed]) => {
    const panel = byId(id)
    if (panel) addWidgetToggle(panel, label, collapsed)
  })

  setupActiveNavigation()
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
