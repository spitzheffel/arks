<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getGapList, detectGaps, fillGap, batchFillGaps, resetFailedGap } from '@/api/gap'
import { getAllSymbols } from '@/api/symbol'
import { getSyncStatusBySymbol, updateAutoGapFill } from '@/api/sync'
import type { DataGapPage, DataGap, GapStatus, Symbol, SyncStatus, GapDetectResult, GapFillResult, BatchGapFillResult } from '@/types'
import { VALID_INTERVALS, GAP_STATUS_LABELS, GAP_STATUS_COLORS } from '@/types'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'

// 缺口列表状态
const loading = ref(false)
const gapPage = ref<DataGapPage | null>(null)
const currentPage = ref(1)
const pageSize = ref(20)
const filterSymbolId = ref<string>('')
const filterInterval = ref<string>('')
const filterStatus = ref<string>('')
const error = ref<string | null>(null)

// 交易对列表
const symbols = ref<Symbol[]>([])

// 选中的缺口
const selectedGapIds = ref<number[]>([])

// 检测缺口弹窗
const showDetectDialog = ref(false)
const detectSymbolId = ref<string>('')
const detectInterval = ref<string>('')
const detectAll = ref(false)
const detectLoading = ref(false)
const detectResult = ref<GapDetectResult | null>(null)
const showDetectResult = ref(false)

// 回补缺口弹窗
const showFillDialog = ref(false)
const fillGapId = ref<number | null>(null)
const fillLoading = ref(false)
const fillResult = ref<GapFillResult | null>(null)
const showFillResult = ref(false)

// 批量回补弹窗
const showBatchFillDialog = ref(false)
const batchFillLoading = ref(false)
const batchFillResult = ref<BatchGapFillResult | null>(null)
const showBatchFillResult = ref(false)

// 自动回补配置弹窗
const showAutoFillConfigDialog = ref(false)
const autoFillConfigSymbolId = ref<number | null>(null)
const autoFillConfigList = ref<SyncStatus[]>([])
const autoFillConfigLoading = ref(false)

// 计算属性
const gaps = computed(() => gapPage.value?.records || [])
const totalRecords = computed(() => gapPage.value?.total || 0)
const totalPages = computed(() => gapPage.value?.pages || 0)
const hasSelectedGaps = computed(() => selectedGapIds.value.length > 0)
const allSelected = computed(() => gaps.value.length > 0 && selectedGapIds.value.length === gaps.value.length)
const pendingGaps = computed(() => gaps.value.filter(g => g.status === 'PENDING' || g.status === 'FAILED'))

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

async function loadGaps() {
  loading.value = true
  error.value = null
  selectedGapIds.value = []
  try {
    const params: Record<string, unknown> = { page: currentPage.value, size: pageSize.value }
    if (filterSymbolId.value) params.symbolId = Number(filterSymbolId.value)
    if (filterInterval.value) params.interval = filterInterval.value
    if (filterStatus.value) params.status = filterStatus.value
    gapPage.value = await getGapList(params)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载缺口列表失败'
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadGaps()
}

function handleFilterChange() {
  currentPage.value = 1
  loadGaps()
}

function toggleSelectAll() {
  if (allSelected.value) {
    selectedGapIds.value = []
  } else {
    selectedGapIds.value = pendingGaps.value.map(g => g.id)
  }
}

function toggleSelectGap(gapId: number) {
  const index = selectedGapIds.value.indexOf(gapId)
  if (index === -1) {
    selectedGapIds.value.push(gapId)
  } else {
    selectedGapIds.value.splice(index, 1)
  }
}

function isGapSelected(gapId: number): boolean {
  return selectedGapIds.value.includes(gapId)
}

function canSelectGap(gap: DataGap): boolean {
  return gap.status === 'PENDING' || gap.status === 'FAILED'
}

function formatTime(isoString: string | null): string {
  if (!isoString) return '-'
  return new Date(isoString).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

// 检测缺口
function openDetectDialog() {
  detectSymbolId.value = ''
  detectInterval.value = ''
  detectAll.value = false
  showDetectDialog.value = true
}

function closeDetectDialog() {
  showDetectDialog.value = false
}

async function confirmDetect() {
  detectLoading.value = true
  try {
    const request: Record<string, unknown> = {}
    if (detectAll.value) {
      request.detectAll = true
    } else {
      if (!detectSymbolId.value || !detectInterval.value) {
        error.value = '请选择交易对和周期，或勾选批量检测'
        detectLoading.value = false
        return
      }
      request.symbolId = Number(detectSymbolId.value)
      request.interval = detectInterval.value
    }
    detectResult.value = await detectGaps(request)
    showDetectResult.value = true
    closeDetectDialog()
    loadGaps()
  } catch (e) {
    detectResult.value = {
      success: false,
      message: e instanceof Error ? e.message : '检测失败',
      totalDetected: 0,
      newGaps: 0,
      existingGaps: 0
    }
    showDetectResult.value = true
    closeDetectDialog()
  } finally {
    detectLoading.value = false
  }
}

function closeDetectResult() {
  showDetectResult.value = false
  detectResult.value = null
}

// 单个回补
function openFillDialog(gapId: number) {
  fillGapId.value = gapId
  showFillDialog.value = true
}

function closeFillDialog() {
  showFillDialog.value = false
  fillGapId.value = null
}

async function confirmFill() {
  if (!fillGapId.value) return
  fillLoading.value = true
  try {
    fillResult.value = await fillGap(fillGapId.value)
    showFillResult.value = true
    closeFillDialog()
    loadGaps()
  } catch (e) {
    fillResult.value = {
      success: false,
      message: e instanceof Error ? e.message : '回补失败',
      gapId: fillGapId.value,
      filledCount: 0,
      durationMs: 0
    }
    showFillResult.value = true
    closeFillDialog()
  } finally {
    fillLoading.value = false
  }
}

function closeFillResult() {
  showFillResult.value = false
  fillResult.value = null
}

// 批量回补
function openBatchFillDialog() {
  if (!hasSelectedGaps.value) return
  showBatchFillDialog.value = true
}

function closeBatchFillDialog() {
  showBatchFillDialog.value = false
}

async function confirmBatchFill() {
  if (!hasSelectedGaps.value) return
  batchFillLoading.value = true
  try {
    batchFillResult.value = await batchFillGaps(selectedGapIds.value)
    showBatchFillResult.value = true
    closeBatchFillDialog()
    selectedGapIds.value = []
    loadGaps()
  } catch (e) {
    batchFillResult.value = {
      success: false,
      message: e instanceof Error ? e.message : '批量回补失败',
      totalRequested: selectedGapIds.value.length,
      successCount: 0,
      failedCount: selectedGapIds.value.length,
      results: []
    }
    showBatchFillResult.value = true
    closeBatchFillDialog()
  } finally {
    batchFillLoading.value = false
  }
}

function closeBatchFillResult() {
  showBatchFillResult.value = false
  batchFillResult.value = null
}

// 重置失败缺口
async function handleResetGap(gapId: number) {
  try {
    await resetFailedGap(gapId)
    loadGaps()
  } catch (e) {
    console.error('重置缺口失败:', e)
  }
}

// 自动回补配置
function openAutoFillConfigDialog(symbolId: number) {
  autoFillConfigSymbolId.value = symbolId
  autoFillConfigLoading.value = true
  showAutoFillConfigDialog.value = true
  loadAutoFillConfig(symbolId)
}

async function loadAutoFillConfig(symbolId: number) {
  try {
    autoFillConfigList.value = await getSyncStatusBySymbol(symbolId)
  } catch (e) {
    console.error('加载自动回补配置失败:', e)
  } finally {
    autoFillConfigLoading.value = false
  }
}

function closeAutoFillConfigDialog() {
  showAutoFillConfigDialog.value = false
  autoFillConfigSymbolId.value = null
  autoFillConfigList.value = []
}

async function toggleAutoGapFill(status: SyncStatus) {
  try {
    const updated = await updateAutoGapFill(status.id, !status.autoGapFillEnabled)
    status.autoGapFillEnabled = updated.autoGapFillEnabled
  } catch (e) {
    console.error('更新自动回补开关失败:', e)
  }
}

onMounted(async () => {
  await loadSymbols()
  await loadGaps()
})</script>

<template>
  <div class="space-y-6">
    <!-- 页面标题和操作按钮 -->
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-800">缺口管理</h1>
      <div class="flex items-center gap-2">
        <button class="btn btn-primary" @click="openDetectDialog">检测缺口</button>
        <button
          class="btn btn-secondary"
          :disabled="!hasSelectedGaps"
          @click="openBatchFillDialog"
        >
          批量回补 ({{ selectedGapIds.length }})
        </button>
      </div>
    </div>

    <!-- 筛选条件 -->
    <div class="card">
      <div class="flex flex-wrap gap-4 items-center">
        <div class="flex items-center gap-2">
          <label class="text-sm text-gray-600">交易对:</label>
          <select v-model="filterSymbolId" class="input w-48" @change="handleFilterChange">
            <option value="">全部</option>
            <option v-for="s in symbols" :key="s.id" :value="s.id">{{ s.symbol }}</option>
          </select>
        </div>
        <div class="flex items-center gap-2">
          <label class="text-sm text-gray-600">周期:</label>
          <select v-model="filterInterval" class="input w-28" @change="handleFilterChange">
            <option value="">全部</option>
            <option v-for="iv in VALID_INTERVALS" :key="iv" :value="iv">{{ iv }}</option>
          </select>
        </div>
        <div class="flex items-center gap-2">
          <label class="text-sm text-gray-600">状态:</label>
          <select v-model="filterStatus" class="input w-28" @change="handleFilterChange">
            <option value="">全部</option>
            <option value="PENDING">待回补</option>
            <option value="FILLING">回补中</option>
            <option value="FILLED">已回补</option>
            <option value="FAILED">回补失败</option>
          </select>
        </div>
        <button class="btn btn-secondary" @click="loadGaps">刷新</button>
      </div>
    </div>

    <!-- 错误提示 -->
    <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md">
      {{ error }}
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="card">
      <LoadingSpinner text="加载中..." />
    </div>

    <!-- 空状态 -->
    <EmptyState
      v-else-if="gaps.length === 0"
      icon="🔍"
      title="暂无缺口记录"
      description="还没有检测到数据缺口，点击「检测缺口」按钮开始检测"
    />

    <!-- 缺口列表 -->
    <div v-else class="card overflow-hidden p-0">
      <table class="w-full">
        <thead class="bg-gray-50 border-b">
          <tr>
            <th class="px-4 py-3 text-left">
              <input
                type="checkbox"
                :checked="allSelected"
                :indeterminate="hasSelectedGaps && !allSelected"
                class="rounded border-gray-300"
                @change="toggleSelectAll"
              >
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">交易对</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">周期</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">缺口时间范围</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">缺失数量</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">状态</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">重试次数</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-200">
          <tr v-for="gap in gaps" :key="gap.id" class="hover:bg-gray-50">
            <td class="px-4 py-4">
              <input
                v-if="canSelectGap(gap)"
                type="checkbox"
                :checked="isGapSelected(gap.id)"
                class="rounded border-gray-300"
                @change="toggleSelectGap(gap.id)"
              >
            </td>
            <td class="px-4 py-4">
              <div class="text-sm font-medium text-gray-900">{{ gap.symbol }}</div>
              <div class="text-xs text-gray-500">{{ gap.dataSourceName }} / {{ gap.marketName }}</div>
            </td>
            <td class="px-4 py-4">
              <span class="px-2 py-1 text-xs font-medium bg-blue-100 text-blue-800 rounded">
                {{ gap.interval }}
              </span>
            </td>
            <td class="px-4 py-4 text-sm text-gray-500">
              <div>{{ formatTime(gap.gapStart) }}</div>
              <div class="text-gray-400">至</div>
              <div>{{ formatTime(gap.gapEnd) }}</div>
            </td>
            <td class="px-4 py-4 text-sm font-medium text-gray-900">
              {{ gap.missingCount }}
            </td>
            <td class="px-4 py-4">
              <StatusBadge
                :status="GAP_STATUS_LABELS[gap.status as GapStatus] || gap.status"
                :type="GAP_STATUS_COLORS[gap.status as GapStatus] || 'default'"
              />
              <div v-if="gap.errorMessage" class="mt-1 text-xs text-red-500 max-w-xs truncate" :title="gap.errorMessage">
                {{ gap.errorMessage }}
              </div>
            </td>
            <td class="px-4 py-4 text-sm text-gray-600">
              {{ gap.retryCount }}
            </td>
            <td class="px-4 py-4">
              <div class="flex items-center gap-2">
                <button
                  v-if="gap.status === 'PENDING' || gap.status === 'FAILED'"
                  class="text-blue-600 hover:text-blue-800 text-sm"
                  @click="openFillDialog(gap.id)"
                >
                  回补
                </button>
                <button
                  v-if="gap.status === 'FAILED'"
                  class="text-yellow-600 hover:text-yellow-800 text-sm"
                  @click="handleResetGap(gap.id)"
                >
                  重置
                </button>
                <button
                  class="text-gray-600 hover:text-gray-800 text-sm"
                  @click="openAutoFillConfigDialog(gap.symbolId)"
                >
                  自动回补
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div v-if="totalPages > 1" class="px-6 py-4 border-t bg-gray-50 flex items-center justify-between">
        <div class="text-sm text-gray-500">
          共 {{ totalRecords }} 条，第 {{ currentPage }} / {{ totalPages }} 页
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

    <!-- 检测缺口弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="showDetectDialog"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          @click.self="closeDetectDialog"
        >
          <div class="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
            <div class="px-6 py-4 border-b">
              <h3 class="text-lg font-semibold text-gray-900">检测数据缺口</h3>
              <p class="text-sm text-gray-500 mt-1">选择交易对和周期进行检测，或批量检测所有符合条件的交易对</p>
            </div>
            <div class="px-6 py-4 space-y-4">
              <div class="flex items-center gap-2">
                <input
                  id="detectAll"
                  v-model="detectAll"
                  type="checkbox"
                  class="rounded border-gray-300"
                >
                <label for="detectAll" class="text-sm text-gray-700">批量检测所有已开启历史同步的交易对</label>
              </div>
              <div v-if="!detectAll">
                <label class="block text-sm font-medium text-gray-700 mb-1">交易对</label>
                <select v-model="detectSymbolId" class="input w-full">
                  <option value="">请选择</option>
                  <option v-for="s in symbols.filter(x => x.historySyncEnabled)" :key="s.id" :value="s.id">
                    {{ s.symbol }}
                  </option>
                </select>
              </div>
              <div v-if="!detectAll">
                <label class="block text-sm font-medium text-gray-700 mb-1">周期</label>
                <select v-model="detectInterval" class="input w-full">
                  <option value="">请选择</option>
                  <option v-for="iv in VALID_INTERVALS" :key="iv" :value="iv">{{ iv }}</option>
                </select>
              </div>
            </div>
            <div class="px-6 py-4 bg-gray-50 border-t flex justify-end gap-3">
              <button class="btn btn-secondary" :disabled="detectLoading" @click="closeDetectDialog">
                取消
              </button>
              <button
                class="btn btn-primary"
                :disabled="detectLoading || (!detectAll && (!detectSymbolId || !detectInterval))"
                @click="confirmDetect"
              >
                {{ detectLoading ? '检测中...' : '开始检测' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 检测结果弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="showDetectResult && detectResult"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          @click.self="closeDetectResult"
        >
          <div class="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
            <div class="px-6 py-4 border-b flex items-center gap-3">
              <div
                :class="[
                  'w-10 h-10 rounded-full flex items-center justify-center',
                  detectResult.success ? 'bg-green-100' : 'bg-red-100'
                ]"
              >
                <span class="text-xl">{{ detectResult.success ? '✓' : '✗' }}</span>
              </div>
              <h3 class="text-lg font-semibold text-gray-900">
                {{ detectResult.success ? '检测完成' : '检测失败' }}
              </h3>
            </div>
            <div class="px-6 py-4 space-y-3">
              <div class="flex justify-between text-sm">
                <span class="text-gray-500">检测到缺口:</span>
                <span class="text-gray-900 font-medium">{{ detectResult.totalDetected }}</span>
              </div>
              <div class="flex justify-between text-sm">
                <span class="text-gray-500">新增缺口:</span>
                <span class="text-gray-900">{{ detectResult.newGaps }}</span>
              </div>
              <div class="flex justify-between text-sm">
                <span class="text-gray-500">已存在缺口:</span>
                <span class="text-gray-900">{{ detectResult.existingGaps }}</span>
              </div>
              <div v-if="detectResult.message" class="mt-3 p-3 bg-gray-50 rounded text-sm text-gray-700">
                {{ detectResult.message }}
              </div>
            </div>
            <div class="px-6 py-4 bg-gray-50 border-t flex justify-end">
              <button class="btn btn-primary" @click="closeDetectResult">确定</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 单个回补确认弹窗 -->
    <ConfirmDialog
      v-model:visible="showFillDialog"
      title="回补缺口"
      message="确定要回补此缺口吗？系统将从交易所拉取缺失的K线数据。"
      type="info"
      confirm-text="开始回补"
      :loading="fillLoading"
      @confirm="confirmFill"
      @cancel="closeFillDialog"
    />

    <!-- 单个回补结果弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="showFillResult && fillResult"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          @click.self="closeFillResult"
        >
          <div class="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
            <div class="px-6 py-4 border-b flex items-center gap-3">
              <div
                :class="[
                  'w-10 h-10 rounded-full flex items-center justify-center',
                  fillResult.success ? 'bg-green-100' : 'bg-red-100'
                ]"
              >
                <span class="text-xl">{{ fillResult.success ? '✓' : '✗' }}</span>
              </div>
              <h3 class="text-lg font-semibold text-gray-900">
                {{ fillResult.success ? '回补成功' : '回补失败' }}
              </h3>
            </div>
            <div class="px-6 py-4 space-y-3">
              <div class="flex justify-between text-sm">
                <span class="text-gray-500">回补数量:</span>
                <span class="text-gray-900 font-medium">{{ fillResult.filledCount }}</span>
              </div>
              <div v-if="fillResult.durationMs" class="flex justify-between text-sm">
                <span class="text-gray-500">耗时:</span>
                <span class="text-gray-900">{{ (fillResult.durationMs / 1000).toFixed(2) }}s</span>
              </div>
              <div v-if="fillResult.message" class="mt-3 p-3 rounded text-sm" :class="fillResult.success ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'">
                {{ fillResult.message }}
              </div>
            </div>
            <div class="px-6 py-4 bg-gray-50 border-t flex justify-end">
              <button class="btn btn-primary" @click="closeFillResult">确定</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 批量回补确认弹窗 -->
    <ConfirmDialog
      v-model:visible="showBatchFillDialog"
      title="批量回补缺口"
      :message="`确定要回补选中的 ${selectedGapIds.length} 个缺口吗？系统将依次从交易所拉取缺失的K线数据。`"
      type="info"
      confirm-text="开始回补"
      :loading="batchFillLoading"
      @confirm="confirmBatchFill"
      @cancel="closeBatchFillDialog"
    />

    <!-- 批量回补结果弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="showBatchFillResult && batchFillResult"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          @click.self="closeBatchFillResult"
        >
          <div class="bg-white rounded-lg shadow-xl max-w-lg w-full mx-4">
            <div class="px-6 py-4 border-b flex items-center gap-3">
              <div
                :class="[
                  'w-10 h-10 rounded-full flex items-center justify-center',
                  batchFillResult.success ? 'bg-green-100' : 'bg-yellow-100'
                ]"
              >
                <span class="text-xl">{{ batchFillResult.success ? '✓' : '⚠' }}</span>
              </div>
              <h3 class="text-lg font-semibold text-gray-900">批量回补完成</h3>
            </div>
            <div class="px-6 py-4 space-y-3">
              <div class="flex justify-between text-sm">
                <span class="text-gray-500">请求回补:</span>
                <span class="text-gray-900">{{ batchFillResult.totalRequested }}</span>
              </div>
              <div class="flex justify-between text-sm">
                <span class="text-gray-500">成功:</span>
                <span class="text-green-600 font-medium">{{ batchFillResult.successCount }}</span>
              </div>
              <div class="flex justify-between text-sm">
                <span class="text-gray-500">失败:</span>
                <span class="text-red-600 font-medium">{{ batchFillResult.failedCount }}</span>
              </div>
              <div v-if="batchFillResult.message" class="mt-3 p-3 bg-gray-50 rounded text-sm text-gray-700">
                {{ batchFillResult.message }}
              </div>
            </div>
            <div class="px-6 py-4 bg-gray-50 border-t flex justify-end">
              <button class="btn btn-primary" @click="closeBatchFillResult">确定</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 自动回补配置弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="showAutoFillConfigDialog"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          @click.self="closeAutoFillConfigDialog"
        >
          <div class="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4">
            <div class="px-6 py-4 border-b">
              <h3 class="text-lg font-semibold text-gray-900">自动回补配置</h3>
              <p class="text-sm text-gray-500 mt-1">
                交易对: {{ autoFillConfigSymbolId ? getSymbolName(autoFillConfigSymbolId) : '' }}
              </p>
            </div>
            <div class="px-6 py-4 max-h-96 overflow-y-auto">
              <div v-if="autoFillConfigLoading" class="py-8">
                <LoadingSpinner text="加载中..." />
              </div>
              <div v-else-if="autoFillConfigList.length === 0" class="py-8 text-center text-gray-500">
                暂无同步状态记录
              </div>
              <table v-else class="w-full">
                <thead class="bg-gray-50">
                  <tr>
                    <th class="px-3 py-2 text-left text-xs font-medium text-gray-500">周期</th>
                    <th class="px-3 py-2 text-left text-xs font-medium text-gray-500">最后同步</th>
                    <th class="px-3 py-2 text-right text-xs font-medium text-gray-500">K线数</th>
                    <th class="px-3 py-2 text-center text-xs font-medium text-gray-500">自动回补</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-200">
                  <tr v-for="st in autoFillConfigList" :key="st.id">
                    <td class="px-3 py-2">
                      <span class="px-2 py-1 text-xs font-medium bg-blue-100 text-blue-800 rounded">
                        {{ st.interval }}
                      </span>
                    </td>
                    <td class="px-3 py-2 text-sm text-gray-500">{{ formatTime(st.lastSyncTime) }}</td>
                    <td class="px-3 py-2 text-sm text-right text-gray-900">{{ st.totalKlines }}</td>
                    <td class="px-3 py-2 text-center">
                      <button
                        type="button"
                        class="relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors"
                        :class="st.autoGapFillEnabled ? 'bg-blue-600' : 'bg-gray-200'"
                        @click="toggleAutoGapFill(st)"
                      >
                        <span
                          class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow transition"
                          :class="st.autoGapFillEnabled ? 'translate-x-5' : 'translate-x-0'"
                        />
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div class="mt-4 p-3 bg-yellow-50 rounded text-sm text-yellow-700">
                <strong>提示:</strong> 自动回补需要同时开启全局自动回补开关（系统配置 sync.gap_fill.auto）才能生效。
                手动删除历史数据后，该周期的自动回补会自动关闭，需手动重新开启。
              </div>
            </div>
            <div class="px-6 py-4 bg-gray-50 border-t flex justify-end">
              <button class="btn btn-primary" @click="closeAutoFillConfigDialog">关闭</button>
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
