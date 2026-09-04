import React, { useEffect, useState } from 'react'
import ReactDOM from 'react-dom/client'
import './styles.css'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

function App() {
  const [backendStatus, setBackendStatus] = useState('Checking backend…')
  const [backendOnline, setBackendOnline] = useState(false)

  useEffect(() => {
    fetch(`${API_BASE_URL}/api/health`)
      .then((response) => {
        if (!response.ok) throw new Error('Health check failed')
        return response.json()
      })
      .then((data) => {
        setBackendOnline(data.status === 'UP')
        setBackendStatus(data.status === 'UP' ? 'Backend online' : 'Backend unavailable')
      })
      .catch(() => {
        setBackendOnline(false)
        setBackendStatus('Backend unavailable')
      })
  }, [])

  return (
    <main className="app-shell">
      <section className="hero">
        <span className="eyebrow">CODEBASE INTELLIGENCE ENGINE</span>
        <h1>Understand your codebase like a map.</h1>
        <p>
          Analyze Java repositories, discover dependencies, detect architectural risks,
          and understand the blast radius of a change.
        </p>
        <div className="status-grid">
          <div className="status-card">
            <div>
              <strong>Phase 1 deployment foundation</strong>
              <span>React frontend + Spring Boot API + cloud-ready configuration.</span>
            </div>
            <span className={`status-dot ${backendOnline ? 'online' : ''}`} aria-label={backendStatus} />
          </div>
          <div className="status-card compact">
            <div>
              <strong>API</strong>
              <span>{backendStatus}</span>
            </div>
            <code>{API_BASE_URL}</code>
          </div>
        </div>
      </section>
    </main>
  )
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
