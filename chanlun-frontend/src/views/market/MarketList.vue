<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getAllMarkets, updateMarketStatus } from '@/api/market'
import { getAllDataSources, syncMarkets } from '@/api/datasource'
import type { Market, MarketGroup, DataSource, MarketSyncResult } from '@/types'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'

// 状态
const loading = ref(false)
const markets = ref<Market[]>([])
const dataSources = ref<DataSource[]>([])
const error = ref<string | null>(null)

// 同步状态
const syncingDataSourceId = ref<number | null>(null)
const syncResult = ref<MarketSyncResult | null>(null)
const showSyncResult = ref(false)

// 筛选参数
const filterDataSourceId = ref<string>('')
const filterMarketType = ref<string>('')
const filterEnabled = ref<string>('')

// 展开状态（按数据源ID）
const expandedGroups = ref<Set<number>>(new Set())

// 确认弹窗状态
const confirmDialog = ref({
  visible: false,
  title: '',
  message: '',
  type: 'info' as 'info' | 'warning' | 'danger',
  loading: false,
  action: null as (() => Promise<void>) | null
})

// 市场类型映射
const marketTypeLabels: Record<string, string> = {
  SPOT: '现货',
  USDT_M: 'U本位合约',
  COIN_M: '币本位合约'
}

// 市场类型颜色
const marketTypeColors: Record<string, 'success' | 'info' | 'warning'> = {
  SPOT: 'success',
  USDT_M: 'info',
  COIN_M: 'warning'
}

// 按数据源分组的市场
const marketGroups = computed<MarketGroup[]>(() => {
  const groupMap = new Map<number, MarketGroup>()
  
  // 先用数据源初始化分组（确保即使没有市场也显示数据源）
  for (const ds of dataSources.value) {
    // 如果有数据源筛选，只显示匹配的数据源
    if (filterDataSourceId.value && ds.id !== Number(filterDataSourceId.value)) {
      continue
    }
    groupMap.set(ds.id, {
      dataSourceId: ds.id,
      dataSourceName: ds.name,
      markets: []
    })
  }
  
  // 将市场分配到对应的分组
  for (const market of markets.value) {
    const group = groupMap.get(market.dataSourceId)
    if (group) {
      group.markets.push(market)
    }
  }
  
  return Array.from(groupMap.values())
})

// 加载数据
async function loadData() {
  loading.value = true
  error.value = null
  try {
    // 并行加载数据源和市场
    const [dsResult, marketResult] = await Promise.all([
      getAllDataSources({ enabled: true }),
      getAllMarkets(buildFilterParams())
    ])
    dataSources.value = dsResult
    markets.value = marketResult
    
    // 默认展开所有分组
    expandedGroups.value = new Set(dsResult.map(ds => ds.id))
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载数据失败'
  } finally {
    loading.value = false
  }
}

// 构建筛选参数
function buildFilterParams(): Record<string, unknown> {
  const params: Record<string, unknown> = {}
  if (filterDataSourceId.value) {
    params.dataSourceId = Number(filterDataSourceId.value)
  }
  if (filterMarketType.value) {
    params.marketType = filterMarketType.value
  }
  if (filterEnabled.value !== '') {
    params.enabled = filterEnabled.value === 'true'
  }
  return params
}

// 筛选变更
function handleFilterChange() {
  loadData()
}

// 切换分组展开状态
function toggleGroup(dataSourceId: number) {
  if (expandedGroups.value.has(dataSourceId)) {
    expandedGroups.value.delete(dataSourceId)
  } else {
    expandedGroups.value.add(dataSourceId)
  }
}

// 展开所有分组
function expandAll() {
  expandedGroups.value = new Set(dataSources.value.map(ds => ds.id))
}

// 折叠所有分组
function collapseAll() {
  expandedGroups.value.clear()
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
function handleToggleStatus(market: Market) {
  const newStatus = !market.enabled
  showConfirm({
    title: newStatus ? '启用市场' : '禁用市场',
    message: newStatus
      ? `确定要启用市场「${market.name}」吗？`
      : `确定要禁用市场「${market.name}」吗？禁用后将停止该市场下所有交易对的数据同步。`,
    type: newStatus ? 'info' : 'warning',
    action: async () => {
      await updateMarketStatus(market.id, newStatus)
    }
  })
}

// 同步市场
async function handleSyncMarkets(dataSourceId: number, dataSourceName: string) {
  syncingDataSourceId.value = dataSourceId
  error.value = null
  syncResult.value = null
  
  try {
    const result = await syncMarkets(dataSourceId)
    syncResult.value = result
    showSyncResult.value = true
    
    // 刷新数据
    await loadData()
  } catch (e) {
    error.value = e instanceof Error ? e.message : `同步市场失败: ${dataSourceName}`
  } finally {
    syncingDataSourceId.value = null
  }
}

// 关闭同步结果弹窗
function closeSyncResult() {
  showSyncResult.value = false
  syncResult.value = null
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

// 初始化
onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 页面标题 -->
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-800">市场管理</h1>
      <div class="flex items-center gap-2">
        <button
          class="btn btn-secondary text-sm"
          @click="expandAll"
        >
          展开全部
        </button>
        <button
          class="btn btn-secondary text-sm"
          @click="collapseAll"
        >
          折叠全部
        </button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="card">
      <div class="flex flex-wrap gap-4 items-center">
        <div class="flex items-center gap-2">
          <label class="text-sm text-gray-600">数据源:</label>
          <select
            v-model="filterDataSourceId"
            class="input w-40"
            @change="handleFilterChange"
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
        <div class="flex items-center gap-2">
          <label class="text-sm text-gray-600">市场类型:</label>
          <select
            v-model="filterMarketType"
            class="input w-40"
            @change="handleFilterChange"
          >
            <option value="">全部</option>
            <option value="SPOT">现货</option>
            <option value="USDT_M">U本位合约</option>
            <option value="COIN_M">币本位合约</option>
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
      v-else-if="marketGroups.length === 0"
      icon="🏪"
      title="暂无市场数据"
      description="请先添加数据源，然后同步市场信息"
    />

    <!-- 市场列表（按数据源分组） -->
    <div v-else class="space-y-4">
      <div
        v-for="group in marketGroups"
        :key="group.dataSourceId"
        class="card overflow-hidden p-0"
      >
        <!-- 分组头部 -->
        <div
          class="px-6 py-4 bg-gray-50 border-b flex items-center justify-between cursor-pointer hover:bg-gray-100 transition-colors"
          @click="toggleGroup(group.dataSourceId)"
        >
          <div class="flex items-center gap-3">
            <svg
              :class="[
                'w-5 h-5 text-gray-500 transition-transform',
                expandedGroups.has(group.dataSourceId) ? 'rotate-90' : ''
              ]"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
            </svg>
            <div>
              <h3 class="font-semibold text-gray-800">{{ group.dataSourceName }}</h3>
              <p class="text-sm text-gray-500">{{ group.markets.length }} 个市场</p>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <button
              class="btn btn-primary text-sm flex items-center gap-1"
              :disabled="syncingDataSourceId === group.dataSourceId"
              @click.stop="handleSyncMarkets(group.dataSourceId, group.dataSourceName)"
            >
              <svg
                v-if="syncingDataSourceId === group.dataSourceId"
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
              {{ syncingDataSourceId === group.dataSourceId ? '同步中...' : '同步市场' }}
            </button>
            <StatusBadge
              :status="`${group.markets.filter(m => m.enabled).length} / ${group.markets.length} 已启用`"
              :type="group.markets.some(m => m.enabled) ? 'success' : 'default'"
            />
          </div>
        </div>

        <!-- 分组内容 -->
        <Transition name="collapse">
          <div v-show="expandedGroups.has(group.dataSourceId)">
            <!-- 空市场提示 -->
            <div
              v-if="group.markets.length === 0"
              class="px-6 py-8 text-center text-gray-500"
            >
              <p>该数据源暂无市场数据</p>
              <p class="text-sm mt-1">请点击「同步市场」按钮获取市场信息</p>
            </div>

            <!-- 市场表格 -->
            <table v-else class="w-full">
              <thead class="bg-gray-50 border-b">
                <tr>
                  <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    市场名称
                  </th>
                  <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    市场类型
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
                <tr
                  v-for="market in group.markets"
                  :key="market.id"
                  class="hover:bg-gray-50"
                >
                  <td class="px-6 py-4 whitespace-nowrap">
                    <div class="font-medium text-gray-900">{{ market.name }}</div>
                    <div class="text-sm text-gray-500">ID: {{ market.id }}</div>
                  </td>
                  <td class="px-6 py-4 whitespace-nowrap">
                    <StatusBadge
                      :status="marketTypeLabels[market.marketType] || market.marketType"
                      :type="marketTypeColors[market.marketType] || 'default'"
                    />
                  </td>
                  <td class="px-6 py-4 whitespace-nowrap">
                    <button
                      :class="[
                        'relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2',
                        market.enabled ? 'bg-blue-600' : 'bg-gray-200'
                      ]"
                      role="switch"
                      :aria-checked="market.enabled"
                      @click="handleToggleStatus(market)"
                    >
                      <span
                        :class="[
                          'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out',
                          market.enabled ? 'translate-x-5' : 'translate-x-0'
                        ]"
                      />
                    </button>
                  </td>
                  <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {{ formatTime(market.createdAt) }}
                  </td>
                  <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                    <router-link
                      :to="{ name: 'symbols', query: { marketId: market.id } }"
                      class="text-blue-600 hover:text-blue-800"
                    >
                      查看交易对
                    </router-link>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </Transition>
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
              <div class="grid grid-cols-3 gap-4 text-center">
                <div class="bg-gray-50 rounded-lg p-3">
                  <div class="text-2xl font-bold text-gray-900">{{ syncResult.syncedCount }}</div>
                  <div class="text-sm text-gray-500">总计</div>
                </div>
                <div class="bg-green-50 rounded-lg p-3">
                  <div class="text-2xl font-bold text-green-600">{{ syncResult.createdCount }}</div>
                  <div class="text-sm text-gray-500">新增</div>
                </div>
                <div class="bg-blue-50 rounded-lg p-3">
                  <div class="text-2xl font-bold text-blue-600">{{ syncResult.existingCount }}</div>
                  <div class="text-sm text-gray-500">已存在</div>
                </div>
              </div>
              <div v-if="syncResult.markets && syncResult.markets.length > 0" class="mt-4">
                <h4 class="text-sm font-medium text-gray-700 mb-2">同步的市场:</h4>
                <div class="space-y-2">
                  <div
                    v-for="market in syncResult.markets"
                    :key="market.id"
                    class="flex items-center justify-between bg-gray-50 rounded px-3 py-2"
                  >
                    <span class="text-sm text-gray-900">{{ market.name }}</span>
                    <StatusBadge
                      :status="marketTypeLabels[market.marketType] || market.marketType"
                      :type="marketTypeColors[market.marketType] || 'default'"
                    />
                  </div>
                </div>
              </div>
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
  </div>
</template>

<style scoped>
.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}

.collapse-enter-from,
.collapse-leave-to {
  opacity: 0;
  max-height: 0;
}

.collapse-enter-to,
.collapse-leave-from {
  opacity: 1;
  max-height: 1000px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
