/**
 * 数据源 API 对接测试
 * 
 * 测试前端 API 模块与后端的对接
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  getDataSourceList,
  getAllDataSources,
  getDataSource,
  createDataSource,
  updateDataSource,
  deleteDataSource,
  updateDataSourceStatus,
  testDataSourceConnection,
  testProxyConnection,
  syncMarkets
} from './datasource'
import type { DataSourceCreateRequest, DataSourceUpdateRequest } from '@/types'

// Mock axios instance
vi.mock('./request', async () => {
  const actual = await vi.importActual('./request')
  return {
    ...actual,
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    del: vi.fn()
  }
})

import { get, post, put, patch, del } from './request'

describe('DataSource API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getDataSourceList', () => {
    it('应该调用正确的 API 端点获取分页列表', async () => {
      const mockResponse = {
        records: [],
        total: 0,
        size: 20,
        current: 1,
        pages: 0
      }
      vi.mocked(get).mockResolvedValue(mockResponse)

      const result = await getDataSourceList({ page: 1, size: 20 })

      expect(get).toHaveBeenCalledWith('/datasources', { page: 1, size: 20 })
      expect(result).toEqual(mockResponse)
    })

    it('应该支持筛选参数', async () => {
      const mockResponse = { records: [], total: 0, size: 20, current: 1, pages: 0 }
      vi.mocked(get).mockResolvedValue(mockResponse)

      await getDataSourceList({ exchangeType: 'BINANCE', enabled: true })

      expect(get).toHaveBeenCalledWith('/datasources', { exchangeType: 'BINANCE', enabled: true })
    })
  })

  describe('getAllDataSources', () => {
    it('应该调用正确的 API 端点获取所有数据源', async () => {
      const mockResponse: never[] = []
      vi.mocked(get).mockResolvedValue(mockResponse)

      const result = await getAllDataSources()

      expect(get).toHaveBeenCalledWith('/datasources/all', undefined)
      expect(result).toEqual(mockResponse)
    })
  })

  describe('getDataSource', () => {
    it('应该调用正确的 API 端点获取单个数据源', async () => {
      const mockResponse = { id: 1, name: 'Test' }
      vi.mocked(get).mockResolvedValue(mockResponse)

      const result = await getDataSource(1)

      expect(get).toHaveBeenCalledWith('/datasources/1')
      expect(result).toEqual(mockResponse)
    })
  })

  describe('createDataSource', () => {
    it('应该调用正确的 API 端点创建数据源', async () => {
      const createRequest: DataSourceCreateRequest = {
        name: '测试数据源',
        exchangeType: 'BINANCE'
      }
      const mockResponse = { id: 1, ...createRequest }
      vi.mocked(post).mockResolvedValue(mockResponse)

      const result = await createDataSource(createRequest)

      expect(post).toHaveBeenCalledWith('/datasources', createRequest)
      expect(result).toEqual(mockResponse)
    })
  })

  describe('updateDataSource', () => {
    it('应该调用正确的 API 端点更新数据源', async () => {
      const updateRequest: DataSourceUpdateRequest = {
        name: '更新后的名称'
      }
      const mockResponse = { id: 1, name: '更新后的名称' }
      vi.mocked(put).mockResolvedValue(mockResponse)

      const result = await updateDataSource(1, updateRequest)

      expect(put).toHaveBeenCalledWith('/datasources/1', updateRequest)
      expect(result).toEqual(mockResponse)
    })
  })

  describe('deleteDataSource', () => {
    it('应该调用正确的 API 端点删除数据源', async () => {
      vi.mocked(del).mockResolvedValue(undefined)

      await deleteDataSource(1)

      expect(del).toHaveBeenCalledWith('/datasources/1')
    })
  })

  describe('updateDataSourceStatus', () => {
    it('应该调用正确的 API 端点更新状态', async () => {
      const mockResponse = { id: 1, enabled: true }
      vi.mocked(patch).mockResolvedValue(mockResponse)

      const result = await updateDataSourceStatus(1, true)

      expect(patch).toHaveBeenCalledWith('/datasources/1/status', { enabled: true })
      expect(result).toEqual(mockResponse)
    })
  })

  describe('testDataSourceConnection', () => {
    it('应该调用正确的 API 端点测试连接', async () => {
      const mockResponse = { success: true, message: '连接成功', latencyMs: 100 }
      vi.mocked(post).mockResolvedValue(mockResponse)

      const result = await testDataSourceConnection(1)

      expect(post).toHaveBeenCalledWith('/datasources/1/test')
      expect(result).toEqual(mockResponse)
    })
  })

  describe('testProxyConnection', () => {
    it('应该调用正确的 API 端点测试代理', async () => {
      const mockResponse = { success: true, message: '代理连接成功', latencyMs: 200 }
      vi.mocked(post).mockResolvedValue(mockResponse)

      const result = await testProxyConnection(1)

      expect(post).toHaveBeenCalledWith('/datasources/1/test-proxy')
      expect(result).toEqual(mockResponse)
    })
  })

  describe('syncMarkets', () => {
    it('应该调用正确的 API 端点同步市场', async () => {
      const mockResponse = {
        success: true,
        message: '市场同步成功',
        syncedCount: 1,
        createdCount: 1,
        existingCount: 0,
        markets: [{ id: 1, name: '现货', marketType: 'SPOT' }]
      }
      vi.mocked(post).mockResolvedValue(mockResponse)

      const result = await syncMarkets(1)

      expect(post).toHaveBeenCalledWith('/datasources/1/sync-markets')
      expect(result).toEqual(mockResponse)
    })
  })
})
