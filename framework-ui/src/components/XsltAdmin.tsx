import { useEffect, useState } from 'react'
import { fetchXslt, fetchXsltVersions, uploadXslt, activateXslt } from '../api'
import type { XsltVersion } from '../types'

type FileMap = Record<string, XsltVersion[]>

function Toast({ msg, ok, onDone }: { msg: string; ok: boolean; onDone: () => void }) {
  useEffect(() => { const t = setTimeout(onDone, 3000); return () => clearTimeout(t) }, [onDone])
  return <div className={`toast ${ok ? 'toast-success' : 'toast-error'}`}>{msg}</div>
}

export default function XsltAdmin() {
  const [fileMap, setFileMap] = useState<FileMap>({})
  const [expanded, setExpanded] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [toast, setToast] = useState<{ msg: string; ok: boolean } | null>(null)

  // Upload form state
  const [upFile, setUpFile] = useState('')
  const [upContent, setUpContent] = useState('')
  const [upComment, setUpComment] = useState('')
  const [uploading, setUploading] = useState(false)

  const allFilenames = Object.keys(fileMap).sort()

  async function load() {
    try {
      const all = await fetchXslt()
      const map: FileMap = {}
      for (const v of all) {
        if (!map[v.filename]) map[v.filename] = []
        map[v.filename].push(v)
      }
      for (const k of Object.keys(map)) {
        map[k].sort((a, b) => b.version - a.version)
      }
      setFileMap(map)
      if (!upFile && Object.keys(map).length > 0) setUpFile(Object.keys(map).sort()[0])
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Load failed')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  async function handleExpand(filename: string) {
    if (expanded === filename) { setExpanded(null); return }
    try {
      const versions = await fetchXsltVersions(filename)
      setFileMap(m => ({ ...m, [filename]: versions.sort((a, b) => b.version - a.version) }))
    } catch {}
    setExpanded(filename)
  }

  async function handleActivate(filename: string, version: number) {
    try {
      await activateXslt(filename, version)
      setToast({ msg: `Activated ${filename} v${version}`, ok: true })
      const versions = await fetchXsltVersions(filename)
      setFileMap(m => ({ ...m, [filename]: versions.sort((a, b) => b.version - a.version) }))
    } catch (e: unknown) {
      setToast({ msg: e instanceof Error ? e.message : 'Activate failed', ok: false })
    }
  }

  async function handleUpload() {
    if (!upFile || !upContent.trim()) return
    setUploading(true)
    try {
      await uploadXslt(upFile, upContent, upComment || 'Uploaded via UI')
      setToast({ msg: `Uploaded new version of ${upFile}`, ok: true })
      setUpContent(''); setUpComment('')
      await load()
      setExpanded(upFile)
    } catch (e: unknown) {
      setToast({ msg: e instanceof Error ? e.message : 'Upload failed', ok: false })
    } finally {
      setUploading(false)
    }
  }

  if (loading) return <div className="loading">Loading...</div>
  if (error)   return <div className="error-msg">{error}</div>

  return (
    <>
      <h1 className="page-title">XSLT Admin</h1>

      {/* File list */}
      {allFilenames.map(filename => {
        const versions = fileMap[filename] ?? []
        const active = versions.find(v => v.active)
        const isOpen = expanded === filename

        return (
          <div key={filename} className="xslt-file">
            <div className="xslt-file-header" onClick={() => handleExpand(filename)}>
              <div className="xslt-filename">{filename}</div>
              <div className="xslt-file-meta">
                {active && (
                  <>
                    <span className="badge badge-active">v{active.version} active</span>
                    <span>{active.comment}</span>
                  </>
                )}
                <span className="chevron" style={{ transform: isOpen ? 'rotate(90deg)' : undefined }}>▶</span>
              </div>
            </div>

            {isOpen && (
              <div className="version-list">
                {versions.map(v => (
                  <div key={v.id} className="version-row">
                    <div className="version-info">
                      <span className="version-num">v{v.version}</span>
                      <span className={`badge ${v.active ? 'badge-active' : 'badge-inactive'}`}>
                        {v.active ? 'active' : 'inactive'}
                      </span>
                      <span className="version-comment">{v.comment}</span>
                      <span className="version-date">{new Date(v.uploadedAt).toLocaleString()}</span>
                    </div>
                    <div className="version-actions">
                      {!v.active && (
                        <button
                          className="btn btn-sm btn-success"
                          onClick={() => handleActivate(filename, v.version)}
                        >
                          Activate
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )
      })}

      {/* Upload form */}
      <div className="card">
        <div className="upload-title">Upload New XSLT Version</div>
        <div className="form-row">
          <div className="form-group">
            <label>File</label>
            <select value={upFile} onChange={e => setUpFile(e.target.value)}>
              {allFilenames.map(f => <option key={f} value={f}>{f}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label>Comment</label>
            <input
              type="text"
              placeholder="Describe this change..."
              value={upComment}
              onChange={e => setUpComment(e.target.value)}
            />
          </div>
        </div>
        <div className="form-group" style={{ marginBottom: 14 }}>
          <label>XSLT Content</label>
          <textarea
            rows={16}
            placeholder="Paste your XSLT stylesheet here..."
            value={upContent}
            onChange={e => setUpContent(e.target.value)}
          />
        </div>
        <button
          className="btn btn-primary"
          disabled={uploading || !upContent.trim()}
          onClick={handleUpload}
        >
          {uploading ? 'Uploading...' : 'Upload & Activate'}
        </button>
      </div>

      {toast && (
        <Toast msg={toast.msg} ok={toast.ok} onDone={() => setToast(null)} />
      )}
    </>
  )
}
