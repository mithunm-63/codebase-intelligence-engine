import React, { useEffect, useMemo, useState } from 'react'
import ReactDOM from 'react-dom/client'
import './styles.css'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

function Stat({ value, label }) {
  return <div><strong>{value ?? 0}</strong><span>{label}</span></div>
}

function shortLabel(value, max = 24) {
  if (!value) return ''
  return value.length > max ? `${value.slice(0, max - 1)}…` : value
}

function ArchitectureGraph({ graph, selectedId, onSelect }) {
  const [zoom, setZoom] = useState(1)
  const [pan, setPan] = useState({ x: 0, y: 0 })

  const layout = useMemo(() => {
    const nodes = graph?.nodes || []
    const width = 1040
    const nodeWidth = 180
    const nodeHeight = 56
    const cols = Math.max(1, Math.min(5, Math.ceil(Math.sqrt(nodes.length))))
    const gapX = 34
    const gapY = 38
    const positions = new Map()
    nodes.forEach((node, index) => {
      const col = index % cols
      const row = Math.floor(index / cols)
      positions.set(node.id, {
        x: 24 + col * (nodeWidth + gapX),
        y: 28 + row * (nodeHeight + gapY),
      })
    })
    const rows = Math.ceil(nodes.length / cols)
    const height = Math.max(460, 56 + rows * (nodeHeight + gapY))
    return { width, height, nodeWidth, nodeHeight, positions }
  }, [graph])

  if (!graph || graph.nodes.length === 0) {
    return <div className="graph-empty">No graph nodes are available yet. Run graph synchronization after analysis.</div>
  }

  const connectedIds = new Set()
  if (selectedId) {
    ;(graph.edges || []).forEach((edge) => {
      if (edge.sourceId === selectedId) connectedIds.add(edge.targetId)
      if (edge.targetId === selectedId) connectedIds.add(edge.sourceId)
    })
    connectedIds.add(selectedId)
  }

  return (
    <div className="graph-shell">
      <div className="graph-toolbar">
        <span>{graph.nodeCount} nodes · {graph.edgeCount} edges</span>
        <div>
          <button type="button" onClick={() => setZoom((value) => Math.max(.55, value - .1))}>−</button>
          <button type="button" onClick={() => { setZoom(1); setPan({ x: 0, y: 0 }) }}>Reset</button>
          <button type="button" onClick={() => setZoom((value) => Math.min(1.8, value + .1))}>+</button>
        </div>
      </div>
      <div className="graph-viewport">
        <svg viewBox={`0 0 ${layout.width} ${layout.height}`} role="img" aria-label="Architecture dependency graph">
          <defs>
            <marker id="graph-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto" markerUnits="strokeWidth">
              <path d="M0,0 L8,4 L0,8 z" fill="currentColor" />
            </marker>
          </defs>
          <g transform={`translate(${pan.x} ${pan.y}) scale(${zoom})`}>
            {(graph.edges || []).map((edge, index) => {
              const source = layout.positions.get(edge.sourceId)
              const target = layout.positions.get(edge.targetId)
              if (!source || !target) return null
              const x1 = source.x + layout.nodeWidth / 2
              const y1 = source.y + layout.nodeHeight
              const x2 = target.x + layout.nodeWidth / 2
              const y2 = target.y
              const highlighted = selectedId && (edge.sourceId === selectedId || edge.targetId === selectedId)
              return (
                <g key={`${edge.sourceId}-${edge.targetId}-${edge.relationshipType}-${index}`} className={highlighted ? 'graph-edge highlighted' : 'graph-edge'}>
                  <line x1={x1} y1={y1} x2={x2} y2={y2} markerEnd="url(#graph-arrow)" />
                  <title>{edge.relationshipType}{edge.occurrenceCount ? ` · ${edge.occurrenceCount} occurrences` : ''}</title>
                </g>
              )
            })}
            {graph.nodes.map((node) => {
              const position = layout.positions.get(node.id)
              const selected = node.id === selectedId
              const connected = connectedIds.has(node.id)
              return (
                <g
                  key={node.id}
                  transform={`translate(${position.x} ${position.y})`}
                  className={`graph-node ${selected ? 'selected' : ''} ${selectedId && !connected ? 'dimmed' : ''}`}
                  onClick={() => onSelect(node)}
                  onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') onSelect(node) }}
                  role="button"
                  tabIndex="0"
                >
                  <rect width={layout.nodeWidth} height={layout.nodeHeight} rx="12" />
                  <text x="12" y="22" className="node-title">{shortLabel(node.name, 27)}</text>
                  <text x="12" y="41" className="node-subtitle">{shortLabel(node.kind === 'PACKAGE' ? 'PACKAGE' : node.kind, 24)}</text>
                  <title>{node.qualifiedName}</title>
                </g>
              )
            })}
          </g>
        </svg>
      </div>
      <p className="muted">Click a node to highlight its immediate relationships. Class graphs are capped for browser readability.</p>
    </div>
  )
}

function App() {
  const [backendOnline, setBackendOnline] = useState(false)
  const [mode, setMode] = useState('github')
  const [projectName, setProjectName] = useState('')
  const [repositoryUrl, setRepositoryUrl] = useState('')
  const [zipFile, setZipFile] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)
  const [detail, setDetail] = useState(null)
  const [graph, setGraph] = useState(null)
  const [graphView, setGraphView] = useState('class')
  const [graphLoading, setGraphLoading] = useState(false)
  const [graphMessage, setGraphMessage] = useState('')
  const [selectedNode, setSelectedNode] = useState(null)

  useEffect(() => {
    fetch(`${API_BASE_URL}/api/health`)
      .then((response) => { if (!response.ok) throw new Error(); return response.json() })
      .then((data) => setBackendOnline(data.status === 'UP'))
      .catch(() => setBackendOnline(false))
  }, [])

  const loadGraph = async (projectId, view = graphView) => {
    if (!projectId) return
    setGraphLoading(true)
    setGraphMessage('')
    try {
      const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/analysis/graph?view=${view}&nodeLimit=120&edgeLimit=500`)
      const data = await response.json()
      if (!response.ok) throw new Error(data.error || 'Could not load the architecture graph.')
      setGraph(data)
      setSelectedNode(null)
    } catch (err) {
      setGraph(null)
      setGraphMessage(err.message || 'Could not load the architecture graph.')
    } finally {
      setGraphLoading(false)
    }
  }

  const syncGraph = async () => {
    if (!result?.projectId) return
    setGraphLoading(true)
    setGraphMessage('Synchronizing dependency edges into Neo4j…')
    try {
      const response = await fetch(`${API_BASE_URL}/api/projects/${result.projectId}/analysis/graph/sync`, { method: 'POST' })
      const data = await response.json()
      if (!response.ok) throw new Error(data.error || 'Could not synchronize the graph.')
      setGraphMessage(`Neo4j synchronized: ${data.classNodes} class nodes, ${data.classEdges} class edges, ${data.packageNodes} package nodes.`)
      await loadGraph(result.projectId, graphView)
    } catch (err) {
      setGraphMessage(err.message || 'Could not synchronize the graph.')
      setGraphLoading(false)
    }
  }

  const analyzeRepository = async (event) => {
    event.preventDefault()
    setError('')
    setResult(null)
    setDetail(null)
    setGraph(null)
    setGraphMessage('')
    if (!projectName.trim()) return setError('Enter a project name.')
    if (mode === 'github' && !repositoryUrl.trim()) return setError('Enter a public GitHub repository URL.')
    if (mode === 'zip' && !zipFile) return setError('Choose a ZIP file.')

    setLoading(true)
    try {
      const createResponse = await fetch(`${API_BASE_URL}/api/projects`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: projectName.trim(),
          sourceType: mode === 'github' ? 'GITHUB_PUBLIC' : 'ZIP_UPLOAD',
          sourceUrl: mode === 'github' ? repositoryUrl.trim() : null,
        }),
      })
      const project = await createResponse.json()
      if (!createResponse.ok) throw new Error(project.error || 'Could not create project.')

      let ingestResponse
      if (mode === 'github') {
        ingestResponse = await fetch(`${API_BASE_URL}/api/projects/${project.id}/ingest/github`, {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ repositoryUrl: repositoryUrl.trim() }),
        })
      } else {
        const form = new FormData(); form.append('file', zipFile)
        ingestResponse = await fetch(`${API_BASE_URL}/api/projects/${project.id}/ingest/zip`, { method: 'POST', body: form })
      }
      const ingestion = await ingestResponse.json()
      if (!ingestResponse.ok) {
        throw new Error(ingestion.error || `Repository analysis failed (HTTP ${ingestResponse.status}).`)
      }
      setResult(ingestion)
      await loadGraph(ingestion.projectId, 'class')
    } catch (err) {
      setError(err.message || 'Something went wrong.')
    } finally {
      setLoading(false)
    }
  }

  const openClass = async (className, classId) => {
    setDetail({ loading: true, title: className })
    try {
      const response = await fetch(`${API_BASE_URL}/api/projects/${result.projectId}/analysis/classes/${classId}`)
      const data = await response.json()
      if (!response.ok) throw new Error(data.error || 'Could not load class detail.')
      setDetail(data)
    } catch (err) {
      setError(err.message)
      setDetail(null)
    }
  }

  const handleGraphView = async (view) => {
    setGraphView(view)
    await loadGraph(result?.projectId, view)
  }

  return (
    <main className="app-shell">
      <section className="hero">
        <header>
          <span className="eyebrow">CODEBASE INTELLIGENCE ENGINE</span>
          <h1>Understand your codebase like a map.</h1>
          <p>Analyze Java repositories, resolve project dependencies, project the architecture into Neo4j, and explore the codebase as a graph.</p>
        </header>

        <section className="workspace-card">
          <div className="card-heading">
            <div><strong>Repository + architecture analysis</strong><span>Public demo limits remain intentionally small.</span></div>
            <span className={`status-pill ${backendOnline ? 'online' : ''}`}>{backendOnline ? 'API online' : 'API unavailable'}</span>
          </div>

          <div className="tabs">
            <button className={mode === 'github' ? 'active' : ''} onClick={() => setMode('github')} type="button">GitHub</button>
            <button className={mode === 'zip' ? 'active' : ''} onClick={() => setMode('zip')} type="button">ZIP upload</button>
          </div>

          <form onSubmit={analyzeRepository}>
            <label>Project name<input value={projectName} onChange={(e) => setProjectName(e.target.value)} placeholder="payment-platform" /></label>
            {mode === 'github' ? (
              <label>Public GitHub repository URL<input value={repositoryUrl} onChange={(e) => setRepositoryUrl(e.target.value)} placeholder="https://github.com/owner/repository" /></label>
            ) : (
              <label>Repository ZIP<input type="file" accept=".zip,application/zip" onChange={(e) => setZipFile(e.target.files?.[0] || null)} /></label>
            )}
            <button className="primary" disabled={loading || !backendOnline} type="submit">{loading ? 'Analyzing Java + dependencies…' : 'Analyze repository'}</button>
          </form>

          {error && <div className="message error">{error}</div>}

          {result && (
            <>
              <div className="result-card">
                <div className="result-title"><strong>Repository analysis complete</strong><span>{result.projectId}</span></div>
                {result.graphStatus === 'UNAVAILABLE' && (
                  <div className="message warning">
                    Source and dependency analysis completed, but Neo4j is currently unavailable. Configure NEO4J_URI, NEO4J_USERNAME and NEO4J_PASSWORD on Render, then use “Sync Neo4j”.
                    {result.graphError ? ` (${result.graphError})` : ''}
                  </div>
                )}
                <div className="metrics">
                  <Stat value={result.classCount + result.interfaceCount + result.enumCount + result.recordCount + result.annotationCount} label="Types" />
                  <Stat value={result.methodCount} label="Methods" />
                  <Stat value={result.constructorCount} label="Constructors" />
                  <Stat value={result.fieldCount} label="Fields" />
                </div>
                <div className="metrics secondary">
                  <Stat value={result.classCount} label="Classes" />
                  <Stat value={result.interfaceCount} label="Interfaces" />
                  <Stat value={result.enumCount} label="Enums" />
                  <Stat value={result.recordCount} label="Records" />
                </div>
                <div className="metrics secondary">
                  <Stat value={result.dependencyCount} label="Resolved edges" />
                  <Stat value={result.dependencyOccurrenceCount} label="Dependency occurrences" />
                  <Stat value={result.unresolvedReferenceCount} label="Ambiguous refs" />
                  <Stat value={result.status} label="Status" />
                </div>
                {result.parseErrorCount > 0 && <div className="message warning">{result.parseErrorCount} Java file(s) could not be parsed. The valid source was still indexed.</div>}
              </div>

              <div className="graph-card">
                <div className="section-title">
                  <div><strong>Architecture graph</strong><span>Neo4j-backed view of the analyzed dependency model.</span></div>
                  <div className="graph-actions">
                    <button type="button" className={graphView === 'class' ? 'active' : ''} onClick={() => handleGraphView('class')}>Classes</button>
                    <button type="button" className={graphView === 'package' ? 'active' : ''} onClick={() => handleGraphView('package')}>Packages</button>
                    <button type="button" onClick={syncGraph} disabled={graphLoading}>Sync Neo4j</button>
                  </div>
                </div>
                {graphMessage && <div className="message info">{graphMessage}</div>}
                {graphLoading && !graph && <div className="graph-empty">Loading architecture graph…</div>}
                <ArchitectureGraph graph={graph} selectedId={selectedNode?.id} onSelect={setSelectedNode} />
                {selectedNode && (
                  <div className="selected-node">
                    <div><strong>{selectedNode.name}</strong><span>{selectedNode.kind} · {selectedNode.packageName}</span></div>
                    <code>{selectedNode.qualifiedName}</code>
                  </div>
                )}
              </div>

              <div className="class-browser">
                <div className="section-title"><strong>Type index</strong><span>Click a type to inspect its fields, methods, and relationships.</span></div>
                <div className="class-list">
                  {(result.discoveredTypes || []).map((name, index) => {
                    const id = (result.classIds || [])[index]
                    return <button key={name} type="button" onClick={() => id && openClass(name, id)}>{name}</button>
                  })}
                </div>
              </div>
            </>
          )}

          {detail && !detail.loading && (
            <div className="detail-card">
              <div className="section-title"><strong>{detail.qualifiedName}</strong><span>{detail.kind} · {detail.lineCount} lines</span></div>
              <div className="detail-grid">
                <div><span>Source</span><code>{detail.sourcePath}</code></div>
                <div><span>Lines</span><strong>{detail.startLine}–{detail.endLine}</strong></div>
                <div><span>Fields</span><strong>{detail.fields.length}</strong></div>
                <div><span>Methods</span><strong>{detail.methods.length}</strong></div>
              </div>
              <h3>Fields</h3>
              {detail.fields.map((field) => <div className="item" key={field.id}><code>{field.type} {field.name}</code><span>{field.modifiers}</span></div>)}
              <h3>Methods</h3>
              {detail.methods.map((method) => <div className="item" key={method.id}><code>{method.signature}</code><span>{method.kind} · {method.lineCount} lines</span></div>)}
              <div className="dependency-columns">
                <div>
                  <h3>Depends on</h3>
                  {(detail.dependencies || []).length === 0 && <p className="muted">No project dependency resolved.</p>}
                  {(detail.dependencies || []).map((dependency) => (
                    <div className="item" key={dependency.id}><code>{dependency.className}</code><span>{dependency.type} · line {dependency.sourceLine} · ×{dependency.occurrenceCount}</span></div>
                  ))}
                </div>
                <div>
                  <h3>Used by</h3>
                  {(detail.dependents || []).length === 0 && <p className="muted">No project class depends on this type.</p>}
                  {(detail.dependents || []).map((dependency) => (
                    <div className="item" key={dependency.id}><code>{dependency.className}</code><span>{dependency.type} · line {dependency.sourceLine} · ×{dependency.occurrenceCount}</span></div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </section>
      </section>
    </main>
  )
}

ReactDOM.createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>)
