<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import {
  getDataSourceList,
  getDataSource,
  createDataSource,
  updateDataSource,
  deleteDataSource,
  updateDataSourceStatus,
  testDataSourceConnection,
  testProxyConnection
} from '@/api/datasource'
import type {
  DataSource,
  DataSourcePage,
  DataSourceCreateRequest,
  DataSourceUpdateRequest,
  ConnectionTestResult,
  ProxyTestResult
} from '@/types'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import DataSourceForm from './DataSourceForm.vue'

// 状态
const loading = ref(false)
const dataSourcePage = ref<DataSourcePage | null>(null)
const error = ref<string | null>(null)

// 表单弹窗状态
const formVisible = ref(false)
const editingDataSource = ref<DataSource | null>(null)
const formLoading = ref(false)

// 分页参数
const currentPage = ref(1)
const pageSize = ref(20)

// 筛选参数
const filterExchangeType = ref<string>('')
const filterEnabled = ref<string>('')

// 确认弹窗状态
const confirmDialog = ref({
  visible: false,
  title: '',
  message: '',
  type: 'info' as 'info' | 'warning' | 'danger',
  loading: false,
  action: null as (() => Promise<void>) | null
})

// 连接测试状态
const connectionTestDialog = ref({
  visible: false,
  loading: false,
  dataSourceName: '',
  result: null as ConnectionTestResult | null,
  error: null as string | null
})

// 代理测试状态
const proxyTestDialog = ref({
  visible: false,
  loading: false,
  dataSourceName: '',
  result: null as ProxyTestResult | null,
  error: null as string | null
})

// 计算属性
const dataSources = computed(() => dataSourcePage.value?.records || [])
const totalRecords = computed(() => dataSourcePage.value?.total || 0)
const totalPages = computed(() => dataSourcePage.value?.pages || 0)

// 交易所类型映射
const exchangeTypeLabels: Record<string, string> = {
  BINANCE: '币安',
  OKX: 'OKX',
  HUOBI: '火币'
}

// 加载数据
async function loadData() {
  loading.value = true
  error.value = null
  try {
    const params: Record<string, unknown> = {
      page: currentPage.value,
      size: pageSize.value
    }
    if (filterExchangeType.value) {
      params.exchangeType = filterExchangeType.value
    }
    if (filterEnabled.value !== '') {
      params.enabled = filterEnabled.value === 'true'
    }
    dataSourcePage.value = await getDataSourceList(params)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载数据失败'
  } finally {
    loading.value = false
  }
}

// 切换页码
function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

// 筛选变更
function handleFilterChange() {
  currentPage.value = 1
  loadData()
}

// 显示确认弹窗
function showConfirm(options: {
  title: string
  message: string
  type: 'info' | 'warning' | 'danger'
  action: () => Promise<void>
}) {
  confirmDialog.value = {
    visible: true,
    title: options.title,
    message: options.message,
    type: options.type,
    loading: false,
    action: options.action
  }
}

// 确认操作
async function handleConfirm() {
  if (!confirmDialog.value.action) return
  confirmDialog.value.loading = true
  try {
    await confirmDialog.value.action()
    confirmDialog.value.visible = false
    await loadData()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    confirmDialog.value.loading = false
  }
}

// 取消确认
function handleCancel() {
  confirmDialog.value.visible = false
}

// 切换启用状态
function handleToggleStatus(ds: DataSource) {
  const newStatus = !ds.enabled
  showConfirm({
    title: newStatus ? '启用数据源' : '禁用数据源',
    message: newStatus
      ? `确定要启用数据源「${ds.name}」吗？`
      : `确定要禁用数据源「${ds.name}」吗？禁用后将停止该数据源下所有交易对的数据同步。`,
    type: newStatus ? 'info' : 'warning',
    action: async () => {
      await updateDataSourceStatus(ds.id, newStatus)
    }
  })
}

// 删除数据源
function handleDelete(ds: DataSource) {
  showConfirm({
    title: '删除数据源',
    message: `确定要删除数据源「${ds.name}」吗？删除后将无法恢复，但已同步的历史数据会保留。`,
    type: 'danger',
    action: async () => {
      await deleteDataSource(ds.id)
    }
  })
}

// 格式化时间
function formatTime(isoString: string): string {
  if (!isoString) return '-'
  const date = new Date(isoString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 格式化服务器时间
function formatServerTime(isoString: string): string {
  if (!isoString) return '-'
  const date = new Date(isoString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 打开新增表单
function handleAdd() {
  editingDataSource.value = null
  formVisible.value = true
}

// 测试数据源连接
async function handleTestConnection(ds: DataSource) {
  connectionTestDialog.value = {
    visible: true,
    loading: true,
    dataSourceName: ds.name,
    result: null,
    error: null
  }
  
  try {
    const result = await testDataSourceConnection(ds.id)
    connectionTestDialog.value.result = result
  } catch (e) {
    connectionTestDialog.value.error = e instanceof Error ? e.message : '连接测试失败'
  } finally {
    connectionTestDialog.value.loading = false
  }
}

// 关闭连接测试弹窗
function closeConnectionTestDialog() {
  connectionTestDialog.value.visible = false
}

// 测试代理连接
async function handleTestProxy(ds: DataSource) {
  if (!ds.proxyEnabled) {
    error.value = '该数据源未启用代理'
    return
  }
  
  proxyTestDialog.value = {
    visible: true,
    loading: true,
    dataSourceName: ds.name,
    result: null,
    error: null
  }
  
  try {
    const result = await testProxyConnection(ds.id)
    proxyTestDialog.value.result = result
  } catch (e) {
    proxyTestDialog.value.error = e instanceof Error ? e.message : '代理测试失败'
  } finally {
    proxyTestDialog.value.loading = false
  }
}

// 关闭代理测试弹窗
function closeProxyTestDialog() {
  proxyTestDialog.value.visible = false
}

// 打开编辑表单
async function handleEdit(ds: DataSource) {
  try {
    // 获取完整的数据源详情
    const detail = await getDataSource(ds.id)
    editingDataSource.value = detail
    formVisible.value = true
  } catch (e) {
    error.value = e instanceof Error ? e.message : '获取数据源详情失败'
  }
}

// 处理表单提交
async function handleFormSubmit(data: DataSourceCreateRequest | DataSourceUpdateRequest) {
  formLoading.value = true
  error.value = null
  try {
    if (editingDataSource.value) {
      // 编辑模式
      await updateDataSource(editingDataSource.value.id, data as DataSourceUpdateRequest)
    } else {
      // 新增模式
      await createDataSource(data as DataSourceCreateRequest)
    }
    formVisible.value = false
    await loadData()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    formLoading.value = false
  }
}

// 关闭表单
function handleFormCancel() {
  formVisible.value = false
  editingDataSource.value = null
}

// 初始化
onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 页面标题和操作 -->
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-800">数据源管理</h1>
      <button class="btn btn-primary" @click="handleAdd">
        + 新增数据源
      </button>
    </div>

    <!-- 筛选栏 -->
    <div class="card">
      <div class="flex flex-wrap gap-4 items-center">
        <div class="flex items-center gap-2">
          <label class="text-sm text-gray-600">交易所类型:</label>
          <select
            v-model="filterExchangeType"
            class="input w-40"
            @change="handleFilterChange"
          >
            <option value="">全部</option>
            <option value="BINANCE">币安</option>
            <option value="OKX">OKX</option>
            <option value="HUOBI">火币</option>
          </select>
        </div>
        <div class="flex items-center gap-2">
          <label class="text-sm text-gray-600">状态:</label>
          <select
            v-model="filterEnabled"
            class="input w-32"
            @change="handleFilterChange"
          >
            <option value="">全部</option>
            <option value="true">已启用</option>
            <option value="false">已禁用</option>
          </select>
        </div>
        <button
          class="btn btn-secondary"
          :disabled="loading"
          @click="loadData"
        >
          刷新
        </button>
      </div>
    </div>

    <!-- 错误提示 -->
    <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md">
      {{ error }}
      <button class="ml-2 underline" @click="loadData">重试</button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="card">
      <LoadingSpinner text="加载中..." />
    </div>

    <!-- 空状态 -->
    <EmptyState
      v-else-if="dataSources.length === 0"
      icon="📡"
      title="暂无数据源"
      description="还没有配置任何数据源，点击上方按钮添加第一个数据源"
    >
      <template #action>
        <button class="btn btn-primary" @click="handleAdd">
          + 新增数据源
        </button>
      </template>
    </EmptyState>

    <!-- 数据源列表 -->
    <div v-else class="card overflow-hidden p-0">
      <table class="w-full">
        <thead class="bg-gray-50 border-b">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              名称
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              交易所
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              API配置
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              代理
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              状态
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              创建时间
            </th>
            <th class="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
              操作
            </th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-200">
          <tr v-for="ds in dataSources" :key="ds.id" class="hover:bg-gray-50">
            <td class="px-6 py-4 whitespace-nowrap">
              <div class="font-medium text-gray-900">{{ ds.name }}</div>
              <div class="text-sm text-gray-500">ID: {{ ds.id }}</div>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <StatusBadge
                :status="exchangeTypeLabels[ds.exchangeType] || ds.exchangeType"
                type="info"
              />
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <div class="flex items-center gap-2 text-sm">
                <span
                  :class="ds.hasApiKey ? 'text-green-600' : 'text-gray-400'"
                  :title="ds.hasApiKey ? '已配置 API Key' : '未配置 API Key'"
                >
                  🔑
                </span>
                <span
                  :class="ds.hasSecretKey ? 'text-green-600' : 'text-gray-400'"
                  :title="ds.hasSecretKey ? '已配置 Secret Key' : '未配置 Secret Key'"
                >
                  🔐
                </span>
              </div>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <template v-if="ds.proxyEnabled">
                <StatusBadge
                  :status="`${ds.proxyType || 'HTTP'} ${ds.proxyHost}:${ds.proxyPort}`"
                  type="info"
                />
              </template>
              <span v-else class="text-gray-400 text-sm">未启用</span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <button
                :class="[
                  'relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2',
                  ds.enabled ? 'bg-blue-600' : 'bg-gray-200'
                ]"
                role="switch"
                :aria-checked="ds.enabled"
                @click="handleToggleStatus(ds)"
              >
                <span
                  :class="[
                    'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out',
                    ds.enabled ? 'translate-x-5' : 'translate-x-0'
                  ]"
                />
              </button>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
              {{ formatTime(ds.createdAt) }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
              <div class="flex items-center justify-end gap-2">
                <button
                  class="text-green-600 hover:text-green-800"
                  title="测试连接"
                  @click="handleTestConnection(ds)"
                >
                  测试
                </button>
                <button
                  v-if="ds.proxyEnabled"
                  class="text-purple-600 hover:text-purple-800"
                  title="测试代理"
                  @click="handleTestProxy(ds)"
                >
                  代理
                </button>
                <button
                  class="text-blue-600 hover:text-blue-800"
                  @click="handleEdit(ds)"
                >
                  编辑
                </button>
                <button
                  class="text-red-600 hover:text-red-800"
                  @click="handleDelete(ds)"
                >
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div
        v-if="totalPages > 1"
        class="px-6 py-4 border-t bg-gray-50 flex items-center justify-between"
      >
        <div class="text-sm text-gray-500">
          共 {{ totalRecords }} 条记录，第 {{ currentPage }} / {{ totalPages }} 页
        </div>
        <div class="flex items-center gap-2">
          <button
            class="btn btn-secondary text-sm"
            :disabled="currentPage <= 1"
            @click="handlePageChange(currentPage - 1)"
          >
            上一页
          </button>
          <button
            class="btn btn-secondary text-sm"
            :disabled="currentPage >= totalPages"
            @click="handlePageChange(currentPage + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <!-- 确认弹窗 -->
    <ConfirmDialog
      v-model:visible="confirmDialog.visible"
      :title="confirmDialog.title"
      :message="confirmDialog.message"
      :type="confirmDialog.type"
      :loading="confirmDialog.loading"
      @confirm="handleConfirm"
      @cancel="handleCancel"
    />

    <!-- 新增/编辑表单 -->
    <DataSourceForm
      v-model:visible="formVisible"
      :data-source="editingDataSource"
      @submit="handleFormSubmit"
      @cancel="handleFormCancel"
    />

    <!-- 连接测试结果弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="connectionTestDialog.visible"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          @click.self="closeConnectionTestDialog"
        >
          <div class="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
            <!-- 头部 -->
            <div class="px-6 py-4 border-b flex items-center justify-between">
              <h3 class="text-lg font-semibold text-gray-800">
                连接测试 - {{ connectionTestDialog.dataSourceName }}
              </h3>
              <button
                type="button"
                class="text-gray-400 hover:text-gray-600"
                @click="closeConnectionTestDialog"
              >
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            
            <!-- 内容 -->
            <div class="px-6 py-6">
              <!-- 加载中 -->
              <div v-if="connectionTestDialog.loading" class="flex flex-col items-center py-8">
                <svg class="animate-spin h-10 w-10 text-blue-600 mb-4" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" fill="none" />
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                </svg>
                <p class="text-gray-600">正在测试连接...</p>
              </div>
              
              <!-- 错误 -->
              <div v-else-if="connectionTestDialog.error" class="text-center py-4">
                <div class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-red-100 mb-4">
                  <svg class="w-8 h-8 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </div>
                <h4 class="text-lg font-medium text-gray-900 mb-2">连接失败</h4>
                <p class="text-gray-600">{{ connectionTestDialog.error }}</p>
              </div>
              
              <!-- 测试结果 -->
              <div v-else-if="connectionTestDialog.result" class="space-y-4">
                <!-- 状态图标 -->
                <div class="text-center">
                  <div
                    :class="[
                      'inline-flex items-center justify-center w-16 h-16 rounded-full mb-4',
                      connectionTestDialog.result.success ? 'bg-green-100' : 'bg-red-100'
                    ]"
                  >
                    <svg
                      v-if="connectionTestDialog.result.success"
                      class="w-8 h-8 text-green-600"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                    </svg>
                    <svg
                      v-else
                      class="w-8 h-8 text-red-600"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </div>
                  <h4
                    :class="[
                      'text-lg font-medium mb-2',
                      connectionTestDialog.result.success ? 'text-green-700' : 'text-red-700'
                    ]"
                  >
                    {{ connectionTestDialog.result.success ? '连接成功' : '连接失败' }}
                  </h4>
                  <p class="text-gray-600">{{ connectionTestDialog.result.message }}</p>
                </div>
                
                <!-- 详细信息 -->
                <div v-if="connectionTestDialog.result.success" class="bg-gray-50 rounded-lg p-4 space-y-2">
                  <div class="flex justify-between text-sm">
                    <span class="text-gray-500">响应延迟</span>
                    <span class="font-medium text-gray-900">{{ connectionTestDialog.result.latencyMs }} ms</span>
                  </div>
                  <div v-if="connectionTestDialog.result.serverTime" class="flex justify-between text-sm">
                    <span class="text-gray-500">服务器时间</span>
                    <span class="font-medium text-gray-900">{{ formatServerTime(connectionTestDialog.result.serverTime) }}</span>
                  </div>
                  <div v-if="connectionTestDialog.result.timeDiffMs !== undefined" class="flex justify-between text-sm">
                    <span class="text-gray-500">时间差</span>
                    <span
                      :class="[
                        'font-medium',
                        Math.abs(connectionTestDialog.result.timeDiffMs) > 1000 ? 'text-yellow-600' : 'text-gray-900'
                      ]"
                    >
                      {{ connectionTestDialog.result.timeDiffMs }} ms
                      <span v-if="Math.abs(connectionTestDialog.result.timeDiffMs) > 1000" class="text-xs">
                        (建议同步本地时间)
                      </span>
                    </span>
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 底部 -->
            <div class="px-6 py-4 bg-gray-50 border-t flex justify-end">
              <button
                class="btn btn-primary"
                @click="closeConnectionTestDialog"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 代理测试结果弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="proxyTestDialog.visible"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          @click.self="closeProxyTestDialog"
        >
          <div class="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
            <!-- 头部 -->
            <div class="px-6 py-4 border-b flex items-center justify-between">
              <h3 class="text-lg font-semibold text-gray-800">
                代理测试 - {{ proxyTestDialog.dataSourceName }}
              </h3>
              <button
                type="button"
                class="text-gray-400 hover:text-gray-600"
                @click="closeProxyTestDialog"
              >
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            
            <!-- 内容 -->
            <div class="px-6 py-6">
              <!-- 加载中 -->
              <div v-if="proxyTestDialog.loading" class="flex flex-col items-center py-8">
                <svg class="animate-spin h-10 w-10 text-purple-600 mb-4" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" fill="none" />
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                </svg>
                <p class="text-gray-600">正在测试代理连接...</p>
              </div>
              
              <!-- 错误 -->
              <div v-else-if="proxyTestDialog.error" class="text-center py-4">
                <div class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-red-100 mb-4">
                  <svg class="w-8 h-8 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </div>
                <h4 class="text-lg font-medium text-gray-900 mb-2">测试失败</h4>
                <p class="text-gray-600">{{ proxyTestDialog.error }}</p>
              </div>
              
              <!-- 测试结果 -->
              <div v-else-if="proxyTestDialog.result" class="space-y-4">
                <!-- 状态图标 -->
                <div class="text-center">
                  <div
                    :class="[
                      'inline-flex items-center justify-center w-16 h-16 rounded-full mb-4',
                      proxyTestDialog.result.success ? 'bg-green-100' : 'bg-red-100'
                    ]"
                  >
                    <svg
                      v-if="proxyTestDialog.result.success"
                      class="w-8 h-8 text-green-600"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                    </svg>
                    <svg
                      v-else
                      class="w-8 h-8 text-red-600"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </div>
                  <h4
                    :class="[
                      'text-lg font-medium mb-2',
                      proxyTestDialog.result.success ? 'text-green-700' : 'text-red-700'
                    ]"
                  >
                    {{ proxyTestDialog.result.success ? '代理连接成功' : '代理连接失败' }}
                  </h4>
                  <p class="text-gray-600">{{ proxyTestDialog.result.message }}</p>
                </div>
                
                <!-- 详细信息 -->
                <div class="bg-gray-50 rounded-lg p-4 space-y-2">
                  <div v-if="proxyTestDialog.result.latencyMs" class="flex justify-between text-sm">
                    <span class="text-gray-500">响应延迟</span>
                    <span class="font-medium text-gray-900">{{ proxyTestDialog.result.latencyMs }} ms</span>
                  </div>
                  <div v-if="proxyTestDialog.result.statusCode" class="flex justify-between text-sm">
                    <span class="text-gray-500">HTTP 状态码</span>
                    <span class="font-medium text-gray-900">{{ proxyTestDialog.result.statusCode }}</span>
                  </div>
                  <div v-if="proxyTestDialog.result.testUrl" class="flex justify-between text-sm">
                    <span class="text-gray-500">测试地址</span>
                    <span class="font-medium text-gray-900 text-xs truncate max-w-[200px]" :title="proxyTestDialog.result.testUrl">
                      {{ proxyTestDialog.result.testUrl }}
                    </span>
                  </div>
                  <div v-if="proxyTestDialog.result.responseBody && proxyTestDialog.result.success" class="text-sm">
                    <span class="text-gray-500 block mb-1">响应内容</span>
                    <pre class="bg-gray-100 p-2 rounded text-xs overflow-x-auto max-h-24">{{ proxyTestDialog.result.responseBody }}</pre>
                  </div>
                  <div v-if="proxyTestDialog.result.errorDetail && !proxyTestDialog.result.success" class="text-sm">
                    <span class="text-gray-500 block mb-1">错误详情</span>
                    <p class="text-red-600 text-xs">{{ proxyTestDialog.result.errorDetail }}</p>
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 底部 -->
            <div class="px-6 py-4 bg-gray-50 border-t flex justify-end">
              <button
                class="btn btn-primary"
                @click="closeProxyTestDialog"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
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
</style>
