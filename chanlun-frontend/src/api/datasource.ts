/**
 * 数据源 API 模块
 */
import { get, post, put, patch, del } from './request'
import type {
  DataSource,
  DataSourcePage,
  DataSourceCreateRequest,
  DataSourceUpdateRequest,
  ProxyTestResult,
  ConnectionTestResult
} from '@/types'

const BASE_URL = '/datasources'

/**
 * 获取数据源列表（分页）
 */
export async function getDataSourceList(params?: {
  page?: number
  size?: number
  exchangeType?: string
  enabled?: boolean
}): Promise<DataSourcePage> {
  return get<DataSourcePage>(BASE_URL, params)
}

/**
 * 获取所有数据源（不分页）
 */
export async function getAllDataSources(params?: {
  exchangeType?: string
  enabled?: boolean
}): Promise<DataSource[]> {
  return get<DataSource[]>(`${BASE_URL}/all`, params)
}

/**
 * 获取数据源详情
 */
export async function getDataSource(id: number): Promise<DataSource> {
  return get<DataSource>(`${BASE_URL}/${id}`)
}

/**
 * 创建数据源
 */
export async function createDataSource(data: DataSourceCreateRequest): Promise<DataSource> {
  return post<DataSource>(BASE_URL, data)
}

/**
 * 更新数据源
 */
export async function updateDataSource(id: number, data: DataSourceUpdateRequest): Promise<DataSource> {
  return put<DataSource>(`${BASE_URL}/${id}`, data)
}

/**
 * 删除数据源
 */
export async function deleteDataSource(id: number): Promise<void> {
  return del<void>(`${BASE_URL}/${id}`)
}

/**
 * 更新数据源状态（启用/禁用）
 */
export async function updateDataSourceStatus(id: number, enabled: boolean): Promise<DataSource> {
  return patch<DataSource>(`${BASE_URL}/${id}/status`, { enabled })
}

/**
 * 测试数据源连接
 */
export async function testDataSourceConnection(id: number): Promise<ConnectionTestResult> {
  return post<ConnectionTestResult>(`${BASE_URL}/${id}/test`)
}

/**
 * 测试代理连接
 */
export async function testProxyConnection(id: number): Promise<ProxyTestResult> {
  return post<ProxyTestResult>(`${BASE_URL}/${id}/test-proxy`)
}

/**
 * 同步市场信息
 */
export async function syncMarkets(id: number): Promise<import('@/types').MarketSyncResult> {
  return post<import('@/types').MarketSyncResult>(`${BASE_URL}/${id}/sync-markets`)
}
