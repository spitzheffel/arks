/**
 * 数据缺口 API 测试
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getGapList, getGap, detectGaps, fillGap, batchFillGaps, resetFailedGap } from './gap'
import * as request from './request'

vi.mock('./request', () => ({
  get: vi.fn(),
  post: vi.fn()
}))

describe('Gap API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getGapList', () => {
    it('should fetch gap list without params', async () => {
      const mockData = {
        records: [],
        total: 0,
        page: 1,
        size: 20,
        pages: 0
      }
      vi.mocked(request.get).mockResolvedValue(mockData)

      const result = await getGapList()

      expect(request.get).toHaveBeenCalledWith('/gaps', undefined)
      expect(result).toEqual(mockData)
    })

    it('should fetch gap list with params', async () => {
      const mockData = {
        records: [{ id: 1, symbolId: 1, interval: '1h', status: 'PENDING' }],
        total: 1,
        page: 1,
        size: 20,
        pages: 1
      }
      vi.mocked(request.get).mockResolvedValue(mockData)

      const params = { symbolId: 1, interval: '1h', status: 'PENDING' as const }
      const result = await getGapList(params)

      expect(request.get).toHaveBeenCalledWith('/gaps', params)
      expect(result).toEqual(mockData)
    })
  })

  describe('getGap', () => {
    it('should fetch gap by id', async () => {
      const mockGap = { id: 1, symbolId: 1, interval: '1h', status: 'PENDING' }
      vi.mocked(request.get).mockResolvedValue(mockGap)

      const result = await getGap(1)

      expect(request.get).toHaveBeenCalledWith('/gaps/1')
      expect(result).toEqual(mockGap)
    })
  })

  describe('detectGaps', () => {
    it('should detect gaps for single symbol', async () => {
      const mockResult = {
        success: true,
        message: '检测完成',
        totalDetected: 2,
        newGaps: 1,
        existingGaps: 1
      }
      vi.mocked(request.post).mockResolvedValue(mockResult)

      const result = await detectGaps({ symbolId: 1, interval: '1h' })

      expect(request.post).toHaveBeenCalledWith('/gaps/detect', { symbolId: 1, interval: '1h' })
      expect(result).toEqual(mockResult)
    })

    it('should detect all gaps', async () => {
      const mockResult = {
        success: true,
        message: '批量检测完成',
        totalDetected: 10,
        newGaps: 5,
        existingGaps: 5
      }
      vi.mocked(request.post).mockResolvedValue(mockResult)

      const result = await detectGaps({ detectAll: true })

      expect(request.post).toHaveBeenCalledWith('/gaps/detect', { detectAll: true })
      expect(result).toEqual(mockResult)
    })
  })

  describe('fillGap', () => {
    it('should fill single gap', async () => {
      const mockResult = {
        success: true,
        message: '回补成功',
        gapId: 1,
        filledCount: 100,
        durationMs: 1500
      }
      vi.mocked(request.post).mockResolvedValue(mockResult)

      const result = await fillGap(1)

      expect(request.post).toHaveBeenCalledWith('/gaps/1/fill')
      expect(result).toEqual(mockResult)
    })
  })

  describe('batchFillGaps', () => {
    it('should batch fill gaps', async () => {
      const mockResult = {
        success: true,
        message: '批量回补完成',
        totalRequested: 3,
        successCount: 2,
        failedCount: 1,
        results: []
      }
      vi.mocked(request.post).mockResolvedValue(mockResult)

      const result = await batchFillGaps([1, 2, 3])

      expect(request.post).toHaveBeenCalledWith('/gaps/batch-fill', { gapIds: [1, 2, 3] })
      expect(result).toEqual(mockResult)
    })
  })

  describe('resetFailedGap', () => {
    it('should reset failed gap', async () => {
      const mockGap = { id: 1, symbolId: 1, interval: '1h', status: 'PENDING' }
      vi.mocked(request.post).mockResolvedValue(mockGap)

      const result = await resetFailedGap(1)

      expect(request.post).toHaveBeenCalledWith('/gaps/1/reset')
      expect(result).toEqual(mockGap)
    })
  })
})
