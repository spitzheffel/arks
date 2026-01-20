<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import {
  getSyncTaskList,
  getSyncStatusList,
  getSyncStatusBySymbol,
  triggerHistorySync,
  getKlines,
  deleteKlines,
  updateAutoGapFill
} from '@/api/sync'
import { getAllSymbols } from '@/api/symbol'
import type {
  SyncTaskPage,
  SyncTaskType,
  SyncTaskStatus,
  SyncStatus,
  SyncStatusPage,
  Symbol,
  Kline,
  HistorySyncResult
} from '@/types'
import { VALID_INTERVALS, TASK_TYPE_LABELS, TASK_STATUS_LABELS, TASK_STATUS_COLORS } from '@/types'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'

type TabType = 'tasks' | 'status' | 'klines'
const activeTab = ref<TabType>('tasks')

// 同步任务状态
const tasksLoading = ref(false)
const taskPage = ref<SyncTaskPage | null>(null)
const taskCurrentPage = ref(1)
const taskPageSize = ref(20)
const taskFilterSymbolId = ref<string>('')
const taskFilterType = ref<string>('')
const taskFilterStatus = ref<string>('')
const taskError = ref<string | null>(null)

// 同步状态
const statusLoading = ref(false)
const statusPage = ref<SyncStatusPage | null>(null)
const statusCurrentPage = ref(1)
const statusPageSize = ref(20)
const statusFilterSymbolId = ref<string>('')
const statusError = ref<string | null>(null)

// K线数据状态
const klinesLoading = ref(false)
const klines = ref<Kline[]>([])
const klineSymbolId = ref<string>('')
const klineInterval = ref<string>('1h')
const klineStartTime = ref<string>('')
const klineEndTime = ref<string>('')
const klineLimit = ref<number>(500)
const klinesError = ref<string | null>(null)

// 交易对列表
const symbols = ref<Symbol[]>([])

// 历史同步弹窗
const showHistorySyncDialog = ref(false)
const historySyncSymbolId = ref<string>('')
const historySyncInterval = ref<string>('1h')
const historySyncStartTime = ref<string>('')
const historySyncEndTime = ref<string>('')
const historySyncLoading = ref(false)
const historySyncResult = ref<HistorySyncResult | null>(null)
const showHistorySyncResult = ref(false)

// 删除K线弹窗
const showDeleteKlineDialog = ref(false)
const deleteKlineSymbolId = ref<string>('')
const deleteKlineInterval = ref<string>('1h')
const deleteKlineStartTime = ref<string>('')
const deleteKlineEndTime = ref<string>('')
const deleteKlineLoading = ref(false)

// 同步状态详情弹窗
const showStatusDetailDialog = ref(false)
const statusDetailSymbolId = ref<number | null>(null)
const statusDetailList = ref<SyncStatus[]>([])
const statusDetailLoading = ref(false)

// 计算属性
const tasks = computed(() => taskPage.value?.records || [])
const taskTotalRecords = computed(() => taskPage.value?.total || 0)
const taskTotalPages = computed(() => taskPage.value?.pages || 0)
const statuses = computed(() => statusPage.value?.records || [])
const statusTotalRecords = computed(() => statusPage.value?.total || 0)
const statusTotalPages = computed(() => statusPage.value?.pages || 0)

function getSymbolName(symbolId: number): string {
  const symbol = symbols.value.find(s => s.id === symbolId)
  return symbol?.symbol || `ID: ${symbolId}`
}

async function loadSymbols() {
  try {
    symbols.value = await getAllSymbols()
  } catch (e) {
    console.error('加载交易对失败:', e)
  }
}

async function loadTasks() {
  tasksLoading.value = true
  taskError.value = null
  try {
    const params: Record<string, unknown> = { page: taskCurrentPage.value, size: taskPageSize.value }
    if (taskFilterSymbolId.value) params.symbolId = Number(taskFilterSymbolId.value)
    if (taskFilterType.value) params.taskType = taskFilterType.value
    if (taskFilterStatus.value) params.status = taskFilterStatus.value
    taskPage.value = await getSyncTaskList(params)
  } catch (e) {
    taskError.value = e instanceof Error ? e.message : '加载同步任务失败'
  } finally {
    tasksLoading.value = false
  }
}

function handleTaskPageChange(page: number) {
  taskCurrentPage.value = page
  loadTasks()
}

function handleTaskFilterChange() {
  taskCurrentPage.value = 1
  loadTasks()
}

async function loadStatuses() {
  statusLoading.value = true
  statusError.value = null
  try {
    const params: Record<string, unknown> = { page: statusCurrentPage.value, size: statusPageSize.value }
    if (statusFilterSymbolId.value) params.symbolId = Number(statusFilterSymbolId.value)
    statusPage.value = await getSyncStatusList(params)
  } catch (e) {
    statusError.value = e instanceof Error ? e.message : '加载同步状态失败'
  } finally {
    statusLoading.value = false
  }
}

function handleStatusPageChange(page: number) {
  statusCurrentPage.value = page
  loadStatuses()
}

function handleStatusFilterChange() {
  statusCurrentPage.value = 1
  loadStatuses()
}

async function viewStatusDetail(symbolId: number) {
  statusDetailSymbolId.value = symbolId
  statusDetailLoading.value = true
  showStatusDetailDialog.value = true
  try {
    statusDetailList.value = await getSyncStatusBySymbol(symbolId)
  } catch (e) {
    console.error('加载同步状态详情失败:', e)
  } finally {
    statusDetailLoading.value = false
  }
}

function closeStatusDetailDialog() {
  showStatusDetailDialog.value = false
  statusDetailSymbolId.value = null
  statusDetailList.value = []
}

async function toggleAutoGapFill(status: SyncStatus) {
  try {
    const updated = await updateAutoGapFill(status.id, !status.autoGapFillEnabled)
    status.autoGapFillEnabled = updated.autoGapFillEnabled
  } catch (e) {
    console.error('更新自动回补开关失败:', e)
  }
}

async function loadKlines() {
  if (!klineSymbolId.value || !klineInterval.value) {
    klinesError.value = '请选择交易对和周期'
    return
  }
  klinesLoading.value = true
  klinesError.value = null
  try {
    const params: Record<string, unknown> = {
      symbolId: Number(klineSymbolId.value),
      interval: klineInterval.value,
      limit: klineLimit.value
    }
    if (klineStartTime.value) params.startTime = new Date(klineStartTime.value).toISOString()
    if (klineEndTime.value) params.endTime = new Date(klineEndTime.value).toISOString()
    klines.value = await getKlines(params)
  } catch (e) {
    klinesError.value = e instanceof Error ? e.message : '加载K线数据失败'
  } finally {
    klinesLoading.value = false
  }
}

function openHistorySyncDialog() {
  historySyncSymbolId.value = ''
  historySyncInterval.value = '1h'
  historySyncStartTime.value = ''
  historySyncEndTime.value = ''
  showHistorySyncDialog.value = true
}

function closeHistorySyncDialog() {
  showHistorySyncDialog.value = false
}

async function confirmHistorySync() {
  if (!historySyncSymbolId.value || !historySyncInterval.value || !historySyncStartTime.value || !historySyncEndTime.value) return
  historySyncLoading.value = true
  try {
    const result = await triggerHistorySync(Number(historySyncSymbolId.value), {
      interval: historySyncInterval.value,
      startTime: new Date(historySyncStartTime.value).toISOString(),
      endTime: new Date(historySyncEndTime.value).toISOString()
    })
    historySyncResult.value = result
    showHistorySyncResult.value = true
    closeHistorySyncDialog()
    if (activeTab.value === 'tasks') loadTasks()
  } catch (e) {
    historySyncResult.value = {
      success: false, taskId: null, symbolId: Number(historySyncSymbolId.value),
      interval: historySyncInterval.value, startTime: historySyncStartTime.value,
      endTime: historySyncEndTime.value, syncedCount: 0, durationMs: 0,
      errorMessage: e instanceof Error ? e.message : '同步失败'
    }
    showHistorySyncResult.value = true
    closeHistorySyncDialog()
  } finally {
    historySyncLoading.value = false
  }
}

function closeHistorySyncResult() {
  showHistorySyncResult.value = false
  historySyncResult.value = null
}

function openDeleteKlineDialog() {
  deleteKlineSymbolId.value = ''
  deleteKlineInterval.value = '1h'
  deleteKlineStartTime.value = ''
  deleteKlineEndTime.value = ''
  showDeleteKlineDialog.value = true
}

function closeDeleteKlineDialog() {
  showDeleteKlineDialog.value = false
}

async function confirmDeleteKline() {
  if (!deleteKlineSymbolId.value || !deleteKlineInterval.value || !deleteKlineStartTime.value || !deleteKlineEndTime.value) return
  deleteKlineLoading.value = true
  try {
    await deleteKlines(Number(deleteKlineSymbolId.value), {
      interval: deleteKlineInterval.value,
      startTime: new Date(deleteKlineStartTime.value).toISOString(),
      endTime: new Date(deleteKlineEndTime.value).toISOString()
    })
    closeDeleteKlineDialog()
    if (activeTab.value === 'klines' && klineSymbolId.value === deleteKlineSymbolId.value && klineInterval.value === deleteKlineInterval.value) {
      loadKlines()
    }
  } catch (e) {
    console.error('删除K线数据失败:', e)
  } finally {
    deleteKlineLoading.value = false
  }
}

function formatTime(isoString: string | null): string {
  if (!isoString) return '-'
  return new Date(isoString).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function formatNumber(num: number | string): string {
  const n = typeof num === 'string' ? parseFloat(num) : num
  if (isNaN(n)) return '-'
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 8 })
}

function switchTab(tab: TabType) {
  activeTab.value = tab
  if (tab === 'tasks' && !taskPage.value) loadTasks()
  else if (tab === 'status' && !statusPage.value) loadStatuses()
}

onMounted(async () => {
  await loadSymbols()
  await loadTasks()
})
</script>


<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-800">数据同步</h1>
      <div class="flex items-center gap-2">
        <button class="btn btn-primary" @click="openHistorySyncDialog">手动历史同步</button>
        <button class="btn btn-danger" @click="openDeleteKlineDialog">删除历史数据</button>
      </div>
    </div>

    <div class="border-b border-gray-200">
      <nav class="-mb-px flex space-x-8">
        <button :class="['py-2 px-1 border-b-2 font-medium text-sm', activeTab === 'tasks' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700']" @click="switchTab('tasks')">同步任务</button>
        <button :class="['py-2 px-1 border-b-2 font-medium text-sm', activeTab === 'status' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700']" @click="switchTab('status')">同步状态</button>
        <button :class="['py-2 px-1 border-b-2 font-medium text-sm', activeTab === 'klines' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700']" @click="switchTab('klines')">K线数据</button>
      </nav>
    </div>

    <div v-if="activeTab === 'tasks'" class="space-y-4">
      <div class="card">
        <div class="flex flex-wrap gap-4 items-center">
          <div class="flex items-center gap-2">
            <label class="text-sm text-gray-600">交易对:</label>
            <select v-model="taskFilterSymbolId" class="input w-48" @change="handleTaskFilterChange">
              <option value="">全部</option>
              <option v-for="s in symbols" :key="s.id" :value="s.id">{{ s.symbol }}</option>
            </select>
          </div>
          <div class="flex items-center gap-2">
            <label class="text-sm text-gray-600">类型:</label>
            <select v-model="taskFilterType" class="input w-32" @change="handleTaskFilterChange">
              <option value="">全部</option>
              <option value="REALTIME">实时同步</option>
              <option value="HISTORY">历史同步</option>
              <option value="GAP_FILL">缺口回补</option>
            </select>
          </div>
          <div class="flex items-center gap-2">
            <label class="text-sm text-gray-600">状态:</label>
            <select v-model="taskFilterStatus" class="input w-28" @change="handleTaskFilterChange">
              <option value="">全部</option>
              <option value="PENDING">等待中</option>
              <option value="RUNNING">执行中</option>
              <option value="SUCCESS">成功</option>
              <option value="FAILED">失败</option>
            </select>
          </div>
          <button class="btn btn-secondary" @click="loadTasks">刷新</button>
        </div>
      </div>
      <div v-if="taskError" class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md">{{ taskError }}</div>
      <div v-if="tasksLoading" class="card"><LoadingSpinner text="加载中..." /></div>
      <EmptyState v-else-if="tasks.length === 0" icon="📋" title="暂无同步任务" description="还没有同步任务记录" />
      <div v-else class="card overflow-hidden p-0">
        <table class="w-full">
          <thead class="bg-gray-50 border-b">
            <tr>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">ID</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">交易对</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">周期</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">类型</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">状态</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">时间范围</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">同步数量</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">创建时间</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200">
            <tr v-for="task in tasks" :key="task.id" class="hover:bg-gray-50">
              <td class="px-4 py-4 text-sm text-gray-900">{{ task.id }}</td>
              <td class="px-4 py-4 text-sm font-medium text-gray-900">{{ getSymbolName(task.symbolId) }}</td>
              <td class="px-4 py-4"><span class="px-2 py-1 text-xs font-medium bg-blue-100 text-blue-800 rounded">{{ task.interval }}</span></td>
              <td class="px-4 py-4 text-sm text-gray-600">{{ TASK_TYPE_LABELS[task.taskType as SyncTaskType] || task.taskType }}</td>
              <td class="px-4 py-4"><StatusBadge :status="TASK_STATUS_LABELS[task.status as SyncTaskStatus] || task.status" :type="TASK_STATUS_COLORS[task.status as SyncTaskStatus] || 'default'" /></td>
              <td class="px-4 py-4 text-sm text-gray-500">
                <template v-if="task.startTime && task.endTime">
                  <div>{{ formatTime(task.startTime) }}</div>
                  <div class="text-gray-400">至</div>
                  <div>{{ formatTime(task.endTime) }}</div>
                </template>
                <span v-else>-</span>
              </td>
              <td class="px-4 py-4 text-sm text-gray-900">{{ task.syncedCount || 0 }}</td>
              <td class="px-4 py-4 text-sm text-gray-500">{{ formatTime(task.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="taskTotalPages > 1" class="px-6 py-4 border-t bg-gray-50 flex items-center justify-between">
          <div class="text-sm text-gray-500">共 {{ taskTotalRecords }} 条，第 {{ taskCurrentPage }} / {{ taskTotalPages }} 页</div>
          <div class="flex items-center gap-2">
            <button class="btn btn-secondary text-sm" :disabled="taskCurrentPage <= 1" @click="handleTaskPageChange(taskCurrentPage - 1)">上一页</button>
            <button class="btn btn-secondary text-sm" :disabled="taskCurrentPage >= taskTotalPages" @click="handleTaskPageChange(taskCurrentPage + 1)">下一页</button>
          </div>
        </div>
      </div>
    </div>


    <div v-if="activeTab === 'status'" class="space-y-4">
      <div class="card">
        <div class="flex flex-wrap gap-4 items-center">
          <div class="flex items-center gap-2">
            <label class="text-sm text-gray-600">交易对:</label>
            <select v-model="statusFilterSymbolId" class="input w-48" @change="handleStatusFilterChange">
              <option value="">全部</option>
              <option v-for="s in symbols" :key="s.id" :value="s.id">{{ s.symbol }}</option>
            </select>
          </div>
          <button class="btn btn-secondary" @click="loadStatuses">刷新</button>
        </div>
      </div>
      <div v-if="statusError" class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md">{{ statusError }}</div>
      <div v-if="statusLoading" class="card"><LoadingSpinner text="加载中..." /></div>
      <EmptyState v-else-if="statuses.length === 0" icon="📊" title="暂无同步状态" description="还没有同步状态记录" />
      <div v-else class="card overflow-hidden p-0">
        <table class="w-full">
          <thead class="bg-gray-50 border-b">
            <tr>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">交易对</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">周期</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">最后同步</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">最后K线</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">K线数</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">自动回补</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200">
            <tr v-for="st in statuses" :key="st.id" class="hover:bg-gray-50">
              <td class="px-4 py-4 text-sm font-medium text-gray-900">{{ getSymbolName(st.symbolId) }}</td>
              <td class="px-4 py-4"><span class="px-2 py-1 text-xs font-medium bg-blue-100 text-blue-800 rounded">{{ st.interval }}</span></td>
              <td class="px-4 py-4 text-sm text-gray-500">{{ formatTime(st.lastSyncTime) }}</td>
              <td class="px-4 py-4 text-sm text-gray-500">{{ formatTime(st.lastKlineTime) }}</td>
              <td class="px-4 py-4 text-sm text-gray-900">{{ formatNumber(st.totalKlines) }}</td>
              <td class="px-4 py-4">
                <button type="button" class="relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors" :class="st.autoGapFillEnabled ? 'bg-blue-600' : 'bg-gray-200'" @click="toggleAutoGapFill(st)">
                  <span class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow transition" :class="st.autoGapFillEnabled ? 'translate-x-5' : 'translate-x-0'" />
                </button>
              </td>
              <td class="px-4 py-4"><button class="text-blue-600 hover:text-blue-800 text-sm" @click="viewStatusDetail(st.symbolId)">查看详情</button></td>
            </tr>
          </tbody>
        </table>
        <div v-if="statusTotalPages > 1" class="px-6 py-4 border-t bg-gray-50 flex items-center justify-between">
          <div class="text-sm text-gray-500">共 {{ statusTotalRecords }} 条，第 {{ statusCurrentPage }} / {{ statusTotalPages }} 页</div>
          <div class="flex items-center gap-2">
            <button class="btn btn-secondary text-sm" :disabled="statusCurrentPage <= 1" @click="handleStatusPageChange(statusCurrentPage - 1)">上一页</button>
            <button class="btn btn-secondary text-sm" :disabled="statusCurrentPage >= statusTotalPages" @click="handleStatusPageChange(statusCurrentPage + 1)">下一页</button>
          </div>
        </div>
      </div>
    </div>


    <div v-if="activeTab === 'klines'" class="space-y-4">
      <div class="card">
        <div class="flex flex-wrap gap-4 items-end">
          <div class="flex flex-col gap-1">
            <label class="text-sm text-gray-600">交易对</label>
            <select v-model="klineSymbolId" class="input w-48">
              <option value="">请选择</option>
              <option v-for="s in symbols" :key="s.id" :value="s.id">{{ s.symbol }}</option>
            </select>
          </div>
          <div class="flex flex-col gap-1">
            <label class="text-sm text-gray-600">周期</label>
            <select v-model="klineInterval" class="input w-28">
              <option v-for="iv in VALID_INTERVALS" :key="iv" :value="iv">{{ iv }}</option>
            </select>
          </div>
          <div class="flex flex-col gap-1">
            <label class="text-sm text-gray-600">开始时间</label>
            <input v-model="klineStartTime" type="datetime-local" class="input w-48">
          </div>
          <div class="flex flex-col gap-1">
            <label class="text-sm text-gray-600">结束时间</label>
            <input v-model="klineEndTime" type="datetime-local" class="input w-48">
          </div>
          <div class="flex flex-col gap-1">
            <label class="text-sm text-gray-600">数量</label>
            <input v-model.number="klineLimit" type="number" class="input w-24" min="1" max="1000">
          </div>
          <button class="btn btn-primary" :disabled="klinesLoading || !klineSymbolId" @click="loadKlines">查询</button>
        </div>
      </div>
      <div v-if="klinesError" class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md">{{ klinesError }}</div>
      <div v-if="klinesLoading" class="card"><LoadingSpinner text="加载中..." /></div>
      <EmptyState v-else-if="klines.length === 0 && klineSymbolId" icon="📈" title="暂无K线数据" description="该交易对在指定时间范围内没有K线数据" />
      <div v-else-if="klines.length > 0" class="card overflow-hidden p-0">
        <div class="px-4 py-3 bg-gray-50 border-b"><span class="text-sm text-gray-600">共 {{ klines.length }} 条数据</span></div>
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50 border-b">
              <tr>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">开盘时间</th>
                <th class="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">开盘价</th>
                <th class="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">最高价</th>
                <th class="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">最低价</th>
                <th class="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">收盘价</th>
                <th class="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">成交量</th>
                <th class="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">成交额</th>
                <th class="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">笔数</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr v-for="k in klines" :key="k.id" class="hover:bg-gray-50">
                <td class="px-4 py-3 text-sm text-gray-900">{{ formatTime(k.openTime) }}</td>
                <td class="px-4 py-3 text-sm text-right text-gray-900">{{ formatNumber(k.open) }}</td>
                <td class="px-4 py-3 text-sm text-right text-green-600">{{ formatNumber(k.high) }}</td>
                <td class="px-4 py-3 text-sm text-right text-red-600">{{ formatNumber(k.low) }}</td>
                <td class="px-4 py-3 text-sm text-right text-gray-900">{{ formatNumber(k.close) }}</td>
                <td class="px-4 py-3 text-sm text-right text-gray-600">{{ formatNumber(k.volume) }}</td>
                <td class="px-4 py-3 text-sm text-right text-gray-600">{{ formatNumber(k.quoteVolume) }}</td>
                <td class="px-4 py-3 text-sm text-right text-gray-600">{{ k.trades }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <EmptyState v-else icon="🔍" title="请选择查询条件" description="选择交易对和周期后点击查询按钮" />
    </div>


    <!-- 历史同步弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showHistorySyncDialog" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50" @click.self="closeHistorySyncDialog">
          <div class="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
            <div class="px-6 py-4 border-b">
              <h3 class="text-lg font-semibold text-gray-900">手动历史同步</h3>
              <p class="text-sm text-gray-500 mt-1">选择交易对、周期和时间范围</p>
            </div>
            <div class="px-6 py-4 space-y-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">交易对</label>
                <select v-model="historySyncSymbolId" class="input w-full">
                  <option value="">请选择</option>
                  <option v-for="s in symbols.filter(x => x.historySyncEnabled)" :key="s.id" :value="s.id">{{ s.symbol }}</option>
                </select>
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">周期</label>
                <select v-model="historySyncInterval" class="input w-full">
                  <option v-for="iv in VALID_INTERVALS" :key="iv" :value="iv">{{ iv }}</option>
                </select>
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">开始时间</label>
                <input v-model="historySyncStartTime" type="datetime-local" class="input w-full">
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">结束时间</label>
                <input v-model="historySyncEndTime" type="datetime-local" class="input w-full">
              </div>
            </div>
            <div class="px-6 py-4 bg-gray-50 border-t flex justify-end gap-3">
              <button class="btn btn-secondary" :disabled="historySyncLoading" @click="closeHistorySyncDialog">取消</button>
              <button class="btn btn-primary" :disabled="historySyncLoading || !historySyncSymbolId || !historySyncStartTime || !historySyncEndTime" @click="confirmHistorySync">
                {{ historySyncLoading ? '同步中...' : '开始同步' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 历史同步结果弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showHistorySyncResult && historySyncResult" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50" @click.self="closeHistorySyncResult">
          <div class="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
            <div class="px-6 py-4 border-b flex items-center gap-3">
              <div :class="['w-10 h-10 rounded-full flex items-center justify-center', historySyncResult.success ? 'bg-green-100' : 'bg-red-100']">
                <span class="text-xl">{{ historySyncResult.success ? '✓' : '✗' }}</span>
              </div>
              <h3 class="text-lg font-semibold text-gray-900">{{ historySyncResult.success ? '同步成功' : '同步失败' }}</h3>
            </div>
            <div class="px-6 py-4 space-y-3">
              <div class="flex justify-between text-sm"><span class="text-gray-500">交易对:</span><span class="text-gray-900">{{ getSymbolName(historySyncResult.symbolId) }}</span></div>
              <div class="flex justify-between text-sm"><span class="text-gray-500">周期:</span><span class="text-gray-900">{{ historySyncResult.interval }}</span></div>
              <div class="flex justify-between text-sm"><span class="text-gray-500">同步数量:</span><span class="text-gray-900 font-medium">{{ historySyncResult.syncedCount }}</span></div>
              <div v-if="historySyncResult.durationMs" class="flex justify-between text-sm"><span class="text-gray-500">耗时:</span><span class="text-gray-900">{{ (historySyncResult.durationMs / 1000).toFixed(2) }}s</span></div>
              <div v-if="historySyncResult.errorMessage" class="mt-3 p-3 bg-red-50 rounded text-sm text-red-700">{{ historySyncResult.errorMessage }}</div>
            </div>
            <div class="px-6 py-4 bg-gray-50 border-t flex justify-end">
              <button class="btn btn-primary" @click="closeHistorySyncResult">确定</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 删除K线确认弹窗 -->
    <ConfirmDialog v-model:visible="showDeleteKlineDialog" title="删除历史数据" message="此操作将永久删除指定范围内的K线数据，不可恢复。" type="danger" confirm-text="确认删除" :loading="deleteKlineLoading" @confirm="confirmDeleteKline" @cancel="closeDeleteKlineDialog">
      <div class="mt-4 space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">交易对</label>
          <select v-model="deleteKlineSymbolId" class="input w-full">
            <option value="">请选择</option>
            <option v-for="s in symbols" :key="s.id" :value="s.id">{{ s.symbol }}</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">周期</label>
          <select v-model="deleteKlineInterval" class="input w-full">
            <option v-for="iv in VALID_INTERVALS" :key="iv" :value="iv">{{ iv }}</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">开始时间</label>
          <input v-model="deleteKlineStartTime" type="datetime-local" class="input w-full">
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">结束时间</label>
          <input v-model="deleteKlineEndTime" type="datetime-local" class="input w-full">
        </div>
      </div>
    </ConfirmDialog>

    <!-- 同步状态详情弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showStatusDetailDialog" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50" @click.self="closeStatusDetailDialog">
          <div class="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4">
            <div class="px-6 py-4 border-b">
              <h3 class="text-lg font-semibold text-gray-900">同步状态详情</h3>
              <p class="text-sm text-gray-500 mt-1">交易对: {{ statusDetailSymbolId ? getSymbolName(statusDetailSymbolId) : '' }}</p>
            </div>
            <div class="px-6 py-4 max-h-96 overflow-y-auto">
              <div v-if="statusDetailLoading" class="py-8"><LoadingSpinner text="加载中..." /></div>
              <div v-else-if="statusDetailList.length === 0" class="py-8 text-center text-gray-500">暂无同步状态记录</div>
              <table v-else class="w-full">
                <thead class="bg-gray-50">
                  <tr>
                    <th class="px-3 py-2 text-left text-xs font-medium text-gray-500">周期</th>
                    <th class="px-3 py-2 text-left text-xs font-medium text-gray-500">最后同步</th>
                    <th class="px-3 py-2 text-left text-xs font-medium text-gray-500">最后K线</th>
                    <th class="px-3 py-2 text-right text-xs font-medium text-gray-500">K线数</th>
                    <th class="px-3 py-2 text-center text-xs font-medium text-gray-500">自动回补</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-200">
                  <tr v-for="st in statusDetailList" :key="st.id">
                    <td class="px-3 py-2"><span class="px-2 py-1 text-xs font-medium bg-blue-100 text-blue-800 rounded">{{ st.interval }}</span></td>
                    <td class="px-3 py-2 text-sm text-gray-500">{{ formatTime(st.lastSyncTime) }}</td>
                    <td class="px-3 py-2 text-sm text-gray-500">{{ formatTime(st.lastKlineTime) }}</td>
                    <td class="px-3 py-2 text-sm text-right text-gray-900">{{ formatNumber(st.totalKlines) }}</td>
                    <td class="px-3 py-2 text-center"><span :class="['px-2 py-1 text-xs rounded', st.autoGapFillEnabled ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600']">{{ st.autoGapFillEnabled ? '开启' : '关闭' }}</span></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="px-6 py-4 bg-gray-50 border-t flex justify-end">
              <button class="btn btn-primary" @click="closeStatusDetailDialog">关闭</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
