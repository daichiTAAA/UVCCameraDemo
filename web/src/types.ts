export interface WorkSummary {
  workId: string
  model: string
  serial: string
  process: string
  firstRecordedAt: string
  lastRecordedAt: string
  segmentCount: number
}

export interface SegmentSummary {
  segmentId: string
  segmentIndex: number
  recordedAt: string
  fileName?: string
  sizeBytes?: number
}

export interface WorkDetail extends WorkSummary {
  segments: SegmentSummary[]
}

export interface SearchFilters {
  workId?: string
  model?: string
  serial?: string
  process?: string
  from?: string
  to?: string
}

export interface ApiErrorResponse {
  error?: string
  message?: string
}
