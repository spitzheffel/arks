/**
 * 市场相关类型定义
 */

/**
 * 市场类型
 */
export type MarketType = 'SPOT' | 'USDT_M' | 'COIN_M'

/**
 * 市场 DTO
 */
export interface Market {
  id: number
  dataSourceId: number
  dataSourceName: string
  name: string
  marketType: MarketType
  enabled: boolean
  createdAt: string
  updatedAt: string
}

/**
 * 市场分页结果
 */
export interface MarketPage {
  records: Market[]
  total: number
  size: number
  current: number
  pages: number
}

/**
 * 按数据源分组的市场
 */
export interface MarketGroup {
  dataSourceId: number
  dataSourceName: string
  markets: Market[]
}

/**
 * 市场同步结果
 */
export interface MarketSyncResult {
  success: boolean
  message: string
  syncedCount: number
  createdCount: number
  existingCount: number
  markets: Market[]
}
