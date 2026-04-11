import { useState } from 'react'
import Dashboard from './components/Dashboard'
import RequestsTable from './components/RequestsTable'
import XsltAdmin from './components/XsltAdmin'

type Page = 'dashboard' | 'requests' | 'xslt'

const NAV: { id: Page; icon: string; label: string }[] = [
  { id: 'dashboard', icon: '📊', label: 'Dashboard' },
  { id: 'requests',  icon: '📋', label: 'Request Log' },
  { id: 'xslt',      icon: '⚙️',  label: 'XSLT Admin' },
]

export default function App() {
  const [page, setPage] = useState<Page>('dashboard')

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="sidebar-logo">
          Integration<br /><span>Framework</span>
        </div>
        <nav>
          {NAV.map(n => (
            <div
              key={n.id}
              className={`nav-item ${page === n.id ? 'active' : ''}`}
              onClick={() => setPage(n.id)}
            >
              <span className="nav-icon">{n.icon}</span>
              {n.label}
            </div>
          ))}
        </nav>
      </aside>
      <main className="content">
        {page === 'dashboard' && <Dashboard />}
        {page === 'requests'  && <RequestsTable />}
        {page === 'xslt'      && <XsltAdmin />}
      </main>
    </div>
  )
}
