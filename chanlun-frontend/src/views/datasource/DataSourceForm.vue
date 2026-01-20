<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { DataSource, DataSourceCreateRequest, DataSourceUpdateRequest, ExchangeType, ProxyType } from '@/types'

// Props
const props = defineProps<{
  visible: boolean
  dataSource?: DataSource | null
}>()

// Emits
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'submit', data: DataSourceCreateRequest | DataSourceUpdateRequest): void
  (e: 'cancel'): void
}>()

// 是否为编辑模式
const isEditMode = computed(() => !!props.dataSource)

// 表单数据
const formData = ref<{
  name: string
  exchangeType: ExchangeType
  apiKey: string
  secretKey: string
  baseUrl: string
  wsUrl: string
  proxyEnabled: boolean
  proxyType: ProxyType
  proxyHost: string
  proxyPort: number | null
  proxyUsername: string
  proxyPassword: string
  clearApiKey: boolean
  clearSecretKey: boolean
  clearProxyPassword: boolean
}>({
  name: '',
  exchangeType: 'BINANCE',
  apiKey: '',
  secretKey: '',
  baseUrl: '',
  wsUrl: '',
  proxyEnabled: false,
  proxyType: 'HTTP',
  proxyHost: '',
  proxyPort: null,
  proxyUsername: '',
  proxyPassword: '',
  clearApiKey: false,
  clearSecretKey: false,
  clearProxyPassword: false
})

// 表单验证错误
const errors = ref<Record<string, string>>({})

// 提交中状态
const submitting = ref(false)

// 交易所默认配置
const exchangeDefaults: Record<ExchangeType, { baseUrl: string; wsUrl: string }> = {
  BINANCE: {
    baseUrl: 'https://api.binance.com',
    wsUrl: 'wss://stream.binance.com:9443/ws'
  },
  OKX: {
    baseUrl: 'https://www.okx.com',
    wsUrl: 'wss://ws.okx.com:8443/ws/v5/public'
  },
  HUOBI: {
    baseUrl: 'https://api.huobi.pro',
    wsUrl: 'wss://api.huobi.pro/ws'
  }
}

// 监听 visible 变化，重置表单
watch(() => props.visible, (newVal) => {
  if (newVal) {
    resetForm()
  }
})

// 监听交易所类型变化，自动填充默认 URL
watch(() => formData.value.exchangeType, (newType) => {
  if (!isEditMode.value) {
    const defaults = exchangeDefaults[newType]
    if (defaults) {
      formData.value.baseUrl = defaults.baseUrl
      formData.value.wsUrl = defaults.wsUrl
    }
  }
})

// 重置表单
function resetForm() {
  errors.value = {}
  submitting.value = false
  
  if (props.dataSource) {
    // 编辑模式：填充现有数据
    formData.value = {
      name: props.dataSource.name,
      exchangeType: props.dataSource.exchangeType,
      apiKey: '',
      secretKey: '',
      baseUrl: props.dataSource.baseUrl || '',
      wsUrl: props.dataSource.wsUrl || '',
      proxyEnabled: props.dataSource.proxyEnabled,
      proxyType: props.dataSource.proxyType || 'HTTP',
      proxyHost: props.dataSource.proxyHost || '',
      proxyPort: props.dataSource.proxyPort || null,
      proxyUsername: props.dataSource.proxyUsername || '',
      proxyPassword: '',
      clearApiKey: false,
      clearSecretKey: false,
      clearProxyPassword: false
    }
  } else {
    // 新增模式：使用默认值
    const defaults = exchangeDefaults['BINANCE']
    formData.value = {
      name: '',
      exchangeType: 'BINANCE',
      apiKey: '',
      secretKey: '',
      baseUrl: defaults.baseUrl,
      wsUrl: defaults.wsUrl,
      proxyEnabled: false,
      proxyType: 'HTTP',
      proxyHost: '',
      proxyPort: null,
      proxyUsername: '',
      proxyPassword: '',
      clearApiKey: false,
      clearSecretKey: false,
      clearProxyPassword: false
    }
  }
}

// 验证表单
function validateForm(): boolean {
  errors.value = {}
  
  // 名称必填
  if (!formData.value.name.trim()) {
    errors.value.name = '请输入数据源名称'
  } else if (formData.value.name.length > 50) {
    errors.value.name = '名称不能超过50个字符'
  }
  
  // 代理配置验证
  if (formData.value.proxyEnabled) {
    if (!formData.value.proxyHost.trim()) {
      errors.value.proxyHost = '请输入代理地址'
    }
    if (!formData.value.proxyPort) {
      errors.value.proxyPort = '请输入代理端口'
    } else if (formData.value.proxyPort < 1 || formData.value.proxyPort > 65535) {
      errors.value.proxyPort = '端口范围 1-65535'
    }
  }
  
  return Object.keys(errors.value).length === 0
}

// 提交表单
async function handleSubmit() {
  if (!validateForm()) return
  
  submitting.value = true
  
  try {
    if (isEditMode.value) {
      // 编辑模式：构建更新请求
      const updateData: DataSourceUpdateRequest = {
        name: formData.value.name,
        exchangeType: formData.value.exchangeType,
        baseUrl: formData.value.baseUrl || undefined,
        wsUrl: formData.value.wsUrl || undefined,
        proxyEnabled: formData.value.proxyEnabled,
        proxyType: formData.value.proxyEnabled ? formData.value.proxyType : undefined,
        proxyHost: formData.value.proxyEnabled ? formData.value.proxyHost : undefined,
        proxyPort: formData.value.proxyEnabled ? (formData.value.proxyPort || undefined) : undefined,
        proxyUsername: formData.value.proxyEnabled ? (formData.value.proxyUsername || undefined) : undefined,
        clearApiKey: formData.value.clearApiKey,
        clearSecretKey: formData.value.clearSecretKey,
        clearProxyPassword: formData.value.clearProxyPassword
      }
      
      // 只有输入了新值才更新密钥
      if (formData.value.apiKey) {
        updateData.apiKey = formData.value.apiKey
      }
      if (formData.value.secretKey) {
        updateData.secretKey = formData.value.secretKey
      }
      if (formData.value.proxyEnabled && formData.value.proxyPassword) {
        updateData.proxyPassword = formData.value.proxyPassword
      }
      
      emit('submit', updateData)
    } else {
      // 新增模式：构建创建请求
      const createData: DataSourceCreateRequest = {
        name: formData.value.name,
        exchangeType: formData.value.exchangeType,
        apiKey: formData.value.apiKey || undefined,
        secretKey: formData.value.secretKey || undefined,
        baseUrl: formData.value.baseUrl || undefined,
        wsUrl: formData.value.wsUrl || undefined,
        proxyEnabled: formData.value.proxyEnabled,
        proxyType: formData.value.proxyEnabled ? formData.value.proxyType : undefined,
        proxyHost: formData.value.proxyEnabled ? formData.value.proxyHost : undefined,
        proxyPort: formData.value.proxyEnabled ? (formData.value.proxyPort || undefined) : undefined,
        proxyUsername: formData.value.proxyEnabled ? (formData.value.proxyUsername || undefined) : undefined,
        proxyPassword: formData.value.proxyEnabled ? (formData.value.proxyPassword || undefined) : undefined
      }
      
      emit('submit', createData)
    }
  } finally {
    submitting.value = false
  }
}

// 取消
function handleCancel() {
  emit('cancel')
  emit('update:visible', false)
}

// 点击遮罩层关闭
function handleOverlayClick(e: MouseEvent) {
  if (e.target === e.currentTarget && !submitting.value) {
    handleCancel()
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div
        v-if="visible"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 overflow-y-auto py-8"
        @click="handleOverlayClick"
      >
        <Transition name="scale">
          <div
            v-if="visible"
            class="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 my-auto"
            @click.stop
          >
            <!-- 头部 -->
            <div class="px-6 py-4 border-b flex items-center justify-between">
              <h3 class="text-lg font-semibold text-gray-800">
                {{ isEditMode ? '编辑数据源' : '新增数据源' }}
              </h3>
              <button
                type="button"
                class="text-gray-400 hover:text-gray-600"
                :disabled="submitting"
                @click="handleCancel"
              >
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            
            <!-- 表单内容 -->
            <form @submit.prevent="handleSubmit">
              <div class="px-6 py-4 space-y-6 max-h-[calc(100vh-200px)] overflow-y-auto">
                <!-- 基本信息 -->
                <div class="space-y-4">
                  <h4 class="text-sm font-medium text-gray-700 border-b pb-2">基本信息</h4>
                  
                  <!-- 名称 -->
                  <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">
                      数据源名称 <span class="text-red-500">*</span>
                    </label>
                    <input
                      v-model="formData.name"
                      type="text"
                      class="input"
                      :class="{ 'border-red-500': errors.name }"
                      placeholder="例如：币安主账户"
                      maxlength="50"
                    >
                    <p v-if="errors.name" class="mt-1 text-sm text-red-500">{{ errors.name }}</p>
                  </div>
                  
                  <!-- 交易所类型 -->
                  <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">
                      交易所类型 <span class="text-red-500">*</span>
                    </label>
                    <select v-model="formData.exchangeType" class="input">
                      <option value="BINANCE">币安 (Binance)</option>
                      <option value="OKX">OKX</option>
                      <option value="HUOBI">火币 (Huobi)</option>
                    </select>
                  </div>
                </div>
                
                <!-- API 配置 -->
                <div class="space-y-4">
                  <h4 class="text-sm font-medium text-gray-700 border-b pb-2">API 配置</h4>
                  
                  <!-- API Key -->
                  <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">
                      API Key
                      <span v-if="isEditMode && dataSource?.hasApiKey" class="text-green-600 text-xs ml-2">
                        (已配置)
                      </span>
                    </label>
                    <input
                      v-model="formData.apiKey"
                      type="password"
                      class="input"
                      :placeholder="isEditMode ? '留空保持不变，输入新值则更新' : '可选，用于访问私有 API'"
                      autocomplete="off"
                    >
                    <div v-if="isEditMode && dataSource?.hasApiKey" class="mt-1">
                      <label class="inline-flex items-center text-sm text-gray-600">
                        <input
                          v-model="formData.clearApiKey"
                          type="checkbox"
                          class="rounded border-gray-300 text-blue-600 mr-2"
                        >
                        清除已保存的 API Key
                      </label>
                    </div>
                  </div>
                  
                  <!-- Secret Key -->
                  <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">
                      Secret Key
                      <span v-if="isEditMode && dataSource?.hasSecretKey" class="text-green-600 text-xs ml-2">
                        (已配置)
                      </span>
                    </label>
                    <input
                      v-model="formData.secretKey"
                      type="password"
                      class="input"
                      :placeholder="isEditMode ? '留空保持不变，输入新值则更新' : '可选，用于签名请求'"
                      autocomplete="off"
                    >
                    <div v-if="isEditMode && dataSource?.hasSecretKey" class="mt-1">
                      <label class="inline-flex items-center text-sm text-gray-600">
                        <input
                          v-model="formData.clearSecretKey"
                          type="checkbox"
                          class="rounded border-gray-300 text-blue-600 mr-2"
                        >
                        清除已保存的 Secret Key
                      </label>
                    </div>
                  </div>
                  
                  <!-- Base URL -->
                  <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">
                      REST API 地址
                    </label>
                    <input
                      v-model="formData.baseUrl"
                      type="url"
                      class="input"
                      placeholder="https://api.binance.com"
                    >
                    <p class="mt-1 text-xs text-gray-500">切换交易所类型会自动填充默认地址</p>
                  </div>
                  
                  <!-- WebSocket URL -->
                  <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">
                      WebSocket 地址
                    </label>
                    <input
                      v-model="formData.wsUrl"
                      type="url"
                      class="input"
                      placeholder="wss://stream.binance.com:9443/ws"
                    >
                  </div>
                </div>
                
                <!-- 代理配置 -->
                <div class="space-y-4">
                  <div class="flex items-center justify-between border-b pb-2">
                    <h4 class="text-sm font-medium text-gray-700">代理配置</h4>
                    <label class="inline-flex items-center cursor-pointer">
                      <input
                        v-model="formData.proxyEnabled"
                        type="checkbox"
                        class="sr-only peer"
                      >
                      <div class="relative w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full rtl:peer-checked:after:-translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:start-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600" />
                      <span class="ms-2 text-sm text-gray-600">{{ formData.proxyEnabled ? '已启用' : '未启用' }}</span>
                    </label>
                  </div>
                  
                  <div v-if="formData.proxyEnabled" class="space-y-4 pl-4 border-l-2 border-blue-200">
                    <!-- 代理类型 -->
                    <div>
                      <label class="block text-sm font-medium text-gray-700 mb-1">
                        代理类型
                      </label>
                      <select v-model="formData.proxyType" class="input w-40">
                        <option value="HTTP">HTTP</option>
                        <option value="SOCKS5">SOCKS5</option>
                      </select>
                    </div>
                    
                    <!-- 代理地址和端口 -->
                    <div class="grid grid-cols-3 gap-4">
                      <div class="col-span-2">
                        <label class="block text-sm font-medium text-gray-700 mb-1">
                          代理地址 <span class="text-red-500">*</span>
                        </label>
                        <input
                          v-model="formData.proxyHost"
                          type="text"
                          class="input"
                          :class="{ 'border-red-500': errors.proxyHost }"
                          placeholder="127.0.0.1 或 proxy.example.com"
                        >
                        <p v-if="errors.proxyHost" class="mt-1 text-sm text-red-500">{{ errors.proxyHost }}</p>
                      </div>
                      <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">
                          端口 <span class="text-red-500">*</span>
                        </label>
                        <input
                          v-model.number="formData.proxyPort"
                          type="number"
                          class="input"
                          :class="{ 'border-red-500': errors.proxyPort }"
                          placeholder="7890"
                          min="1"
                          max="65535"
                        >
                        <p v-if="errors.proxyPort" class="mt-1 text-sm text-red-500">{{ errors.proxyPort }}</p>
                      </div>
                    </div>
                    
                    <!-- 代理认证 -->
                    <div class="grid grid-cols-2 gap-4">
                      <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">
                          用户名
                        </label>
                        <input
                          v-model="formData.proxyUsername"
                          type="text"
                          class="input"
                          placeholder="可选"
                        >
                      </div>
                      <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">
                          密码
                          <span v-if="isEditMode && dataSource?.hasProxyPassword" class="text-green-600 text-xs ml-2">
                            (已配置)
                          </span>
                        </label>
                        <input
                          v-model="formData.proxyPassword"
                          type="password"
                          class="input"
                          :placeholder="isEditMode ? '留空保持不变' : '可选'"
                          autocomplete="off"
                        >
                        <div v-if="isEditMode && dataSource?.hasProxyPassword" class="mt-1">
                          <label class="inline-flex items-center text-sm text-gray-600">
                            <input
                              v-model="formData.clearProxyPassword"
                              type="checkbox"
                              class="rounded border-gray-300 text-blue-600 mr-2"
                            >
                            清除已保存的密码
                          </label>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              
              <!-- 底部按钮 -->
              <div class="px-6 py-4 bg-gray-50 border-t flex justify-end gap-3">
                <button
                  type="button"
                  class="btn btn-secondary"
                  :disabled="submitting"
                  @click="handleCancel"
                >
                  取消
                </button>
                <button
                  type="submit"
                  class="btn btn-primary"
                  :disabled="submitting"
                >
                  <span v-if="submitting" class="inline-flex items-center gap-2">
                    <svg class="animate-spin h-4 w-4" viewBox="0 0 24 24">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" fill="none" />
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                    </svg>
                    保存中...
                  </span>
                  <span v-else>{{ isEditMode ? '保存修改' : '创建数据源' }}</span>
                </button>
              </div>
            </form>
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
