import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAppStore } from './app'

describe('useAppStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('loading 状态', () => {
    it('初始状态应该为 false', () => {
      const store = useAppStore()
      expect(store.loading).toBe(false)
      expect(store.loadingText).toBe('')
    })

    it('setLoading 应该正确设置状态', () => {
      const store = useAppStore()
      store.setLoading(true, '加载中...')
      expect(store.loading).toBe(true)
      expect(store.loadingText).toBe('加载中...')
    })
  })

  describe('通知功能', () => {
    it('notify 应该添加通知', () => {
      const store = useAppStore()
      const id = store.notify('success', '操作成功')
      expect(store.notifications).toHaveLength(1)
      expect(store.notifications[0]).toMatchObject({
        id,
        type: 'success',
        message: '操作成功'
      })
    })

    it('通知应该在指定时间后自动移除', () => {
      const store = useAppStore()
      store.notify('info', '测试消息', 1000)
      expect(store.notifications).toHaveLength(1)
      
      vi.advanceTimersByTime(1000)
      expect(store.notifications).toHaveLength(0)
    })

    it('removeNotification 应该移除指定通知', () => {
      const store = useAppStore()
      const id = store.notify('error', '错误消息', 0) // duration=0 不自动移除
      expect(store.notifications).toHaveLength(1)
      
      store.removeNotification(id)
      expect(store.notifications).toHaveLength(0)
    })

    it('快捷方法应该正确工作', () => {
      const store = useAppStore()
      
      store.success('成功')
      expect(store.notifications[0].type).toBe('success')
      
      store.error('错误')
      expect(store.notifications[1].type).toBe('error')
      
      store.warning('警告')
      expect(store.notifications[2].type).toBe('warning')
      
      store.info('信息')
      expect(store.notifications[3].type).toBe('info')
    })
  })
})
