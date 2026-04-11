import { useEffect, useState } from 'react'
import { fetchRequests } from '../api'
import type { PageResponse, RequestLog } from '../types'

function fmt(iso: string | null) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}

function duration(r: RequestLog) {
  if (!r.completedAt) return '—'
  const ms = new Date(r.completedAt).getTime() - new Date(r.receivedAt).getTime()
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`
}

export default function RequestsTable() {
  const [data, setData] = useState<PageResponse<RequestLog> | null>(null)
  const [page, setPage] = useState(0)
  const [expanded, setExpanded] = useState<number | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    setError('')
    fetchRequests(page, 50)
      .then(setData)
      .catch(e => setError(e.message))
  }, [page])

  if (error) return <div className="error-msg">{error}</div>
  if (!data)  return <div className="loading">Loading...</div>

  return (
    <>
      <h1 className="page-title">Request Log</h1>
      <div className="card">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Correlation ID</th>
                <th>Source</th>
                <th>Entity</th>
                <th>Operation</th>
                <th>Status</th>
                <th>Received</th>
                <th>Duration</th>
              </tr>
            </thead>
            <tbody>
              {data.content.length === 0 && (
                <tr><td colSpan={7}><div className="empty">No requests yet</div></td></tr>
              )}
              {data.content.map(r => (
                <>
                  <tr key={r.id} onClick={() => setExpanded(expanded === r.id ? null : r.id)}>
                    <td className="mono">{r.correlationId}</td>
                    <td>{r.sourceSystem ?? '—'}</td>
                    <td>{r.entityType ?? '—'}</td>
                    <td>{r.operation ?? '—'}</td>
                    <td><span className={`badge badge-${r.status}`}>{r.status}</span></td>
                    <td>{fmt(r.receivedAt)}</td>
                    <td>{duration(r)}</td>
                  </tr>
                  {expanded === r.id && (
                    <tr key={`${r.id}-detail`}>
                      <td colSpan={7}>
                        <div className="detail-grid">
                          <div className="detail-item">
                            <label>Routing Slip</label>
                            <span className="mono">{r.routingSlip ?? '—'}</span>
                          </div>
                          <div className="detail-item">
                            <label>Completed At</label>
                            <span>{fmt(r.completedAt)}</span>
                          </div>
                          <div className="detail-item">
                            <label>Error</label>
                            <span>{r.errorMessage ?? '—'}</span>
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </>
              ))}
            </tbody>
          </table>
        </div>

        <div className="pagination">
          <span className="page-info">
            {data.totalElements} total · page {data.number + 1} of {Math.max(1, data.totalPages)}
          </span>
          <button className="page-btn" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
            ← Prev
          </button>
          <button
            className="page-btn"
            disabled={page >= data.totalPages - 1}
            onClick={() => setPage(p => p + 1)}
          >
            Next →
          </button>
        </div>
      </div>
    </>
  )
}
