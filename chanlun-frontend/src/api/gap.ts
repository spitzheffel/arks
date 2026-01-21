/**
 * 数据缺口 API 模块
 */
import { get, post } from './request'
import type {
  DataGap,
  DataGapPage,
  GapQueryParams,
  GapDetectRequest,
  GapDetectResult,
  GapFillResult,
  BatchGapFillResult
} from '@/types'

const BASE_URL = '/gaps'

// ==================== 缺口查询 API ====================

/**
 * 获取缺口列表（分页）
 */
export async function getGapList(params?: GapQueryParams): Promise<DataGapPage> {
  return get<DataGapPage>(BASE_URL, params as Record<string, unknown>)
}

/**
 * 获取缺口详情
 */
export async function getGap(id: number): Promise<DataGap> {
  return get<DataGap>(`${BASE_URL}/${id}`)
}

// ==================== 缺口检测 API ====================

/**
 * 检测数据缺口
 * 支持单交易对检测和批量检测
 */
export async function detectGaps(request: GapDetectRequest): Promise<GapDetectResult> {
  return post<GapDetectResult>(`${BASE_URL}/detect`, request)
}

// ==================== 缺口回补 API ====================

/**
 * 回补单个缺口
 */
export async function fillGap(id: number): Promise<GapFillResult> {
  return post<GapFillResult>(`${BASE_URL}/${id}/fill`)
}

/**
 * 批量回补缺口
 */
export async function batchFillGaps(gapIds: number[]): Promise<BatchGapFillResult> {
  return post<BatchGapFillResult>(`${BASE_URL}/batch-fill`, { gapIds })
}

/**
 * 重置失败的缺口状态
 */
export async function resetFailedGap(id: number): Promise<DataGap> {
  return post<DataGap>(`${BASE_URL}/${id}/reset`)
}
