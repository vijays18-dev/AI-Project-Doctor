

import { useState } from 'react'
import './App.css'

const healthAreas = [
  ['Security', 'security', 'green', '🛡️'],
  ['Code Quality', 'codeQuality', 'blue', '💻'],
  ['Testing', 'testing', 'purple', '🧪'],
  ['Dependencies', 'dependencies', 'orange', '📦'],
  ['Documentation', 'documentation', 'pink', '📚'],
]

function App() {
  const [repoUrl, setRepoUrl] = useState(
    'https://github.com/facebook/react'
  )
  const [backendMessage, setBackendMessage] = useState('')
  const [loading, setLoading] = useState(false)
  const [connectionStatus, setConnectionStatus] = useState('idle')
  const [analysis, setAnalysis] = useState(null)
  const [history, setHistory] = useState([])

  const handleAnalyze = async (event) => {
    event.preventDefault()

    const url = repoUrl.trim()

    if (!/^https?:\/\/github\.com\/[^/\s]+\/[^/\s]+\/?$/.test(url)) {
      setBackendMessage(
        'Enter a valid GitHub repository URL, such as https://github.com/facebook/react'
      )
      setConnectionStatus('error')
      setAnalysis(null)
      return
    }

    setLoading(true)
    setBackendMessage('')
    setConnectionStatus('idle')
    setAnalysis(null)

    try {
      const response = await fetch(
        'http://localhost:8081/api/analyze',
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            repoUrl: url,
          }),
        }
      )

      const data = await response.json()

      if (!response.ok || data.status === 'error') {
        throw new Error(
          data.message || `Request failed with status ${response.status}`
        )
      }

      setBackendMessage(data.message || 'Repository analysis completed.')
      setAnalysis(data)
      setHistory((previous) => [
        {
          repository: data.repository,
          owner: data.owner,
          overall: data.health?.overall ?? 0,
          filesAnalyzed: data.health?.filesAnalyzed ?? data.fileCount,
          scannedAt: new Date().toLocaleString(),
        },
        ...previous.filter((item) => item.repository !== data.repository),
      ].slice(0, 5))
      setConnectionStatus('success')
    } catch (error) {
      console.error('Repository request failed:', error)

      setBackendMessage(
        error.message ||
          'Could not connect. Make sure Spring Boot is running on port 8081.'
      )
      setConnectionStatus('error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-icon">✚</div>
          <div>
            <h2>Project Doctor</h2>
            <p>AI Developer Tools</p>
          </div>
        </div>

        <div className="nav-section">
          <p className="nav-heading">WORKSPACE</p>

          <a className="nav-item active" href="#dashboard">
            <span>▦</span> Dashboard
          </a>

          <a className="nav-item" href="#analysis">
            <span>⌕</span> Repository Analysis
          </a>

          <a className="nav-item" href="#health">
            <span>♡</span> Project Health
          </a>

          <a className="nav-item" href="#activity">
            <span>◷</span> Scan History
          </a>
        </div>

        <div className="sidebar-bottom">
          <div className="help-card">
            <div className="help-icon">✦</div>
            <h3>AI Assistant</h3>
            <p>
              Your project health assistant will help identify
              problems and suggest improvements.
            </p>
          </div>

          <div className="profile">
            <div className="avatar">PD</div>
            <div>
              <strong>Developer</strong>
              <p>Project Workspace</p>
            </div>
          </div>
        </div>
      </aside>

      <main className="main-content" id="dashboard">
        <header className="topbar">
          <div>
            <p className="breadcrumb">Workspace / Dashboard</p>
            <h1>Project Health Dashboard</h1>
          </div>

          <div className="topbar-right">
            <span className="live-dot"></span>
            <span>Local Development</span>
          </div>
        </header>

        <section className="welcome-section">
          <div>
            <p className="eyebrow">YOUR PROJECT, HEALTHIER</p>
            <h2>Good to see you, Developer! 👋</h2>
            <p>
              Connect your repository and prepare to inspect its
              health, quality, security, and documentation.
            </p>
          </div>

          <div className="welcome-art">🩺</div>
        </section>

        <section className="analyze-panel" id="analysis">
          <div className="section-heading">
            <div>
              <h2>Analyze a Repository</h2>
              <p>
                Enter a public GitHub repository to get started.
              </p>
            </div>
            <span className="github-label">GitHub</span>
          </div>

          <form onSubmit={handleAnalyze}>
            <label htmlFor="repoUrl">Repository URL</label>

            <div className="repo-input-row">
              <span className="input-icon">⌘</span>

              <input
                id="repoUrl"
                type="url"
                value={repoUrl}
                onChange={(event) => setRepoUrl(event.target.value)}
                placeholder="https://github.com/username/repository"
                required
              />

              <button type="submit" disabled={loading}>
                {loading ? 'Analyzing...' : 'Analyze Project →'}
              </button>
            </div>
          </form>

          <p className="input-hint">
            Example: https://github.com/facebook/react
          </p>

          {backendMessage && (
            <div
              className={`connection-message ${connectionStatus}`}
              role="status"
            >
              <strong>
                {connectionStatus === 'success'
                  ? '✓ API request successful'
                  : '⚠ Request failed'}
              </strong>
              <p>{backendMessage}</p>

              {connectionStatus === 'success' && (
                <>
                  <p>
                    <strong>Repository:</strong> {analysis?.owner}/{analysis?.repository}
                    {' · '}<strong>Files:</strong> {analysis?.health?.filesAnalyzed ?? analysis?.fileCount}
                  </p>
                </>
              )}
            </div>
          )}
        </section>

        <section className="health-section" id="health">
          <div className="section-heading">
            <div>
              <h2>Project Health Overview</h2>
              <p>
                Heuristic scores based on repository structure and configuration.
              </p>
            </div>

            <span className="sample-badge">
              {analysis ? 'LIVE ANALYSIS' : 'WAITING FOR SCAN'}
            </span>
          </div>

          <div className="overview-grid">
            <article className="overall-card">
              <div className="overall-top">
                <span>Overall Health</span>
                <span className="health-icon">♥</span>
              </div>

              <div className="score-display">
                <strong>{analysis?.health?.overall ?? '—'}</strong>
                <span>/ 100</span>
              </div>

              <div className="progress-track">
                <div
                  className="progress-fill"
                  style={{ width: `${analysis?.health?.overall ?? 0}%` }}
                ></div>
              </div>

              <p className="score-caption">
                {analysis ? `${analysis.health.filesAnalyzed} files inspected` : 'Run a scan to calculate health'}
              </p>
            </article>

            {healthAreas.map(([label, key, tone, icon]) => {
              const score = analysis?.health?.[key]
              return (
              <article className="health-card" key={key}>
                <div className="health-card-top">
                  <div className={`area-icon ${tone}`}>
                    {icon}
                  </div>
                  <span className="card-menu">•••</span>
                </div>

                <p className="area-label">{label}</p>

                <div className="area-score">
                  <strong>{score ?? '—'}</strong>
                  <span>/100</span>
                </div>

                <div className="mini-progress">
                  <div
                    className={`mini-progress-fill ${tone}`}
                    style={{ width: `${score ?? 0}%` }}
                  ></div>
                </div>

                <p className="area-detail">
                  {score == null ? 'Awaiting repository scan' : 'Calculated from repository signals'}
                </p>
              </article>
              )
            })}
          </div>
        </section>

        <section className="bottom-grid" id="activity">
          <article className="activity-panel">
            <div className="section-heading">
              <div>
                <h2>Recent Activity</h2>
                <p>Your project workspace activity</p>
              </div>
            </div>

            {history.length === 0 ? (
              <div className="empty-activity">
                <div className="empty-icon">◷</div>
                <h3>No scans yet</h3>
                <p>Scan a public GitHub repository to start your project history.</p>
              </div>
            ) : (
              <div className="history-list">
                {history.map((item) => (
                  <div className="history-item" key={`${item.owner}-${item.repository}-${item.scannedAt}`}>
                    <div>
                      <strong>{item.owner}/{item.repository}</strong>
                      <p>{item.filesAnalyzed} files · {item.scannedAt}</p>
                    </div>
                    <span className="history-score">{item.overall}/100</span>
                  </div>
                ))}
              </div>
            )}
          </article>

          <article className="insight-panel">
            <div className="insight-symbol">✦</div>
            <p className="eyebrow">PROJECT DOCTOR INSIGHT</p>
            <h2>{analysis ? 'Recommendations' : 'Your next step'}</h2>
            <p>
              {analysis
                ? analysis.recommendations?.slice(0, 3).join(' ')
                : 'Connect a public GitHub repository to receive health scores, findings, and actionable recommendations.'}
            </p>
            <span className="insight-status">
              <span className="live-dot"></span>
              {analysis ? `${analysis.findings?.length ?? 0} findings detected` : 'Ready to analyze'}
            </span>
          </article>
        </section>

        <footer className="footer">
          <span>AI Project Doctor</span>
          <span>Built with React + Spring Boot</span>
        </footer>
      </main>
    </div>
  )
}

export default App
