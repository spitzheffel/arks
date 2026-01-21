/**
 * 数据缺口相关类型定义
 */

/**
 * 缺口状态
 */
export type GapStatus = 'PENDING' | 'FILLING' | 'FILLED' | 'FAILED'

/**
 * 数据缺口 DTO
 */
export interface DataGap {
  id: number
  symbolId: number
  symbol: string
  marketId: number
  marketName: string
  dataSourceId: number
  dataSourceName: string
  interval: string
  gapStart: string
  gapEnd: string
  missingCount: number
  status: GapStatus
  retryCount: number
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

/**
 * 缺口分页结果
 */
export interface DataGapPage {
  records: DataGap[]
  total: number
  page: number
  size: number
  pages: number
}

/**
 * 缺口查询参数
 */
export interface GapQueryParams {
  page?: number
  size?: number
  symbolId?: number
  interval?: string
  status?: GapStatus
}

/**
 * 缺口检测请求
 */
export interface GapDetectRequest {
  symbolId?: number
  interval?: string
  detectAll?: boolean
}

/**
 * 缺口检测结果
 */
export interface GapDetectResult {
  success: boolean
  message: string
  totalDetected: number
  newGaps: number
  existingGaps: number
  symbolId?: number
  interval?: string
}

/**
 * 单个缺口回补结果
 */
export interface GapFillResult {
  success: boolean
  message: string
  gapId: number
  filledCount: number
  durationMs: number
}

/**
 * 批量缺口回补结果
 */
export interface BatchGapFillResult {
  success: boolean
  message: string
  totalRequested: number
  successCount: number
  failedCount: number
  results: GapFillResult[]
}

/**
 * 缺口状态标签映射
 */
export const GAP_STATUS_LABELS: Record<GapStatus, string> = {
  PENDING: '待回补',
  FILLING: '回补中',
  FILLED: '已回补',
  FAILED: '回补失败'
}

/**
 * 缺口状态颜色映射
 */
export const GAP_STATUS_COLORS: Record<GapStatus, 'info' | 'warning' | 'success' | 'danger'> = {
  PENDING: 'info',
  FILLING: 'warning',
  FILLED: 'success',
  FAILED: 'danger'
}
