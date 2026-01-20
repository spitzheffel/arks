/**
 * 市场 API 模块
 */
import { get, patch } from './request'
import type { Market, MarketPage } from '@/types'

const BASE_URL = '/markets'

/**
 * 获取市场列表（分页）
 */
export async function getMarketList(params?: {
  page?: number
  size?: number
  dataSourceId?: number
  marketType?: string
  enabled?: boolean
}): Promise<MarketPage> {
  return get<MarketPage>(BASE_URL, params)
}

/**
 * 获取所有市场（不分页）
 */
export async function getAllMarkets(params?: {
  dataSourceId?: number
  marketType?: string
  enabled?: boolean
}): Promise<Market[]> {
  return get<Market[]>(`${BASE_URL}/all`, params)
}

/**
 * 获取市场详情
 */
export async function getMarket(id: number): Promise<Market> {
  return get<Market>(`${BASE_URL}/${id}`)
}

/**
 * 更新市场状态（启用/禁用）
 */
export async function updateMarketStatus(id: number, enabled: boolean): Promise<Market> {
  return patch<Market>(`${BASE_URL}/${id}/status`, { enabled })
}
