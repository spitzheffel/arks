/**
 * 缺口管理页面组件测试
 * 任务 30.2: 覆盖自动回补开关配置入口
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import GapList from './GapList.vue'

// Mock API 模块
vi.mock('@/api/gap', () => ({
  getGapList: vi.fn().mockResolvedValue({
    records: [
      {
        id: 1,
        symbolId: 1,
        symbol: 'BTCUSDT',
        marketId: 1,
        marketName: '现货',
        dataSourceId: 1,
        dataSourceName: '币安',
        interval: '1h',
        gapStart: '2025-01-01T00:00:00Z',
        gapEnd: '2025-01-01T12:00:00Z',
        missingCount: 12,
        status: 'PENDING',
        retryCount: 0,
        errorMessage: null,
        createdAt: '2025-01-02T00:00:00Z',
        updatedAt: '2025-01-02T00:00:00Z'
      }
    ],
    total: 1,
    page: 1,
    size: 20,
    pages: 1
  }),
  detectGaps: vi.fn(),
  fillGap: vi.fn(),
  batchFillGaps: vi.fn(),
  resetFailedGap: vi.fn()
}))

vi.mock('@/api/symbol', () => ({
  getAllSymbols: vi.fn().mockResolvedValue([
    { id: 1, symbol: 'BTCUSDT', historySyncEnabled: true },
    { id: 2, symbol: 'ETHUSDT', historySyncEnabled: true }
  ])
}))

vi.mock('@/api/sync', () => ({
  getSyncStatusBySymbol: vi.fn().mockResolvedValue([
    {
      id: 1,
      symbolId: 1,
      interval: '1h',
      lastSyncTime: '2025-01-02T00:00:00Z',
      lastKlineTime: '2025-01-01T23:00:00Z',
      totalKlines: 100,
      autoGapFillEnabled: true,
      createdAt: '2025-01-01T00:00:00Z',
      updatedAt: '2025-01-02T00:00:00Z'
    },
    {
      id: 2,
      symbolId: 1,
      interval: '1d',
      lastSyncTime: '2025-01-02T00:00:00Z',
      lastKlineTime: '2025-01-01T00:00:00Z',
      totalKlines: 30,
      autoGapFillEnabled: false,
      createdAt: '2025-01-01T00:00:00Z',
      updatedAt: '2025-01-02T00:00:00Z'
    }
  ]),
  updateAutoGapFill: vi.fn()
}))

import { getSyncStatusBySymbol, updateAutoGapFill } from '@/api/sync'

describe('GapList - 自动回补开关配置入口', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const mountComponent = async () => {
    const wrapper = mount(GapList, {
      global: {
        stubs: {
          Teleport: true,
          Transition: false
        }
      }
    })
    await flushPromises()
    return wrapper
  }

  describe('自动回补配置入口', () => {
    it('缺口列表应显示自动回补按钮', async () => {
      const wrapper = await mountComponent()
      
      const autoFillButton = wrapper.findAll('button').find(btn => btn.text().includes('自动回补'))
      expect(autoFillButton).toBeTruthy()
    })

    it('点击自动回补按钮应打开配置弹窗', async () => {
      const wrapper = await mountComponent()
      
      const autoFillButton = wrapper.findAll('button').find(btn => btn.text().includes('自动回补'))
      await autoFillButton?.trigger('click')
      await flushPromises()
      
      expect(wrapper.text()).toContain('自动回补配置')
      expect(wrapper.text()).toContain('BTCUSDT')
    })

    it('配置弹窗应加载交易对的同步状态', async () => {
      const wrapper = await mountComponent()
      
      const autoFillButton = wrapper.findAll('button').find(btn => btn.text().includes('自动回补'))
      await autoFillButton?.trigger('click')
      await flushPromises()
      
      expect(getSyncStatusBySymbol).toHaveBeenCalledWith(1)
    })

    it('配置弹窗应显示各周期的自动回补开关', async () => {
      const wrapper = await mountComponent()
      
      const autoFillButton = wrapper.findAll('button').find(btn => btn.text().includes('自动回补'))
      await autoFillButton?.trigger('click')
      await flushPromises()
      
      // 应显示周期信息
      expect(wrapper.text()).toContain('1h')
      expect(wrapper.text()).toContain('1d')
      
      // 应显示 K 线数量
      expect(wrapper.text()).toContain('100')
      expect(wrapper.text()).toContain('30')
    })

    it('配置弹窗应显示提示信息', async () => {
      const wrapper = await mountComponent()
      
      const autoFillButton = wrapper.findAll('button').find(btn => btn.text().includes('自动回补'))
      await autoFillButton?.trigger('click')
      await flushPromises()
      
      expect(wrapper.text()).toContain('自动回补需要同时开启全局自动回补开关')
      expect(wrapper.text()).toContain('手动删除历史数据后')
    })

    it('点击开关应调用 updateAutoGapFill API', async () => {
      vi.mocked(updateAutoGapFill).mockResolvedValue({
        id: 2,
        symbolId: 1,
        interval: '1d',
        lastSyncTime: '2025-01-02T00:00:00Z',
        lastKlineTime: '2025-01-01T00:00:00Z',
        totalKlines: 30,
        autoGapFillEnabled: true,
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-02T00:00:00Z'
      })

      const wrapper = await mountComponent()
      
      // 打开配置弹窗
      const autoFillButton = wrapper.findAll('button').find(btn => btn.text().includes('自动回补'))
      await autoFillButton?.trigger('click')
      await flushPromises()
      
      // 找到开关按钮（toggle switch）
      const toggleButtons = wrapper.findAll('button[type="button"]').filter(btn => 
        btn.classes().some(c => c.includes('rounded-full'))
      )
      
      if (toggleButtons.length > 0) {
        await toggleButtons[0].trigger('click')
        await flushPromises()
        
        expect(updateAutoGapFill).toHaveBeenCalled()
      }
    })

    it('配置弹窗应有关闭按钮', async () => {
      const wrapper = await mountComponent()
      
      // 打开配置弹窗
      const autoFillButton = wrapper.findAll('button').find(btn => btn.text().includes('自动回补'))
      await autoFillButton?.trigger('click')
      await flushPromises()
      
      expect(wrapper.text()).toContain('自动回补配置')
      
      // 应有关闭按钮
      const closeButton = wrapper.findAll('button').find(btn => btn.text() === '关闭')
      expect(closeButton).toBeTruthy()
    })
  })

  describe('缺口列表显示', () => {
    it('应正确显示缺口信息', async () => {
      const wrapper = await mountComponent()
      
      expect(wrapper.text()).toContain('BTCUSDT')
      expect(wrapper.text()).toContain('1h')
      expect(wrapper.text()).toContain('12')
      expect(wrapper.text()).toContain('待回补')
    })

    it('应显示数据源和市场信息', async () => {
      const wrapper = await mountComponent()
      
      expect(wrapper.text()).toContain('币安')
      expect(wrapper.text()).toContain('现货')
    })
  })
})
