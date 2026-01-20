/**
 * 同步相关类型定义
 */

/**
 * 同步任务类型
 */
export type SyncTaskType = 'REALTIME' | 'HISTORY' | 'GAP_FILL'

/**
 * 同步任务状态
 */
export type SyncTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

/**
 * 同步任务 DTO
 */
export interface SyncTask {
  id: number
  symbolId: number
  interval: string
  taskType: SyncTaskType
  status: SyncTaskStatus
  startTime: string | null
  endTime: string | null
  syncedCount: number
  retryCount: number
  maxRetries: number
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

/**
 * 同步任务分页结果
 */
export interface SyncTaskPage {
  records: SyncTask[]
  total: number
  page: number
  size: number
  pages: number
}

/**
 * 同步任务查询参数
 */
export interface SyncTaskQueryParams {
  page?: number
  size?: number
  symbolId?: number
  taskType?: SyncTaskType
  status?: SyncTaskStatus
}

/**
 * 同步状态 DTO
 */
export interface SyncStatus {
  id: number
  symbolId: number
  interval: string
  lastSyncTime: string | null
  lastKlineTime: string | null
  totalKlines: number
  autoGapFillEnabled: boolean
  createdAt: string
  updatedAt: string
}

/**
 * 同步状态分页结果
 */
export interface SyncStatusPage {
  records: SyncStatus[]
  total: number
  page: number
  size: number
  pages: number
}

/**
 * 历史同步请求
 */
export interface HistorySyncRequest {
  interval: string
  startTime: string
  endTime: string
}

/**
 * 历史同步结果
 */
export interface HistorySyncResult {
  success: boolean
  taskId: number | null
  symbolId: number
  interval: string
  startTime: string
  endTime: string
  syncedCount: number
  durationMs: number
  errorMessage: string | null
}

/**
 * K线数据 DTO
 */
export interface Kline {
  id: number
  symbolId: number
  interval: string
  openTime: string
  open: string
  high: string
  low: string
  close: string
  volume: string
  quoteVolume: string
  trades: number
  closeTime: string
  createdAt: string
}

/**
 * K线查询参数
 */
export interface KlineQueryParams {
  symbolId: number
  interval: string
  startTime?: string
  endTime?: string
  limit?: number
}

/**
 * K线删除请求
 */
export interface KlineDeleteRequest {
  interval: string
  startTime: string
  endTime: string
}

/**
 * K线删除结果
 */
export interface KlineDeleteResult {
  symbolId: number
  interval: string
  startTime: string
  endTime: string
  deletedCount: number
}

/**
 * 任务类型标签映射
 */
export const TASK_TYPE_LABELS: Record<SyncTaskType, string> = {
  REALTIME: '实时同步',
  HISTORY: '历史同步',
  GAP_FILL: '缺口回补'
}

/**
 * 任务状态标签映射
 */
export const TASK_STATUS_LABELS: Record<SyncTaskStatus, string> = {
  PENDING: '等待中',
  RUNNING: '执行中',
  SUCCESS: '成功',
  FAILED: '失败'
}

/**
 * 任务状态颜色映射
 */
export const TASK_STATUS_COLORS: Record<SyncTaskStatus, 'info' | 'warning' | 'success' | 'danger'> = {
  PENDING: 'info',
  RUNNING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger'
}
