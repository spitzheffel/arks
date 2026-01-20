/**
 * 数据源相关类型定义
 */

/**
 * 交易所类型
 */
export type ExchangeType = 'BINANCE' | 'OKX' | 'HUOBI'

/**
 * 代理类型
 */
export type ProxyType = 'HTTP' | 'SOCKS5'

/**
 * 数据源 DTO
 */
export interface DataSource {
  id: number
  name: string
  exchangeType: ExchangeType
  baseUrl: string
  wsUrl: string
  proxyEnabled: boolean
  proxyType?: ProxyType
  proxyHost?: string
  proxyPort?: number
  proxyUsername?: string
  enabled: boolean
  createdAt: string
  updatedAt: string
  hasApiKey: boolean
  hasSecretKey: boolean
  hasProxyPassword: boolean
}

/**
 * 创建数据源请求
 */
export interface DataSourceCreateRequest {
  name: string
  exchangeType: ExchangeType
  apiKey?: string
  secretKey?: string
  baseUrl?: string
  wsUrl?: string
  proxyEnabled?: boolean
  proxyType?: ProxyType
  proxyHost?: string
  proxyPort?: number
  proxyUsername?: string
  proxyPassword?: string
}

/**
 * 更新数据源请求
 */
export interface DataSourceUpdateRequest {
  name?: string
  exchangeType?: ExchangeType
  apiKey?: string
  secretKey?: string
  baseUrl?: string
  wsUrl?: string
  proxyEnabled?: boolean
  proxyType?: ProxyType
  proxyHost?: string
  proxyPort?: number
  proxyUsername?: string
  proxyPassword?: string
  clearApiKey?: boolean
  clearSecretKey?: boolean
  clearProxyPassword?: boolean
}

/**
 * 数据源分页结果
 */
export interface DataSourcePage {
  records: DataSource[]
  total: number
  size: number
  current: number
  pages: number
}

/**
 * 代理测试结果
 */
export interface ProxyTestResult {
  success: boolean
  message: string
  latencyMs?: number
  statusCode?: number
  responseBody?: string
  testUrl?: string
  errorDetail?: string
}

/**
 * 连接测试结果
 */
export interface ConnectionTestResult {
  success: boolean
  message: string
  latencyMs?: number
  serverTime?: string
  timeDiffMs?: number
}
