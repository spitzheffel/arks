/**
 * 同步管理 API 模块
 */
import { get, post, patch } from './request'
import type {
  SyncTask,
  SyncTaskPage,
  SyncTaskQueryParams,
  SyncStatus,
  SyncStatusPage,
  HistorySyncRequest,
  HistorySyncResult,
  Kline,
  KlineQueryParams,
  KlineDeleteRequest,
  KlineDeleteResult
} from '@/types'

const SYNC_BASE_URL = '/sync'
const KLINE_BASE_URL = '/klines'

// ==================== 同步任务 API ====================

/**
 * 获取同步任务列表（分页）
 */
export async function getSyncTaskList(params?: SyncTaskQueryParams): Promise<SyncTaskPage> {
  return get<SyncTaskPage>(`${SYNC_BASE_URL}/tasks`, params as Record<string, unknown>)
}

/**
 * 获取同步任务详情
 */
export async function getSyncTask(id: number): Promise<SyncTask> {
  return get<SyncTask>(`${SYNC_BASE_URL}/tasks/${id}`)
}

/**
 * 触发历史同步
 */
export async function triggerHistorySync(
  symbolId: number,
  request: HistorySyncRequest
): Promise<HistorySyncResult> {
  return post<HistorySyncResult>(`${SYNC_BASE_URL}/symbols/${symbolId}/history`, request)
}

// ==================== 同步状态 API ====================

/**
 * 获取同步状态列表（分页）
 */
export async function getSyncStatusList(params?: {
  symbolId?: number
  page?: number
  size?: number
}): Promise<SyncStatusPage> {
  return get<SyncStatusPage>(`${SYNC_BASE_URL}/status`, params as Record<string, unknown>)
}

/**
 * 获取同步状态详情
 */
export async function getSyncStatus(id: number): Promise<SyncStatus> {
  return get<SyncStatus>(`${SYNC_BASE_URL}/status/${id}`)
}

/**
 * 获取交易对的所有同步状态
 */
export async function getSyncStatusBySymbol(symbolId: number): Promise<SyncStatus[]> {
  return get<SyncStatus[]>(`${SYNC_BASE_URL}/symbols/${symbolId}/status`)
}

/**
 * 更新周期级别自动回补开关
 */
export async function updateAutoGapFill(id: number, enabled: boolean): Promise<SyncStatus> {
  return patch<SyncStatus>(`${SYNC_BASE_URL}/status/${id}/auto-gap-fill`, { enabled })
}

// ==================== K线数据 API ====================

/**
 * 获取K线数据
 */
export async function getKlines(params: KlineQueryParams): Promise<Kline[]> {
  return get<Kline[]>(KLINE_BASE_URL, params as Record<string, unknown>)
}

/**
 * 删除K线数据
 */
export async function deleteKlines(
  symbolId: number,
  request: KlineDeleteRequest
): Promise<KlineDeleteResult> {
  // axios delete 方法需要通过 data 传递请求体
  const { axiosInstance } = await import('./request')
  const response = await axiosInstance.delete<{ code: number; message: string; data: KlineDeleteResult }>(
    `${KLINE_BASE_URL}/symbols/${symbolId}`,
    { data: request }
  )
  
  if (response.data.code !== 200) {
    throw new Error(response.data.message || '删除失败')
  }
  
  return response.data.data
}
