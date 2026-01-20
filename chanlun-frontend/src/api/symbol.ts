/**
 * 交易对 API 模块
 */
import { get, patch, put, post } from './request'
import type { Symbol, SymbolPage, SymbolQueryParams, SymbolSyncResult } from '@/types'

const BASE_URL = '/symbols'

/**
 * 获取交易对列表（分页）
 */
export async function getSymbolList(params?: SymbolQueryParams): Promise<SymbolPage> {
  return get<SymbolPage>(BASE_URL, params as Record<string, unknown>)
}

/**
 * 获取所有交易对（不分页）
 */
export async function getAllSymbols(params?: Omit<SymbolQueryParams, 'page' | 'size'>): Promise<Symbol[]> {
  return get<Symbol[]>(`${BASE_URL}/all`, params as Record<string, unknown>)
}

/**
 * 获取交易对详情
 */
export async function getSymbol(id: number): Promise<Symbol> {
  return get<Symbol>(`${BASE_URL}/${id}`)
}

/**
 * 更新实时同步状态
 */
export async function updateRealtimeSyncStatus(id: number, enabled: boolean): Promise<Symbol> {
  return patch<Symbol>(`${BASE_URL}/${id}/realtime-sync`, { enabled })
}

/**
 * 更新历史同步状态
 */
export async function updateHistorySyncStatus(id: number, enabled: boolean): Promise<Symbol> {
  return patch<Symbol>(`${BASE_URL}/${id}/history-sync`, { enabled })
}

/**
 * 配置同步周期
 */
export async function updateSyncIntervals(id: number, intervals: string[]): Promise<Symbol> {
  return put<Symbol>(`${BASE_URL}/${id}/intervals`, { intervals })
}

/**
 * 获取支持的同步周期列表
 */
export async function getValidIntervals(): Promise<string[]> {
  return get<string[]>(`${BASE_URL}/intervals`)
}

/**
 * 同步交易对列表（从市场）
 */
export async function syncSymbols(marketId: number): Promise<SymbolSyncResult> {
  return post<SymbolSyncResult>(`/markets/${marketId}/sync-symbols`)
}
