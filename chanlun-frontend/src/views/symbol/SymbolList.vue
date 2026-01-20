<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSymbolList, syncSymbols, updateRealtimeSyncStatus, updateHistorySyncStatus, updateSyncIntervals } from '@/api/symbol'
import { getAllDataSources } from '@/api/datasource'
import { getAllMarkets } from '@/api/market'
import type { Symbol, SymbolPage, DataSource, Market, SymbolSyncResult } from '@/types'
import { VALID_INTERVALS } from '@/types'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'

const route = useRoute()
const router = useRouter()

// 状态
const loading = ref(false)
const symbolPage = ref<SymbolPage | null>(null)
const dataSources = ref<DataSource[]>([])
const markets = ref<Market[]>([])
const error = ref<string | null>(null)

// 分页参数
const currentPage = ref(1)
const pageSize = ref(20)

// 筛选参数
const filterDataSourceId = ref<string>('')
const filterMarketId = ref<string>('')
const filterKeyword = ref<string>('')
const filterRealtimeSync = ref<string>('')
const filterHistorySync = ref<string>('')

// 同步状态
const syncing = ref(false)
const syncResult = ref<SymbolSyncResult | null>(null)
const showSyncResult = ref(false)

// 实时同步开关状态
const showRealtimeSyncConfirm = ref(false)
const realtimeSyncTarget = ref<Symbol | null>(null)
const realtimeSyncLoading = ref(false)

// 历史同步开关状态
const showHistorySyncConfirm = ref(false)
const historySyncTarget = ref<Symbol | null>(null)
const historySyncLoading = ref(false)

// 同步周期配置弹窗状态
const showIntervalsDialog = ref(false)
const intervalsTarget = ref<Symbol | null>(null)
const selectedIntervals = ref<string[]>([])
const intervalsLoading = ref(false)

// 计算属性
const symbols = computed(() => symbolPage.value?.records || [])
const totalRecords = computed(() => symbolPage.value?.total || 0)
const totalPages = computed(() => symbolPage.value?.pages || 0)

// 根据数据源筛选的市场列表
const filteredMarkets = computed(() => {
  if (!filterDataSourceId.value) {
    return markets.value
  }
  return markets.value.filter(m => m.dataSourceId === Number(filterDataSourceId.value))
})

// 当前选中的市场名称
const selectedMarketName = computed(() => {
  if (!filterMarketId.value) return ''
  const market = markets.value.find(m => m.id === Number(filterMarketId.value))
  return market?.name || ''
})

// 市场类型映射
const marketTypeLabels: Record<string, string> = {
  SPOT: '现货',
  USDT_M: 'U本位',
  COIN_M: '币本位'
}

// 市场类型颜色
const marketTypeColors: Record<string, 'success' | 'info' | 'warning'> = {
  SPOT: 'success',
  USDT_M: 'info',
  COIN_M: 'warning'
}

// 交易对状态颜色
const statusColors: Record<string, 'success' | 'warning' | 'danger' | 'default'> = {
  TRADING: 'success',
  HALT: 'danger',
  BREAK: 'warning'
}

// 加载数据源和市场
async function loadFilters() {
  try {
    const [dsResult, marketResult] = await Promise.all([
      getAllDataSources({ enabled: true }),
      getAllMarkets({ enabled: true })
    ])
    dataSources.value = dsResult
    markets.value = marketResult
  } catch (e) {
    console.error('加载筛选数据失败:', e)
  }
}

// 加载交易对数据
async function loadData() {
  loading.value = true
  error.value = null
  try {
    const params: Record<string, unknown> = {
      page: currentPage.value,
      size: pageSize.value
    }
    if (filterDataSourceId.value) {
      params.dataSourceId = Number(filterDataSourceId.value)
    }
    if (filterMarketId.value) {
      params.marketId = Number(filterMarketId.value)
    }
    if (filterKeyword.value.trim()) {
      params.keyword = filterKeyword.value.trim()
    }
    if (filterRealtimeSync.value !== '') {
      params.realtimeSyncEnabled = filterRealtimeSync.value === 'true'
    }
    if (filterHistorySync.value !== '') {
      params.historySyncEnabled = filterHistorySync.value === 'true'
    }
    symbolPage.value = await getSymbolList(params)
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
  updateUrlParams()
}

// 数据源变更时清空市场筛选
function handleDataSourceChange() {
  filterMarketId.value = ''
  handleFilterChange()
}

// 搜索（防抖）
let searchTimeout: ReturnType<typeof setTimeout> | null = null
function handleSearch() {
  if (searchTimeout) {
    clearTimeout(searchTimeout)
  }
  searchTimeout = setTimeout(() => {
    currentPage.value = 1
    loadData()
    updateUrlParams()
  }, 300)
}

// 清空搜索
function clearSearch() {
  filterKeyword.value = ''
  handleFilterChange()
}

// 重置筛选
function resetFilters() {
  filterDataSourceId.value = ''
  filterMarketId.value = ''
  filterKeyword.value = ''
  filterRealtimeSync.value = ''
  filterHistorySync.value = ''
  currentPage.value = 1
  loadData()
  router.replace({ query: {} })
}

// 更新URL参数
function updateUrlParams() {
  const query: Record<string, string> = {}
  if (filterDataSourceId.value) query.dataSourceId = filterDataSourceId.value
  if (filterMarketId.value) query.marketId = filterMarketId.value
  if (filterKeyword.value) query.keyword = filterKeyword.value
  if (filterRealtimeSync.value) query.realtimeSync = filterRealtimeSync.value
  if (filterHistorySync.value) query.historySync = filterHistorySync.value
  router.replace({ query })
}

// 从URL读取筛选参数
function loadUrlParams() {
  const { dataSourceId, marketId, keyword, realtimeSync, historySync } = route.query
  if (dataSourceId) filterDataSourceId.value = String(dataSourceId)
  if (marketId) filterMarketId.value = String(marketId)
  if (keyword) filterKeyword.value = String(keyword)
  if (realtimeSync) filterRealtimeSync.value = String(realtimeSync)
  if (historySync) filterHistorySync.value = String(historySync)
}

// 同步交易对
async function handleSyncSymbols() {
  if (!filterMarketId.value) {
    error.value = '请先选择一个市场'
    return
  }
  
  syncing.value = true
  error.value = null
  syncResult.value = null
  
  try {
    const result = await syncSymbols(Number(filterMarketId.value))
    syncResult.value = result
    showSyncResult.value = true
    
    // 刷新数据
    await loadData()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '同步交易对失败'
  } finally {
    syncing.value = false
  }
}

// 关闭同步结果弹窗
function closeSyncResult() {
  showSyncResult.value = false
  syncResult.value = null
}

// 打开实时同步确认弹窗
function openRealtimeSyncConfirm(symbol: Symbol) {
  realtimeSyncTarget.value = symbol
  showRealtimeSyncConfirm.value = true
}

// 关闭实时同步确认弹窗
function closeRealtimeSyncConfirm() {
  showRealtimeSyncConfirm.value = false
  realtimeSyncTarget.value = null
}

// 确认切换实时同步状态
async function confirmRealtimeSyncToggle() {
  if (!realtimeSyncTarget.value) return
  
  const symbol = realtimeSyncTarget.value
  const newEnabled = !symbol.realtimeSyncEnabled
  
  realtimeSyncLoading.value = true
  error.value = null
  
  try {
    await updateRealtimeSyncStatus(symbol.id, newEnabled)
    // 更新本地数据
    symbol.realtimeSyncEnabled = newEnabled
    closeRealtimeSyncConfirm()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    realtimeSyncLoading.value = false
  }
}

// 获取实时同步确认弹窗消息
const realtimeSyncConfirmMessage = computed(() => {
  if (!realtimeSyncTarget.value) return ''
  const symbol = realtimeSyncTarget.value
  if (symbol.realtimeSyncEnabled) {
    return `确定要关闭交易对 ${symbol.symbol} 的实时同步吗？关闭后将停止接收实时K线数据。`
  }
  return `确定要开启交易对 ${symbol.symbol} 的实时同步吗？开启后将通过 WebSocket 接收实时K线数据。`
})

// 获取实时同步确认弹窗类型
const realtimeSyncConfirmType = computed(() => {
  if (!realtimeSyncTarget.value) return 'info'
  return realtimeSyncTarget.value.realtimeSyncEnabled ? 'warning' : 'info'
})

// 打开历史同步确认弹窗
function openHistorySyncConfirm(symbol: Symbol) {
  historySyncTarget.value = symbol
  showHistorySyncConfirm.value = true
}

// 关闭历史同步确认弹窗
function closeHistorySyncConfirm() {
  showHistorySyncConfirm.value = false
  historySyncTarget.value = null
}

// 确认切换历史同步状态
async function confirmHistorySyncToggle() {
  if (!historySyncTarget.value) return
  
  const symbol = historySyncTarget.value
  const newEnabled = !symbol.historySyncEnabled
  
  historySyncLoading.value = true
  error.value = null
  
  try {
    await updateHistorySyncStatus(symbol.id, newEnabled)
    // 更新本地数据
    symbol.historySyncEnabled = newEnabled
    closeHistorySyncConfirm()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    historySyncLoading.value = false
  }
}

// 获取历史同步确认弹窗消息
const historySyncConfirmMessage = computed(() => {
  if (!historySyncTarget.value) return ''
  const symbol = historySyncTarget.value
  if (symbol.historySyncEnabled) {
    return `确定要关闭交易对 ${symbol.symbol} 的历史同步吗？关闭后将停止同步历史K线数据。`
  }
  return `确定要开启交易对 ${symbol.symbol} 的历史同步吗？开启后将可以同步历史K线数据。`
})

// 获取历史同步确认弹窗类型
const historySyncConfirmType = computed(() => {
  if (!historySyncTarget.value) return 'info'
  return historySyncTarget.value.historySyncEnabled ? 'warning' : 'info'
})

// 打开同步周期配置弹窗
function openIntervalsDialog(symbol: Symbol) {
  intervalsTarget.value = symbol
  selectedIntervals.value = [...(symbol.syncIntervals || [])]
  showIntervalsDialog.value = true
}

// 关闭同步周期配置弹窗
function closeIntervalsDialog() {
  showIntervalsDialog.value = false
  intervalsTarget.value = null
  selectedIntervals.value = []
}

// 切换周期选择
function toggleInterval(interval: string) {
  const index = selectedIntervals.value.indexOf(interval)
  if (index === -1) {
    selectedIntervals.value.push(interval)
  } else {
    selectedIntervals.value.splice(index, 1)
  }
}

// 检查周期是否被选中
function isIntervalSelected(interval: string): boolean {
  return selectedIntervals.value.includes(interval)
}

// 保存同步周期配置
async function saveIntervals() {
  if (!intervalsTarget.value) return
  
  intervalsLoading.value = true
  error.value = null
  
  try {
    const updatedSymbol = await updateSyncIntervals(intervalsTarget.value.id, selectedIntervals.value)
    // 更新本地数据
    intervalsTarget.value.syncIntervals = updatedSymbol.syncIntervals
    closeIntervalsDialog()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存同步周期失败'
  } finally {
    intervalsLoading.value = false
  }
}

// 周期分组（用于弹窗展示）
const intervalGroups = computed(() => [
  {
    label: '分钟级',
    intervals: VALID_INTERVALS.filter(i => i.endsWith('m'))
  },
  {
    label: '小时级',
    intervals: VALID_INTERVALS.filter(i => i.endsWith('h'))
  },
  {
    label: '日级及以上',
    intervals: VALID_INTERVALS.filter(i => i.endsWith('d') || i.endsWith('w') || i.endsWith('M'))
  }
])

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

// 初始化
onMounted(async () => {
  loadUrlParams()
  await loadFilters()
  await loadData()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 页面标题 -->
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-800">交易对管理</h1>
      <div class="text-sm text-gray-500">
        共 {{ totalRecords }} 个交易对
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="card">
      <div class="flex flex-wrap gap-4 items-center">
        <!-- 数据源筛选 -->
        <div class="flex items-center gap-2">
          <label class="text-sm text-gray-600">数据源:</label>
          <select
            v-model="filterDataSourceId"
            class="input w-36"
            @change="handleDataSourceChange"
          >
            <option value="">全部</option>
            <option
              v-for="ds in dataSources"
              :key="ds.id"
              :value="ds.id"
            >
              {{ ds.name }}
            </option>
          </select>
        </div>

        <!-- 市场筛选 -->
        <div class="flex items-center gap-2">
          <label class="text-sm text-gray-600">市场:</label>
          <select
            v-model="filterMarketId"
            class="input w-40"
            @change="handleFilterChange"
          >
            <option value="">全部</option>
            <option
              v-for="market in filteredMarkets"
              :key="market.id"
              :value="market.id"
            >
              {{ market.name }}
            </option>
          </select>
        </div>

        <!-- 实时同步筛选 -->
        <div class="flex items-center gap-2">
          <label class="text-sm text-gray-600">实时同步:</label>
          <select
            v-model="filterRealtimeSync"
            class="input w-28"
            @change="handleFilterChange"
          >
            <option value="">全部</option>
            <option value="true">已开启</option>
            <option value="false">未开启</option>
          </select>
        </div>

        <!-- 历史同步筛选 -->
        <div class="flex items-center gap-2">
          <label class="text-sm text-gray-600">历史同步:</label>
          <select
            v-model="filterHistorySync"
            class="input w-28"
            @change="handleFilterChange"
          >
            <option value="">全部</option>
            <option value="true">已开启</option>
            <option value="false">未开启</option>
          </select>
        </div>

        <!-- 搜索框 -->
        <div class="flex items-center gap-2 flex-1 min-w-[200px]">
          <label class="text-sm text-gray-600">搜索:</label>
          <div class="relative flex-1">
            <input
              v-model="filterKeyword"
              type="text"
              class="input w-full pr-8"
              placeholder="交易对代码、基础货币、报价货币"
              @input="handleSearch"
              @keyup.enter="handleFilterChange"
            >
            <button
              v-if="filterKeyword"
              class="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              @click="clearSearch"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="flex items-center gap-2">
          <button
            v-if="filterMarketId"
            class="btn btn-primary flex items-center gap-1"
            :disabled="syncing"
            @click="handleSyncSymbols"
          >
            <svg
              v-if="syncing"
              class="animate-spin h-4 w-4"
              fill="none"
              viewBox="0 0 24 24"
            >
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
            </svg>
            <svg
              v-else
              class="h-4 w-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            {{ syncing ? '同步中...' : '同步交易对' }}
          </button>
          <button
            class="btn btn-secondary"
            :disabled="loading"
            @click="loadData"
          >
            刷新
          </button>
          <button
            class="btn btn-secondary"
            @click="resetFilters"
          >
            重置
          </button>
        </div>
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
      v-else-if="symbols.length === 0"
      icon="📊"
      title="暂无交易对"
      :description="filterKeyword || filterDataSourceId || filterMarketId ? '没有找到匹配的交易对，请尝试调整筛选条件' : '请先同步市场的交易对列表'"
    >
      <template #action>
        <button
          v-if="filterKeyword || filterDataSourceId || filterMarketId"
          class="btn btn-primary"
          @click="resetFilters"
        >
          重置筛选
        </button>
        <router-link
          v-else
          to="/markets"
          class="btn btn-primary"
        >
          前往市场管理
        </router-link>
      </template>
    </EmptyState>

    <!-- 交易对列表 -->
    <div v-else class="card overflow-hidden p-0">
      <table class="w-full">
        <thead class="bg-gray-50 border-b">
          <tr>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              交易对
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              数据源 / 市场
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              精度
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              实时同步
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              历史同步
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              同步周期
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              状态
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              更新时间
            </th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-200">
          <tr
            v-for="symbol in symbols"
            :key="symbol.id"
            class="hover:bg-gray-50"
          >
            <!-- 交易对 -->
            <td class="px-4 py-4 whitespace-nowrap">
              <div class="font-medium text-gray-900">{{ symbol.symbol }}</div>
              <div class="text-sm text-gray-500">
                {{ symbol.baseAsset }} / {{ symbol.quoteAsset }}
              </div>
            </td>

            <!-- 数据源 / 市场 -->
            <td class="px-4 py-4 whitespace-nowrap">
              <div class="text-sm text-gray-900">{{ symbol.dataSourceName }}</div>
              <div class="flex items-center gap-1 mt-1">
                <StatusBadge
                  :status="marketTypeLabels[symbol.marketType] || symbol.marketType"
                  :type="marketTypeColors[symbol.marketType] || 'default'"
                  size="sm"
                />
                <span class="text-xs text-gray-500">{{ symbol.marketName }}</span>
              </div>
            </td>

            <!-- 精度 -->
            <td class="px-4 py-4 whitespace-nowrap">
              <div class="text-sm text-gray-600">
                <span class="text-gray-400">价格:</span> {{ symbol.pricePrecision }}
              </div>
              <div class="text-sm text-gray-600">
                <span class="text-gray-400">数量:</span> {{ symbol.quantityPrecision }}
              </div>
            </td>

            <!-- 实时同步开关 -->
            <td class="px-4 py-4 whitespace-nowrap">
              <button
                type="button"
                class="relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
                :class="symbol.realtimeSyncEnabled ? 'bg-blue-600' : 'bg-gray-200'"
                role="switch"
                :aria-checked="symbol.realtimeSyncEnabled"
                @click="openRealtimeSyncConfirm(symbol)"
              >
                <span
                  class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
                  :class="symbol.realtimeSyncEnabled ? 'translate-x-5' : 'translate-x-0'"
                />
              </button>
            </td>

            <!-- 历史同步开关 -->
            <td class="px-4 py-4 whitespace-nowrap">
              <button
                type="button"
                class="relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
                :class="symbol.historySyncEnabled ? 'bg-blue-600' : 'bg-gray-200'"
                role="switch"
                :aria-checked="symbol.historySyncEnabled"
                @click="openHistorySyncConfirm(symbol)"
              >
                <span
                  class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
                  :class="symbol.historySyncEnabled ? 'translate-x-5' : 'translate-x-0'"
                />
              </button>
            </td>

            <!-- 同步周期 -->
            <td class="px-4 py-4">
              <div
                class="cursor-pointer hover:bg-gray-100 rounded p-1 -m-1 transition-colors"
                @click="openIntervalsDialog(symbol)"
              >
                <div v-if="symbol.syncIntervals && symbol.syncIntervals.length > 0" class="flex flex-wrap gap-1">
                  <span
                    v-for="interval in symbol.syncIntervals"
                    :key="interval"
                    class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-800"
                  >
                    {{ interval }}
                  </span>
                </div>
                <span v-else class="text-sm text-gray-400 flex items-center gap-1">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                  </svg>
                  点击配置
                </span>
              </div>
            </td>

            <!-- 状态 -->
            <td class="px-4 py-4 whitespace-nowrap">
              <StatusBadge
                :status="symbol.status"
                :type="statusColors[symbol.status] || 'default'"
              />
            </td>

            <!-- 更新时间 -->
            <td class="px-4 py-4 whitespace-nowrap text-sm text-gray-500">
              {{ formatTime(symbol.updatedAt) }}
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

    <!-- 同步结果弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="showSyncResult && syncResult"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          @click.self="closeSyncResult"
        >
          <div class="bg-white rounded-lg shadow-xl max-w-md w-full mx-4 overflow-hidden">
            <div class="px-6 py-4 border-b flex items-center gap-3">
              <div
                :class="[
                  'w-10 h-10 rounded-full flex items-center justify-center',
                  syncResult.success ? 'bg-green-100' : 'bg-red-100'
                ]"
              >
                <svg
                  v-if="syncResult.success"
                  class="w-6 h-6 text-green-600"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                </svg>
                <svg
                  v-else
                  class="w-6 h-6 text-red-600"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </div>
              <div>
                <h3 class="text-lg font-semibold text-gray-900">
                  {{ syncResult.success ? '同步成功' : '同步失败' }}
                </h3>
                <p class="text-sm text-gray-500">{{ syncResult.message }}</p>
              </div>
            </div>
            <div class="px-6 py-4">
              <div class="grid grid-cols-4 gap-3 text-center">
                <div class="bg-gray-50 rounded-lg p-3">
                  <div class="text-2xl font-bold text-gray-900">{{ syncResult.syncedCount }}</div>
                  <div class="text-xs text-gray-500">总计</div>
                </div>
                <div class="bg-green-50 rounded-lg p-3">
                  <div class="text-2xl font-bold text-green-600">{{ syncResult.createdCount }}</div>
                  <div class="text-xs text-gray-500">新增</div>
                </div>
                <div class="bg-blue-50 rounded-lg p-3">
                  <div class="text-2xl font-bold text-blue-600">{{ syncResult.updatedCount }}</div>
                  <div class="text-xs text-gray-500">更新</div>
                </div>
                <div class="bg-gray-50 rounded-lg p-3">
                  <div class="text-2xl font-bold text-gray-600">{{ syncResult.existingCount }}</div>
                  <div class="text-xs text-gray-500">已存在</div>
                </div>
              </div>
              <p v-if="selectedMarketName" class="mt-4 text-sm text-gray-600 text-center">
                市场: {{ selectedMarketName }}
              </p>
            </div>
            <div class="px-6 py-4 bg-gray-50 border-t flex justify-end">
              <button
                class="btn btn-primary"
                @click="closeSyncResult"
              >
                确定
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 实时同步确认弹窗 -->
    <ConfirmDialog
      v-model:visible="showRealtimeSyncConfirm"
      :title="realtimeSyncTarget?.realtimeSyncEnabled ? '关闭实时同步' : '开启实时同步'"
      :message="realtimeSyncConfirmMessage"
      :type="realtimeSyncConfirmType"
      :loading="realtimeSyncLoading"
      :confirm-text="realtimeSyncTarget?.realtimeSyncEnabled ? '确认关闭' : '确认开启'"
      @confirm="confirmRealtimeSyncToggle"
      @cancel="closeRealtimeSyncConfirm"
    />

    <!-- 历史同步确认弹窗 -->
    <ConfirmDialog
      v-model:visible="showHistorySyncConfirm"
      :title="historySyncTarget?.historySyncEnabled ? '关闭历史同步' : '开启历史同步'"
      :message="historySyncConfirmMessage"
      :type="historySyncConfirmType"
      :loading="historySyncLoading"
      :confirm-text="historySyncTarget?.historySyncEnabled ? '确认关闭' : '确认开启'"
      @confirm="confirmHistorySyncToggle"
      @cancel="closeHistorySyncConfirm"
    />

    <!-- 同步周期配置弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="showIntervalsDialog && intervalsTarget"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          @click.self="closeIntervalsDialog"
        >
          <div class="bg-white rounded-lg shadow-xl max-w-lg w-full mx-4 overflow-hidden">
            <!-- 弹窗标题 -->
            <div class="px-6 py-4 border-b">
              <h3 class="text-lg font-semibold text-gray-900">配置同步周期</h3>
              <p class="text-sm text-gray-500 mt-1">
                交易对: {{ intervalsTarget.symbol }}
              </p>
            </div>

            <!-- 周期选择区域 -->
            <div class="px-6 py-4 space-y-4 max-h-96 overflow-y-auto">
              <div
                v-for="group in intervalGroups"
                :key="group.label"
                class="space-y-2"
              >
                <div class="text-sm font-medium text-gray-700">{{ group.label }}</div>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="interval in group.intervals"
                    :key="interval"
                    type="button"
                    class="px-3 py-1.5 rounded-md text-sm font-medium transition-colors border"
                    :class="isIntervalSelected(interval)
                      ? 'bg-blue-600 text-white border-blue-600'
                      : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'"
                    @click="toggleInterval(interval)"
                  >
                    {{ interval }}
                  </button>
                </div>
              </div>

              <!-- 已选择提示 -->
              <div class="pt-2 border-t">
                <div class="text-sm text-gray-600">
                  已选择 {{ selectedIntervals.length }} 个周期
                  <span v-if="selectedIntervals.length > 0" class="text-gray-400">
                    ({{ selectedIntervals.join(', ') }})
                  </span>
                </div>
              </div>
            </div>

            <!-- 弹窗底部按钮 -->
            <div class="px-6 py-4 bg-gray-50 border-t flex justify-end gap-3">
              <button
                class="btn btn-secondary"
                :disabled="intervalsLoading"
                @click="closeIntervalsDialog"
              >
                取消
              </button>
              <button
                class="btn btn-primary"
                :disabled="intervalsLoading"
                @click="saveIntervals"
              >
                <svg
                  v-if="intervalsLoading"
                  class="animate-spin -ml-1 mr-2 h-4 w-4"
                  fill="none"
                  viewBox="0 0 24 24"
                >
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                </svg>
                {{ intervalsLoading ? '保存中...' : '保存' }}
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
