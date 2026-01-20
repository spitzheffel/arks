/**
 * 交易对 API 对接测试
 * 
 * 测试前端 API 模块与后端的对接
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  getSymbolList,
  getAllSymbols,
  getSymbol,
  updateRealtimeSyncStatus,
  updateHistorySyncStatus,
  updateSyncIntervals,
  getValidIntervals,
  syncSymbols
} from './symbol'

// Mock axios instance
vi.mock('./request', async () => {
  const actual = await vi.importActual('./request')
  return {
    ...actual,
    get: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
    post: vi.fn()
  }
})

import { get, patch, put, post } from './request'

describe('Symbol API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getSymbolList', () => {
    it('应该调用正确的 API 端点获取分页列表', async () => {
      const mockResponse = {
        records: [],
        total: 0,
        size: 20,
        current: 1,
        pages: 0
      }
      vi.mocked(get).mockResolvedValue(mockResponse)

      const result = await getSymbolList({ page: 1, size: 20 })

      expect(get).toHaveBeenCalledWith('/symbols', { page: 1, size: 20 })
      expect(result).toEqual(mockResponse)
    })

    it('应该支持筛选参数', async () => {
      const mockResponse = { records: [], total: 0, size: 20, current: 1, pages: 0 }
      vi.mocked(get).mockResolvedValue(mockResponse)

      await getSymbolList({
        marketId: 1,
        dataSourceId: 2,
        keyword: 'BTC',
        realtimeSyncEnabled: true,
        historySyncEnabled: false
      })

      expect(get).toHaveBeenCalledWith('/symbols', {
        marketId: 1,
        dataSourceId: 2,
        keyword: 'BTC',
        realtimeSyncEnabled: true,
        historySyncEnabled: false
      })
    })
  })

  describe('getAllSymbols', () => {
    it('应该调用正确的 API 端点获取所有交易对', async () => {
      const mockResponse: never[] = []
      vi.mocked(get).mockResolvedValue(mockResponse)

      const result = await getAllSymbols()

      expect(get).toHaveBeenCalledWith('/symbols/all', undefined)
      expect(result).toEqual(mockResponse)
    })
  })

  describe('getSymbol', () => {
    it('应该调用正确的 API 端点获取单个交易对', async () => {
      const mockResponse = { id: 1, symbol: 'BTCUSDT' }
      vi.mocked(get).mockResolvedValue(mockResponse)

      const result = await getSymbol(1)

      expect(get).toHaveBeenCalledWith('/symbols/1')
      expect(result).toEqual(mockResponse)
    })
  })

  describe('updateRealtimeSyncStatus', () => {
    it('应该调用正确的 API 端点更新实时同步状态', async () => {
      const mockResponse = { id: 1, realtimeSyncEnabled: true }
      vi.mocked(patch).mockResolvedValue(mockResponse)

      const result = await updateRealtimeSyncStatus(1, true)

      expect(patch).toHaveBeenCalledWith('/symbols/1/realtime-sync', { enabled: true })
      expect(result).toEqual(mockResponse)
    })
  })

  describe('updateHistorySyncStatus', () => {
    it('应该调用正确的 API 端点更新历史同步状态', async () => {
      const mockResponse = { id: 1, historySyncEnabled: true }
      vi.mocked(patch).mockResolvedValue(mockResponse)

      const result = await updateHistorySyncStatus(1, true)

      expect(patch).toHaveBeenCalledWith('/symbols/1/history-sync', { enabled: true })
      expect(result).toEqual(mockResponse)
    })
  })

  describe('updateSyncIntervals', () => {
    it('应该调用正确的 API 端点配置同步周期', async () => {
      const mockResponse = { id: 1, syncIntervals: ['1m', '5m', '1h'] }
      vi.mocked(put).mockResolvedValue(mockResponse)

      const result = await updateSyncIntervals(1, ['1m', '5m', '1h'])

      expect(put).toHaveBeenCalledWith('/symbols/1/intervals', { intervals: ['1m', '5m', '1h'] })
      expect(result).toEqual(mockResponse)
    })
  })

  describe('getValidIntervals', () => {
    it('应该调用正确的 API 端点获取支持的周期列表', async () => {
      const mockResponse = ['1m', '3m', '5m', '15m', '30m', '1h', '2h', '4h', '6h', '8h', '12h', '1d', '3d', '1w', '1M']
      vi.mocked(get).mockResolvedValue(mockResponse)

      const result = await getValidIntervals()

      expect(get).toHaveBeenCalledWith('/symbols/intervals')
      expect(result).toEqual(mockResponse)
    })
  })

  describe('syncSymbols', () => {
    it('应该调用正确的 API 端点同步交易对', async () => {
      const mockResponse = {
        success: true,
        message: '同步成功',
        syncedCount: 10,
        createdCount: 5,
        updatedCount: 3,
        existingCount: 2,
        symbols: []
      }
      vi.mocked(post).mockResolvedValue(mockResponse)

      const result = await syncSymbols(1)

      expect(post).toHaveBeenCalledWith('/markets/1/sync-symbols')
      expect(result).toEqual(mockResponse)
    })
  })
})
