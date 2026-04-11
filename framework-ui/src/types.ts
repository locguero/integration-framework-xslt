export type RequestStatus = 'RECEIVED' | 'ACCEPTED' | 'REJECTED' | 'FAILED'

export interface RequestLog {
  id: number
  correlationId: string
  sourceSystem: string
  entityType: string
  operation: string
  routingSlip: string | null
  status: RequestStatus
  errorMessage: string | null
  receivedAt: string
  completedAt: string | null
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface Stats {
  total: number
  byStatus: Record<string, number>
  recentByHour: Record<string, number>
}

export interface XsltVersion {
  id: number
  filename: string
  version: number
  content: string
  active: boolean
  comment: string
  uploadedAt: string
}
