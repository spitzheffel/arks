<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  visible: boolean
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  type?: 'info' | 'warning' | 'danger'
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'confirm'): void
  (e: 'cancel'): void
  (e: 'update:visible', value: boolean): void
}>()

const typeConfig = computed(() => {
  const configs = {
    info: {
      icon: 'ℹ️',
      confirmClass: 'btn btn-primary'
    },
    warning: {
      icon: '⚠️',
      confirmClass: 'btn bg-yellow-500 text-white hover:bg-yellow-600'
    },
    danger: {
      icon: '🗑️',
      confirmClass: 'btn btn-danger'
    }
  }
  return configs[props.type || 'info']
})

const handleConfirm = () => {
  if (!props.loading) {
    emit('confirm')
  }
}

const handleCancel = () => {
  if (!props.loading) {
    emit('cancel')
    emit('update:visible', false)
  }
}

const handleOverlayClick = (e: MouseEvent) => {
  if (e.target === e.currentTarget && !props.loading) {
    handleCancel()
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div
        v-if="visible"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
        @click="handleOverlayClick"
      >
        <Transition name="scale">
          <div
            v-if="visible"
            class="bg-white rounded-lg shadow-xl max-w-md w-full mx-4 overflow-hidden"
            @click.stop
          >
            <!-- 头部 -->
            <div class="px-6 py-4 border-b flex items-center gap-3">
              <span class="text-2xl">{{ typeConfig.icon }}</span>
              <h3 class="text-lg font-semibold text-gray-800">
                {{ title || '确认操作' }}
              </h3>
            </div>
            
            <!-- 内容 -->
            <div class="px-6 py-4">
              <p class="text-gray-600">{{ message }}</p>
              <slot />
            </div>
            
            <!-- 底部按钮 -->
            <div class="px-6 py-4 bg-gray-50 flex justify-end gap-3">
              <button
                type="button"
                class="btn btn-secondary"
                :disabled="loading"
                @click="handleCancel"
              >
                {{ cancelText || '取消' }}
              </button>
              <button
                type="button"
                :class="typeConfig.confirmClass"
                :disabled="loading"
                @click="handleConfirm"
              >
                <span v-if="loading" class="inline-flex items-center gap-2">
                  <svg class="animate-spin h-4 w-4" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" fill="none" />
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                  </svg>
                  处理中...
                </span>
                <span v-else>{{ confirmText || '确认' }}</span>
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.scale-enter-active,
.scale-leave-active {
  transition: all 0.2s ease;
}

.scale-enter-from,
.scale-leave-to {
  opacity: 0;
  transform: scale(0.95);
}
</style>
