/**
 * 市场 API 对接测试
 * 
 * 测试前端 API 模块与后端的对接
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  getMarketList,
  getAllMarkets,
  getMarket,
  updateMarketStatus
} from './market'

// Mock axios instance
vi.mock('./request', async () => {
  const actual = await vi.importActual('./request')
  return {
    ...actual,
    get: vi.fn(),
    patch: vi.fn()
  }
})

import { get, patch } from './request'

describe('Market API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getMarketList', () => {
    it('应该调用正确的 API 端点获取分页列表', async () => {
      const mockResponse = {
        records: [],
        total: 0,
        size: 20,
        current: 1,
        pages: 0
      }
      vi.mocked(get).mockResolvedValue(mockResponse)

      const result = await getMarketList({ page: 1, size: 20 })

      expect(get).toHaveBeenCalledWith('/markets', { page: 1, size: 20 })
      expect(result).toEqual(mockResponse)
    })

    it('应该支持筛选参数', async () => {
      const mockResponse = { records: [], total: 0, size: 20, current: 1, pages: 0 }
      vi.mocked(get).mockResolvedValue(mockResponse)

      await getMarketList({ dataSourceId: 1, marketType: 'SPOT', enabled: true })

      expect(get).toHaveBeenCalledWith('/markets', { dataSourceId: 1, marketType: 'SPOT', enabled: true })
    })
  })

  describe('getAllMarkets', () => {
    it('应该调用正确的 API 端点获取所有市场', async () => {
      const mockResponse: never[] = []
      vi.mocked(get).mockResolvedValue(mockResponse)

      const result = await getAllMarkets()

      expect(get).toHaveBeenCalledWith('/markets/all', undefined)
      expect(result).toEqual(mockResponse)
    })

    it('应该支持筛选参数', async () => {
      const mockResponse: never[] = []
      vi.mocked(get).mockResolvedValue(mockResponse)

      await getAllMarkets({ dataSourceId: 1, enabled: true })

      expect(get).toHaveBeenCalledWith('/markets/all', { dataSourceId: 1, enabled: true })
    })
  })

  describe('getMarket', () => {
    it('应该调用正确的 API 端点获取单个市场', async () => {
      const mockResponse = { id: 1, name: '现货市场' }
      vi.mocked(get).mockResolvedValue(mockResponse)

      const result = await getMarket(1)

      expect(get).toHaveBeenCalledWith('/markets/1')
      expect(result).toEqual(mockResponse)
    })
  })

  describe('updateMarketStatus', () => {
    it('应该调用正确的 API 端点更新状态', async () => {
      const mockResponse = { id: 1, enabled: true }
      vi.mocked(patch).mockResolvedValue(mockResponse)

      const result = await updateMarketStatus(1, true)

      expect(patch).toHaveBeenCalledWith('/markets/1/status', { enabled: true })
      expect(result).toEqual(mockResponse)
    })

    it('应该支持禁用市场', async () => {
      const mockResponse = { id: 1, enabled: false }
      vi.mocked(patch).mockResolvedValue(mockResponse)

      const result = await updateMarketStatus(1, false)

      expect(patch).toHaveBeenCalledWith('/markets/1/status', { enabled: false })
      expect(result).toEqual(mockResponse)
    })
  })
})
