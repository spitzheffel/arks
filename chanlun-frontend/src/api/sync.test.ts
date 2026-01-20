/**
 * 同步 API 测试
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as syncApi from './sync'

// Mock axios instance
vi.mock('./request', () => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  axiosInstance: {
    delete: vi.fn()
  }
}))

import { get, post, patch, axiosInstance } from './request'

describe('Sync API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getSyncTaskList', () => {
    it('should fetch sync task list', async () => {
      const mockData = {
        records: [{ id: 1, symbolId: 1, interval: '1h', taskType: 'HISTORY', status: 'SUCCESS' }],
        total: 1,
        page: 1,
        size: 20,
        pages: 1
      }
      vi.mocked(get).mockResolvedValue(mockData)

      const result = await syncApi.getSyncTaskList({ page: 1, size: 20 })

      expect(get).toHaveBeenCalledWith('/sync/tasks', { page: 1, size: 20 })
      expect(result).toEqual(mockData)
    })

    it('should fetch sync task list with filters', async () => {
      const mockData = { records: [], total: 0, page: 1, size: 20, pages: 0 }
      vi.mocked(get).mockResolvedValue(mockData)

      await syncApi.getSyncTaskList({ symbolId: 1, taskType: 'HISTORY', status: 'SUCCESS' })

      expect(get).toHaveBeenCalledWith('/sync/tasks', { symbolId: 1, taskType: 'HISTORY', status: 'SUCCESS' })
    })
  })

  describe('getSyncTask', () => {
    it('should fetch sync task by id', async () => {
      const mockTask = { id: 1, symbolId: 1, interval: '1h', taskType: 'HISTORY', status: 'SUCCESS' }
      vi.mocked(get).mockResolvedValue(mockTask)

      const result = await syncApi.getSyncTask(1)

      expect(get).toHaveBeenCalledWith('/sync/tasks/1')
      expect(result).toEqual(mockTask)
    })
  })

  describe('triggerHistorySync', () => {
    it('should trigger history sync', async () => {
      const mockResult = {
        success: true,
        taskId: 1,
        symbolId: 1,
        interval: '1h',
        startTime: '2025-01-01T00:00:00Z',
        endTime: '2025-01-02T00:00:00Z',
        syncedCount: 24,
        durationMs: 1000
      }
      vi.mocked(post).mockResolvedValue(mockResult)

      const result = await syncApi.triggerHistorySync(1, {
        interval: '1h',
        startTime: '2025-01-01T00:00:00Z',
        endTime: '2025-01-02T00:00:00Z'
      })

      expect(post).toHaveBeenCalledWith('/sync/symbols/1/history', {
        interval: '1h',
        startTime: '2025-01-01T00:00:00Z',
        endTime: '2025-01-02T00:00:00Z'
      })
      expect(result).toEqual(mockResult)
    })
  })

  describe('getSyncStatusList', () => {
    it('should fetch sync status list', async () => {
      const mockData = {
        records: [{ id: 1, symbolId: 1, interval: '1h', totalKlines: 100 }],
        total: 1,
        page: 1,
        size: 20,
        pages: 1
      }
      vi.mocked(get).mockResolvedValue(mockData)

      const result = await syncApi.getSyncStatusList({ page: 1, size: 20 })

      expect(get).toHaveBeenCalledWith('/sync/status', { page: 1, size: 20 })
      expect(result).toEqual(mockData)
    })
  })

  describe('getSyncStatusBySymbol', () => {
    it('should fetch sync status by symbol id', async () => {
      const mockStatuses = [
        { id: 1, symbolId: 1, interval: '1h', totalKlines: 100 },
        { id: 2, symbolId: 1, interval: '1d', totalKlines: 30 }
      ]
      vi.mocked(get).mockResolvedValue(mockStatuses)

      const result = await syncApi.getSyncStatusBySymbol(1)

      expect(get).toHaveBeenCalledWith('/sync/symbols/1/status')
      expect(result).toEqual(mockStatuses)
    })
  })

  describe('updateAutoGapFill', () => {
    it('should update auto gap fill setting', async () => {
      const mockStatus = { id: 1, symbolId: 1, interval: '1h', autoGapFillEnabled: true }
      vi.mocked(patch).mockResolvedValue(mockStatus)

      const result = await syncApi.updateAutoGapFill(1, true)

      expect(patch).toHaveBeenCalledWith('/sync/status/1/auto-gap-fill', { enabled: true })
      expect(result).toEqual(mockStatus)
    })
  })

  describe('getKlines', () => {
    it('should fetch klines', async () => {
      const mockKlines = [
        { id: 1, symbolId: 1, interval: '1h', openTime: '2025-01-01T00:00:00Z', open: '100', close: '101' }
      ]
      vi.mocked(get).mockResolvedValue(mockKlines)

      const result = await syncApi.getKlines({ symbolId: 1, interval: '1h', limit: 100 })

      expect(get).toHaveBeenCalledWith('/klines', { symbolId: 1, interval: '1h', limit: 100 })
      expect(result).toEqual(mockKlines)
    })

    it('should fetch klines with time range', async () => {
      const mockKlines: never[] = []
      vi.mocked(get).mockResolvedValue(mockKlines)

      await syncApi.getKlines({
        symbolId: 1,
        interval: '1h',
        startTime: '2025-01-01T00:00:00Z',
        endTime: '2025-01-02T00:00:00Z'
      })

      expect(get).toHaveBeenCalledWith('/klines', {
        symbolId: 1,
        interval: '1h',
        startTime: '2025-01-01T00:00:00Z',
        endTime: '2025-01-02T00:00:00Z'
      })
    })
  })

  describe('deleteKlines', () => {
    it('should delete klines', async () => {
      const mockResult = {
        symbolId: 1,
        interval: '1h',
        startTime: '2025-01-01T00:00:00Z',
        endTime: '2025-01-02T00:00:00Z',
        deletedCount: 24
      }
      vi.mocked(axiosInstance.delete).mockResolvedValue({
        data: { code: 200, message: 'success', data: mockResult }
      })

      const result = await syncApi.deleteKlines(1, {
        interval: '1h',
        startTime: '2025-01-01T00:00:00Z',
        endTime: '2025-01-02T00:00:00Z'
      })

      expect(axiosInstance.delete).toHaveBeenCalledWith('/klines/symbols/1', {
        data: {
          interval: '1h',
          startTime: '2025-01-01T00:00:00Z',
          endTime: '2025-01-02T00:00:00Z'
        }
      })
      expect(result).toEqual(mockResult)
    })

    it('should throw error when delete fails', async () => {
      vi.mocked(axiosInstance.delete).mockResolvedValue({
        data: { code: 400, message: '删除失败', data: null }
      })

      await expect(syncApi.deleteKlines(1, {
        interval: '1h',
        startTime: '2025-01-01T00:00:00Z',
        endTime: '2025-01-02T00:00:00Z'
      })).rejects.toThrow('删除失败')
    })
  })
})
