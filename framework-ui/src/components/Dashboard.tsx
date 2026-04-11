import { useEffect, useState } from 'react'
import {
  PieChart, Pie, Cell, Tooltip, ResponsiveContainer,
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Legend,
} from 'recharts'
import { fetchStats } from '../api'
import type { Stats } from '../types'

const STATUS_COLORS: Record<string, string> = {
  ACCEPTED: '#a6e3a1',
  REJECTED: '#fab387',
  FAILED:   '#f38ba8',
  RECEIVED: '#89b4fa',
}

export default function Dashboard() {
  const [stats, setStats] = useState<Stats | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    fetchStats()
      .then(setStats)
      .catch(e => setError(e.message))
  }, [])

  if (error) return <div className="error-msg">{error}</div>
  if (!stats) return <div className="loading">Loading...</div>

  const pieData = Object.entries(stats.byStatus).map(([name, value]) => ({ name, value }))

  const barData = Object.entries(stats.recentByHour).map(([hour, count]) => ({
    hour: new Date(hour).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    requests: count,
  }))

  const accepted = stats.byStatus['ACCEPTED'] ?? 0
  const rejected = stats.byStatus['REJECTED'] ?? 0
  const failed   = stats.byStatus['FAILED']   ?? 0

  return (
    <>
      <h1 className="page-title">Dashboard</h1>

      <div className="stat-grid">
        <div className="stat-card total">
          <div className="stat-label">Total Requests</div>
          <div className="stat-value">{stats.total}</div>
        </div>
        <div className="stat-card accepted">
          <div className="stat-label">Accepted</div>
          <div className="stat-value">{accepted}</div>
        </div>
        <div className="stat-card rejected">
          <div className="stat-label">Rejected</div>
          <div className="stat-value">{rejected}</div>
        </div>
        <div className="stat-card failed">
          <div className="stat-label">Failed</div>
          <div className="stat-value">{failed}</div>
        </div>
      </div>

      <div className="charts-row">
        <div className="card">
          <div className="card-title">Status Breakdown</div>
          {pieData.length === 0 ? (
            <div className="empty">No data yet</div>
          ) : (
            <ResponsiveContainer width="100%" height={260}>
              <PieChart>
                <Pie
                  data={pieData}
                  cx="50%" cy="50%"
                  outerRadius={90}
                  dataKey="value"
                  label={({ name, percent }) =>
                    `${name} ${(percent * 100).toFixed(0)}%`
                  }
                >
                  {pieData.map(entry => (
                    <Cell key={entry.name} fill={STATUS_COLORS[entry.name] ?? '#cdd6f4'} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="card">
          <div className="card-title">Requests — Last 24 Hours</div>
          {barData.length === 0 ? (
            <div className="empty">No data in the last 24 hours</div>
          ) : (
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={barData} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e9e9ee" />
                <XAxis dataKey="hour" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                <Tooltip />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Bar dataKey="requests" fill="#89b4fa" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>
    </>
  )
}
