import React, { useEffect, useState } from 'react'
import ReactDOM from 'react-dom/client'
import './styles.css'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

function Stat({ value, label }) {
  return <div><strong>{value ?? 0}</strong><span>{label}</span></div>
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

  useEffect(() => {
    fetch(`${API_BASE_URL}/api/health`)
      .then((response) => { if (!response.ok) throw new Error(); return response.json() })
      .then((data) => setBackendOnline(data.status === 'UP'))
      .catch(() => setBackendOnline(false))
  }, [])

  const analyzeRepository = async (event) => {
    event.preventDefault()
    setError('')
    setResult(null)
    setDetail(null)
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
      if (!ingestResponse.ok) throw new Error(ingestion.error || 'Repository ingestion failed.')
      setResult(ingestion)
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

  return (
    <main className="app-shell">
      <section className="hero">
        <header>
          <span className="eyebrow">CODEBASE INTELLIGENCE ENGINE</span>
          <h1>Understand your codebase like a map.</h1>
          <p>Phase 3 parses Java source with JavaParser and turns it into searchable classes, methods, fields, annotations, and source locations.</p>
        </header>

        <section className="workspace-card">
          <div className="card-heading">
            <div><strong>Repository + AST analysis</strong><span>Public demo limits remain intentionally small.</span></div>
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
            <button className="primary" disabled={loading || !backendOnline} type="submit">{loading ? 'Parsing Java source…' : 'Analyze repository'}</button>
          </form>

          {error && <div className="message error">{error}</div>}

          {result && (
            <>
              <div className="result-card">
                <div className="result-title"><strong>Repository analysis complete</strong><span>{result.projectId}</span></div>
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
                <div className="sample-list">
                  <span>Discovered types</span>
                  {(result.discoveredTypes || []).map((type) => <code key={type}>{type}</code>)}
                </div>
              </div>

              <div className="class-browser">
                <div className="section-title"><strong>Type index</strong><span>Click a type to inspect its fields and methods.</span></div>
                <div className="class-list">
                  {(result.discoveredTypes || []).map((name, index) => {
                    const id = (result.classIds || [])[index]
                    return <button key={name} type="button" onClick={() => id && openClass(name, id)}>{name}</button>
                  })}
                </div>
                <p className="muted">Use GET /api/projects/{result.projectId}/analysis/ast for the complete AST summary.</p>
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
                    <div className="item" key={dependency.id}>
                      <code>{dependency.className}</code>
                      <span>{dependency.type} · line {dependency.sourceLine} · ×{dependency.occurrenceCount}</span>
                    </div>
                  ))}
                </div>
                <div>
                  <h3>Used by</h3>
                  {(detail.dependents || []).length === 0 && <p className="muted">No project class depends on this type.</p>}
                  {(detail.dependents || []).map((dependency) => (
                    <div className="item" key={dependency.id}>
                      <code>{dependency.className}</code>
                      <span>{dependency.type} · line {dependency.sourceLine} · ×{dependency.occurrenceCount}</span>
                    </div>
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
