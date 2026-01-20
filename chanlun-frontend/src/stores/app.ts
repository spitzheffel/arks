import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Notification {
  id: number
  type: 'success' | 'error' | 'warning' | 'info'
  message: string
  duration?: number
}

export const useAppStore = defineStore('app', () => {
  // 全局 loading 状态
  const loading = ref(false)
  const loadingText = ref('')

  // 通知列表
  const notifications = ref<Notification[]>([])
  let notificationId = 0

  // 设置 loading 状态
  function setLoading(isLoading: boolean, text = '') {
    loading.value = isLoading
    loadingText.value = text
  }

  // 添加通知
  function notify(type: Notification['type'], message: string, duration = 3000) {
    const id = ++notificationId
    notifications.value.push({ id, type, message, duration })

    if (duration > 0) {
      setTimeout(() => {
        removeNotification(id)
      }, duration)
    }

    return id
  }

  // 移除通知
  function removeNotification(id: number) {
    const index = notifications.value.findIndex(n => n.id === id)
    if (index > -1) {
      notifications.value.splice(index, 1)
    }
  }

  // 快捷方法
  const success = (message: string, duration?: number) => notify('success', message, duration)
  const error = (message: string, duration?: number) => notify('error', message, duration)
  const warning = (message: string, duration?: number) => notify('warning', message, duration)
  const info = (message: string, duration?: number) => notify('info', message, duration)

  return {
    loading,
    loadingText,
    notifications,
    setLoading,
    notify,
    removeNotification,
    success,
    error,
    warning,
    info
  }
})
