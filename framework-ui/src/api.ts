import type { PageResponse, RequestLog, Stats, XsltVersion } from './types'

const BASE = '/admin'

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`)
  return res.json()
}

export async function fetchStats(): Promise<Stats> {
  return json(await fetch(`${BASE}/requests/stats`))
}

export async function fetchRequests(page = 0, size = 50): Promise<PageResponse<RequestLog>> {
  return json(await fetch(`${BASE}/requests?page=${page}&size=${size}`))
}

export async function fetchXslt(): Promise<XsltVersion[]> {
  return json(await fetch(`${BASE}/xslt`))
}

export async function fetchXsltVersions(filename: string): Promise<XsltVersion[]> {
  return json(await fetch(`${BASE}/xslt/${encodeURIComponent(filename)}`))
}

export async function uploadXslt(filename: string, content: string, comment: string): Promise<void> {
  const res = await fetch(`${BASE}/xslt/${encodeURIComponent(filename)}?comment=${encodeURIComponent(comment)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain' },
    body: content,
  })
  await json(res)
}

export async function activateXslt(filename: string, version: number): Promise<void> {
  const res = await fetch(`${BASE}/xslt/${encodeURIComponent(filename)}/activate/${version}`, {
    method: 'PUT',
  })
  await json(res)
}
