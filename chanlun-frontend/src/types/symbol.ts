/**
 * 交易对相关类型定义
 */

import type { MarketType } from './market'

/**
 * 交易对 DTO
 */
export interface Symbol {
  id: number
  marketId: number
  marketName: string
  marketType: MarketType
  dataSourceId: number
  dataSourceName: string
  symbol: string
  baseAsset: string
  quoteAsset: string
  pricePrecision: number
  quantityPrecision: number
  realtimeSyncEnabled: boolean
  historySyncEnabled: boolean
  syncIntervals: string[]
  status: string
  createdAt: string
  updatedAt: string
}

/**
 * 交易对分页结果
 */
export interface SymbolPage {
  records: Symbol[]
  total: number
  size: number
  current: number
  pages: number
}

/**
 * 交易对查询参数
 */
export interface SymbolQueryParams {
  page?: number
  size?: number
  marketId?: number
  dataSourceId?: number
  keyword?: string
  realtimeSyncEnabled?: boolean
  historySyncEnabled?: boolean
}

/**
 * 交易对同步结果
 */
export interface SymbolSyncResult {
  success: boolean
  message: string
  syncedCount: number
  createdCount: number
  updatedCount: number
  existingCount: number
  symbols: Symbol[]
}

/**
 * 同步周期配置请求
 */
export interface SymbolIntervalsRequest {
  intervals: string[]
}

/**
 * 支持的同步周期列表
 */
export const VALID_INTERVALS = [
  '1m', '3m', '5m', '15m', '30m',
  '1h', '2h', '4h', '6h', '8h', '12h',
  '1d', '3d', '1w', '1M'
] as const

export type ValidInterval = typeof VALID_INTERVALS[number]
