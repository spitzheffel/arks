/**
 * 系统配置 API 测试
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getAllConfigs, getConfig, updateConfig } from './config'
import * as request from './request'

vi.mock('./request', () => ({
  get: vi.fn(),
  put: vi.fn()
}))

describe('Config API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getAllConfigs', () => {
    it('should fetch all configs', async () => {
      const mockData = [
        {
          id: 1,
          configKey: 'sync.realtime.enabled',
          configValue: 'true',
          description: '实时同步开关',
          createdAt: '2025-01-01T00:00:00Z',
          updatedAt: '2025-01-01T00:00:00Z'
        },
        {
          id: 2,
          configKey: 'sync.history.cron',
          configValue: '0 30 3 * * ?',
          description: '历史同步 Cron 表达式',
          createdAt: '2025-01-01T00:00:00Z',
          updatedAt: '2025-01-01T00:00:00Z'
        }
      ]
      vi.mocked(request.get).mockResolvedValue(mockData)

      const result = await getAllConfigs()

      expect(request.get).toHaveBeenCalledWith('/config')
      expect(result).toEqual(mockData)
    })
  })

  describe('getConfig', () => {
    it('should fetch config by key', async () => {
      const mockConfig = {
        id: 1,
        configKey: 'sync.realtime.enabled',
        configValue: 'true',
        description: '实时同步开关',
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T00:00:00Z'
      }
      vi.mocked(request.get).mockResolvedValue(mockConfig)

      const result = await getConfig('sync.realtime.enabled')

      expect(request.get).toHaveBeenCalledWith('/config/sync.realtime.enabled')
      expect(result).toEqual(mockConfig)
    })
  })

  describe('updateConfig', () => {
    it('should update config value', async () => {
      const mockConfig = {
        id: 1,
        configKey: 'sync.realtime.enabled',
        configValue: 'false',
        description: '实时同步开关',
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T12:00:00Z'
      }
      vi.mocked(request.put).mockResolvedValue(mockConfig)

      const result = await updateConfig('sync.realtime.enabled', { value: 'false' })

      expect(request.put).toHaveBeenCalledWith('/config/sync.realtime.enabled', { value: 'false' })
      expect(result).toEqual(mockConfig)
    })
  })
})
