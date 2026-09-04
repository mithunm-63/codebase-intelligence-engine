import React, { useEffect, useState } from 'react'
import ReactDOM from 'react-dom/client'
import './styles.css'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

function App() {
  const [backendOnline, setBackendOnline] = useState(false)
  const [mode, setMode] = useState('github')
  const [projectName, setProjectName] = useState('')
  const [repositoryUrl, setRepositoryUrl] = useState('')
  const [zipFile, setZipFile] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)

  useEffect(() => {
    fetch(`${API_BASE_URL}/api/health`)
      .then((response) => {
        if (!response.ok) throw new Error('Health check failed')
        return response.json()
      })
      .then((data) => setBackendOnline(data.status === 'UP'))
      .catch(() => setBackendOnline(false))
  }, [])

  const analyzeRepository = async (event) => {
    event.preventDefault()
    setError('')
    setResult(null)

    if (!projectName.trim()) {
      setError('Enter a project name.')
      return
    }
    if (mode === 'github' && !repositoryUrl.trim()) {
      setError('Enter a public GitHub repository URL.')
      return
    }
    if (mode === 'zip' && !zipFile) {
      setError('Choose a ZIP file.')
      return
    }

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
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ repositoryUrl: repositoryUrl.trim() }),
        })
      } else {
        const form = new FormData()
        form.append('file', zipFile)
        ingestResponse = await fetch(`${API_BASE_URL}/api/projects/${project.id}/ingest/zip`, {
          method: 'POST',
          body: form,
        })
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

  return (
    <main className="app-shell">
      <section className="hero">
        <header>
          <span className="eyebrow">CODEBASE INTELLIGENCE ENGINE</span>
          <h1>Understand your codebase like a map.</h1>
          <p>
            Bring in a public Java repository or a ZIP. Phase 2 discovers the repository structure
            and Java source footprint while keeping the ingestion path ready for AST analysis.
          </p>
        </header>

        <section className="workspace-card">
          <div className="card-heading">
            <div>
              <strong>Repository ingestion</strong>
              <span>Public demo limits are intentionally small for safe cloud deployment.</span>
            </div>
            <span className={`status-pill ${backendOnline ? 'online' : ''}`}>
              {backendOnline ? 'API online' : 'API unavailable'}
            </span>
          </div>

          <div className="tabs">
            <button className={mode === 'github' ? 'active' : ''} onClick={() => setMode('github')} type="button">GitHub</button>
            <button className={mode === 'zip' ? 'active' : ''} onClick={() => setMode('zip')} type="button">ZIP upload</button>
          </div>

          <form onSubmit={analyzeRepository}>
            <label>
              Project name
              <input value={projectName} onChange={(e) => setProjectName(e.target.value)} placeholder="payment-platform" />
            </label>

            {mode === 'github' ? (
              <label>
                Public GitHub repository URL
                <input value={repositoryUrl} onChange={(e) => setRepositoryUrl(e.target.value)} placeholder="https://github.com/owner/repository" />
              </label>
            ) : (
              <label>
                Repository ZIP
                <input type="file" accept=".zip,application/zip" onChange={(e) => setZipFile(e.target.files?.[0] || null)} />
              </label>
            )}

            <button className="primary" disabled={loading || !backendOnline} type="submit">
              {loading ? 'Ingesting repository…' : 'Start repository ingestion'}
            </button>
          </form>

          {error && <div className="message error">{error}</div>}

          {result && (
            <div className="result-card">
              <div className="result-title">
                <strong>Repository ready for analysis</strong>
                <span>{result.projectId}</span>
              </div>
              <div className="metrics">
                <div><strong>{result.totalFiles}</strong><span>Total files</span></div>
                <div><strong>{result.javaFiles}</strong><span>Java files</span></div>
                <div><strong>{result.mainJavaFiles}</strong><span>Main Java</span></div>
                <div><strong>{result.testJavaFiles}</strong><span>Test Java</span></div>
              </div>
              <div className="sample-list">
                <span>Discovered sample files</span>
                {result.sampleFiles.map((file) => <code key={file}>{file}</code>)}
              </div>
              <div className="next-step">Next phase: parse these Java sources into AST nodes and resolved symbols.</div>
            </div>
          )}
        </section>
      </section>
    </main>
  )
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
