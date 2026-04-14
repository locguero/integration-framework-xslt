import { useEffect, useState } from 'react'
import {
  createCronType,
  deleteCronType,
  fetchCronTypes,
  toggleCronType,
  updateCronType,
} from '../api'
import type { CronRequestType } from '../types'

function Toast({ msg, ok, onDone }: { msg: string; ok: boolean; onDone: () => void }) {
  useEffect(() => { const t = setTimeout(onDone, 3000); return () => clearTimeout(t) }, [onDone])
  return <div className={`toast ${ok ? 'toast-success' : 'toast-error'}`}>{msg}</div>
}

const EMPTY_FORM = { name: '', sourceSystem: '', entityType: '', operation: '', notes: '' }

export default function CronAdmin() {
  const [types, setTypes]           = useState<CronRequestType[]>([])
  const [loading, setLoading]       = useState(true)
  const [error, setError]           = useState('')
  const [toast, setToast]           = useState<{ msg: string; ok: boolean } | null>(null)
  const [toggling, setToggling]     = useState<number | null>(null)
  const [deleting, setDeleting]     = useState<number | null>(null)
  const [editingId, setEditingId]   = useState<number | null>(null)
  const [showAdd, setShowAdd]       = useState(false)
  const [saving, setSaving]         = useState(false)

  // Add / edit form
  const [form, setForm] = useState(EMPTY_FORM)

  async function load() {
    try {
      setTypes(await fetchCronTypes())
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Load failed')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  function notify(msg: string, ok: boolean) { setToast({ msg, ok }) }

  async function handleToggle(t: CronRequestType) {
    setToggling(t.id)
    try {
      const updated = await toggleCronType(t.id)
      setTypes(prev => prev.map(x => x.id === updated.id ? updated : x))
      notify(`${updated.name} ${updated.active ? 'enabled' : 'disabled'}`, true)
    } catch (e: unknown) {
      notify(e instanceof Error ? e.message : 'Toggle failed', false)
    } finally {
      setToggling(null)
    }
  }

  async function handleDelete(t: CronRequestType) {
    if (!confirm(`Delete "${t.name}"? This cannot be undone.`)) return
    setDeleting(t.id)
    try {
      await deleteCronType(t.id)
      setTypes(prev => prev.filter(x => x.id !== t.id))
      notify(`Deleted ${t.name}`, true)
    } catch (e: unknown) {
      notify(e instanceof Error ? e.message : 'Delete failed', false)
    } finally {
      setDeleting(null)
    }
  }

  function startEdit(t: CronRequestType) {
    setEditingId(t.id)
    setForm({ name: t.name, sourceSystem: t.sourceSystem, entityType: t.entityType, operation: t.operation, notes: t.notes ?? '' })
    setShowAdd(false)
  }

  function startAdd() {
    setShowAdd(true)
    setEditingId(null)
    setForm(EMPTY_FORM)
  }

  function cancelForm() {
    setShowAdd(false)
    setEditingId(null)
    setForm(EMPTY_FORM)
  }

  async function handleSave() {
    if (!form.name || !form.sourceSystem || !form.entityType || !form.operation) {
      notify('Name, Source System, Entity Type, and Operation are required.', false)
      return
    }
    setSaving(true)
    try {
      if (editingId !== null) {
        const updated = await updateCronType(editingId, form)
        setTypes(prev => prev.map(x => x.id === updated.id ? updated : x))
        notify(`Updated ${updated.name}`, true)
      } else {
        const created = await createCronType({ ...form, active: true })
        setTypes(prev => [...prev, created].sort((a, b) => a.name.localeCompare(b.name)))
        notify(`Created ${created.name}`, true)
      }
      cancelForm()
    } catch (e: unknown) {
      notify(e instanceof Error ? e.message : 'Save failed', false)
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="loading">Loading...</div>
  if (error)   return <div className="error-msg">{error}</div>

  const activeCount   = types.filter(t => t.active).length
  const disabledCount = types.length - activeCount

  return (
    <>
      <h1 className="page-title">Cron Request Types</h1>

      {/* Summary strip */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
        <div className="card" style={{ padding: '14px 20px', flex: '0 0 auto' }}>
          <div className="stat-label">Schedule</div>
          <div style={{ fontFamily: 'monospace', fontSize: 15, marginTop: 4, color: '#1a1a2e' }}>
            0 0 * * * ?
          </div>
          <div style={{ fontSize: 11, color: '#6c7086', marginTop: 2 }}>
            Every hour — configure in application-cron.yml
          </div>
        </div>
        <div className="card" style={{ padding: '14px 20px', flex: '0 0 auto' }}>
          <div className="stat-label">Active types</div>
          <div style={{ fontSize: 26, fontWeight: 700, color: '#1a7a1a', marginTop: 4 }}>{activeCount}</div>
        </div>
        <div className="card" style={{ padding: '14px 20px', flex: '0 0 auto' }}>
          <div className="stat-label">Disabled types</div>
          <div style={{ fontSize: 26, fontWeight: 700, color: '#6c7086', marginTop: 4 }}>{disabledCount}</div>
        </div>
        <div style={{ flex: 1 }} />
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <button className="btn btn-primary" onClick={startAdd}>+ Add Request Type</button>
        </div>
      </div>

      {/* Add / Edit form */}
      {(showAdd || editingId !== null) && (
        <div className="card" style={{ marginBottom: 20 }}>
          <div className="upload-title">{editingId !== null ? 'Edit Request Type' : 'New Request Type'}</div>
          <div className="form-row">
            <div className="form-group">
              <label>Name</label>
              <input
                type="text"
                placeholder="e.g. ERP Order Create"
                value={form.name}
                onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              />
            </div>
            <div className="form-group">
              <label>Source System</label>
              <input
                type="text"
                placeholder="e.g. ERP"
                value={form.sourceSystem}
                onChange={e => setForm(f => ({ ...f, sourceSystem: e.target.value.toUpperCase() }))}
              />
            </div>
          </div>
          <div className="form-row">
            <div className="form-group">
              <label>Entity Type</label>
              <input
                type="text"
                placeholder="e.g. ORDER"
                value={form.entityType}
                onChange={e => setForm(f => ({ ...f, entityType: e.target.value.toUpperCase() }))}
              />
            </div>
            <div className="form-group">
              <label>Operation</label>
              <input
                type="text"
                placeholder="e.g. CREATE"
                value={form.operation}
                onChange={e => setForm(f => ({ ...f, operation: e.target.value.toUpperCase() }))}
              />
            </div>
          </div>
          <div className="form-group" style={{ marginBottom: 14 }}>
            <label>Notes</label>
            <input
              type="text"
              placeholder="Optional description..."
              value={form.notes}
              onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
            />
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-primary" disabled={saving} onClick={handleSave}>
              {saving ? 'Saving...' : editingId !== null ? 'Save Changes' : 'Create'}
            </button>
            <button className="btn" onClick={cancelForm} style={{ background: '#e9e9ee', color: '#1a1a2e' }}>
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Request types table */}
      <div className="card" style={{ padding: 0 }}>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Source System</th>
                <th>Entity Type</th>
                <th>Operation</th>
                <th>Status</th>
                <th>Notes</th>
                <th>Disabled At</th>
                <th>Disabled By</th>
                <th style={{ textAlign: 'right', paddingRight: 20 }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {types.length === 0 && (
                <tr>
                  <td colSpan={9} style={{ textAlign: 'center', color: '#6c7086', padding: '24px 0' }}>
                    No request types configured. Click "+ Add Request Type" to get started.
                  </td>
                </tr>
              )}
              {types.map(t => (
                <tr key={t.id}>
                  <td style={{ fontWeight: 600 }}>{t.name}</td>
                  <td><span className="mono">{t.sourceSystem}</span></td>
                  <td><span className="mono">{t.entityType}</span></td>
                  <td><span className="mono">{t.operation}</span></td>
                  <td>
                    <span className={`badge ${t.active ? 'badge-active' : 'badge-inactive'}`}>
                      {t.active ? 'active' : 'disabled'}
                    </span>
                  </td>
                  <td style={{ color: '#6c7086', maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {t.notes ?? '—'}
                  </td>
                  <td style={{ fontSize: 12, color: '#6c7086' }}>
                    {t.disabledAt ? new Date(t.disabledAt).toLocaleString() : '—'}
                  </td>
                  <td style={{ fontSize: 12, color: '#6c7086' }}>
                    {t.disabledBy ?? '—'}
                  </td>
                  <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                    <button
                      className="btn btn-sm"
                      style={{ marginRight: 6, background: '#f0f2f5', color: '#1a1a2e' }}
                      onClick={() => startEdit(t)}
                    >
                      Edit
                    </button>
                    <button
                      className={`btn btn-sm ${t.active ? '' : 'btn-success'}`}
                      style={t.active ? { marginRight: 6, background: '#fde8d0', color: '#a04000' } : { marginRight: 6 }}
                      disabled={toggling === t.id}
                      onClick={() => handleToggle(t)}
                    >
                      {toggling === t.id ? '...' : t.active ? 'Disable' : 'Enable'}
                    </button>
                    <button
                      className="btn btn-sm"
                      style={{ background: '#fdd6d8', color: '#9b1c1c' }}
                      disabled={deleting === t.id}
                      onClick={() => handleDelete(t)}
                    >
                      {deleting === t.id ? '...' : 'Delete'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* How it works info box */}
      <div className="card" style={{ marginTop: 20, borderLeft: '4px solid #89b4fa' }}>
        <div className="card-title">How it works</div>
        <ul style={{ fontSize: 13, color: '#444', lineHeight: 1.8, paddingLeft: 18 }}>
          <li>The scheduler fires on the cron expression in <code>application-cron.yml</code> (default: every hour).</li>
          <li>On each tick, all <strong>active</strong> request types are loaded from the database.</li>
          <li>For each active type, the ERP adapter is called with the matching sourceSystem / entityType / operation filter.</li>
          <li>Returned records are pushed onto the processing pipeline — idempotency-checked, XSLT-routed, then delivered.</li>
          <li>Disabling a type takes effect on the <strong>next scheduled tick</strong> — no restart required.</li>
        </ul>
      </div>

      {toast && <Toast msg={toast.msg} ok={toast.ok} onDone={() => setToast(null)} />}
    </>
  )
}
