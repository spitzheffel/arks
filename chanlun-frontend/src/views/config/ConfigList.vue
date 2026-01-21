<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getAllConfigs, updateConfig } from '@/api/config'
import type { SystemConfig, ConfigGroup } from '@/types'
import { LoadingSpinner, ConfirmDialog } from '@/components'

// 状态
const configs = ref<SystemConfig[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// 编辑状态
const editingKey = ref<string | null>(null)
const editValue = ref('')
const saving = ref(false)

// 确认弹窗
const showConfirmDialog = ref(false)
const pendingUpdate = ref<{ key: string; value: string } | null>(null)

// 配置分组定义
const groupDefinitions = [
  { name: '交易对同步', description: '交易对列表同步配置', prefix: 'sync.symbol' },
  { name: '实时同步', description: '实时数据同步配置', prefix: 'sync.realtime' },
  { name: '历史同步', description: '历史数据同步配置', prefix: 'sync.history' },
  { name: '缺口检测', description: '数据缺口检测配置', prefix: 'sync.gap_detect' },
  { name: '缺口回补', description: '数据缺口回补配置', prefix: 'sync.gap_fill' }
]

// 按分组整理配置
const configGroups = computed<ConfigGroup[]>(() => {
  const groups: ConfigGroup[] = []
  const usedKeys = new Set<string>()

  for (const def of groupDefinitions) {
    const groupConfigs = configs.value.filter(c => c.configKey.startsWith(def.prefix))
    if (groupConfigs.length > 0) {
      groups.push({
        name: def.name,
        description: def.description,
        prefix: def.prefix,
        configs: groupConfigs
      })
      groupConfigs.forEach(c => usedKeys.add(c.configKey))
    }
  }

  // 其他未分组的配置
  const otherConfigs = configs.value.filter(c => !usedKeys.has(c.configKey))
  if (otherConfigs.length > 0) {
    groups.push({
      name: '其他配置',
      description: '其他系统配置',
      prefix: '',
      configs: otherConfigs
    })
  }

  return groups
})

// 加载配置
async function loadConfigs() {
  loading.value = true
  error.value = null
  try {
    configs.value = await getAllConfigs()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载配置失败'
  } finally {
    loading.value = false
  }
}

// 开始编辑
function startEdit(config: SystemConfig) {
  editingKey.value = config.configKey
  editValue.value = config.configValue
}

// 取消编辑
function cancelEdit() {
  editingKey.value = null
  editValue.value = ''
}

// 保存前确认
function confirmSave(key: string, value: string) {
  pendingUpdate.value = { key, value }
  showConfirmDialog.value = true
}

// 确认保存
async function handleConfirmSave() {
  if (!pendingUpdate.value) return

  const { key, value } = pendingUpdate.value
  saving.value = true
  try {
    await updateConfig(key, { value })
    await loadConfigs()
    cancelEdit()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '保存配置失败'
  } finally {
    saving.value = false
    showConfirmDialog.value = false
    pendingUpdate.value = null
  }
}

// 取消确认
function handleCancelConfirm() {
  showConfirmDialog.value = false
  pendingUpdate.value = null
}

// 格式化配置键显示
function formatConfigKey(key: string): string {
  // 移除前缀，只显示最后部分
  const parts = key.split('.')
  const lastPart = parts[parts.length - 1] ?? key
  return lastPart
    .replace(/_/g, ' ')
    .replace(/\b\w/g, l => l.toUpperCase())
}

// 格式化时间
function formatTime(time: string): string {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

// 判断是否为布尔值配置
function isBooleanConfig(value: string): boolean {
  return value === 'true' || value === 'false'
}

// 判断是否为 Cron 表达式
function isCronConfig(key: string): boolean {
  return key.endsWith('.cron')
}

// 判断是否为数字配置
function isNumberConfig(value: string): boolean {
  return /^\d+$/.test(value)
}

onMounted(() => {
  loadConfigs()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 页面标题 -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-800">系统配置</h1>
        <p class="mt-1 text-sm text-gray-500">管理系统运行参数和定时任务配置</p>
      </div>
      <button
        :disabled="loading"
        class="inline-flex items-center px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50"
        @click="loadConfigs"
      >
        <svg class="w-4 h-4 mr-2" :class="{ 'animate-spin': loading }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
        </svg>
        刷新
      </button>
    </div>

    <!-- 错误提示 -->
    <div v-if="error" class="bg-red-50 border border-red-200 rounded-md p-4">
      <div class="flex">
        <svg class="h-5 w-5 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <div class="ml-3">
          <p class="text-sm text-red-700">{{ error }}</p>
        </div>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading && configs.length === 0" class="flex justify-center py-12">
      <LoadingSpinner size="lg" />
    </div>

    <!-- 配置分组列表 -->
    <div v-else class="space-y-6">
      <div
        v-for="group in configGroups"
        :key="group.prefix"
        class="bg-white shadow rounded-lg overflow-hidden"
      >
        <!-- 分组标题 -->
        <div class="px-6 py-4 bg-gray-50 border-b border-gray-200">
          <h2 class="text-lg font-medium text-gray-900">{{ group.name }}</h2>
          <p class="mt-1 text-sm text-gray-500">{{ group.description }}</p>
        </div>

        <!-- 配置列表 -->
        <div class="divide-y divide-gray-200">
          <div
            v-for="config in group.configs"
            :key="config.configKey"
            class="px-6 py-4"
          >
            <div class="flex items-start justify-between">
              <div class="flex-1 min-w-0">
                <div class="flex items-center space-x-2">
                  <span class="text-sm font-medium text-gray-900">
                    {{ formatConfigKey(config.configKey) }}
                  </span>
                  <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600">
                    {{ config.configKey }}
                  </span>
                </div>
                <p class="mt-1 text-sm text-gray-500">{{ config.description }}</p>
                <p class="mt-1 text-xs text-gray-400">
                  更新时间: {{ formatTime(config.updatedAt) }}
                </p>
              </div>

              <div class="ml-4 flex-shrink-0">
                <!-- 编辑模式 -->
                <div v-if="editingKey === config.configKey" class="flex items-center space-x-2">
                  <!-- 布尔值选择 -->
                  <select
                    v-if="isBooleanConfig(config.configValue)"
                    v-model="editValue"
                    class="block w-32 rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                  >
                    <option value="true">启用</option>
                    <option value="false">禁用</option>
                  </select>
                  <!-- 数字输入 -->
                  <input
                    v-else-if="isNumberConfig(config.configValue)"
                    v-model="editValue"
                    type="number"
                    min="0"
                    class="block w-32 rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                  >
                  <!-- 文本输入 -->
                  <input
                    v-else
                    v-model="editValue"
                    type="text"
                    :placeholder="isCronConfig(config.configKey) ? 'Cron 表达式' : '配置值'"
                    class="block w-48 rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                  >
                  <button
                    :disabled="saving || editValue === config.configValue"
                    class="inline-flex items-center px-3 py-1.5 border border-transparent text-xs font-medium rounded text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50"
                    @click="confirmSave(config.configKey, editValue)"
                  >
                    保存
                  </button>
                  <button
                    :disabled="saving"
                    class="inline-flex items-center px-3 py-1.5 border border-gray-300 text-xs font-medium rounded text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
                    @click="cancelEdit"
                  >
                    取消
                  </button>
                </div>

                <!-- 显示模式 -->
                <div v-else class="flex items-center space-x-3">
                  <!-- 布尔值显示 -->
                  <span
                    v-if="isBooleanConfig(config.configValue)"
                    :class="[
                      'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium',
                      config.configValue === 'true'
                        ? 'bg-green-100 text-green-800'
                        : 'bg-gray-100 text-gray-800'
                    ]"
                  >
                    {{ config.configValue === 'true' ? '启用' : '禁用' }}
                  </span>
                  <!-- Cron 表达式显示 -->
                  <code
                    v-else-if="isCronConfig(config.configKey)"
                    class="px-2 py-1 bg-gray-100 rounded text-sm font-mono text-gray-700"
                  >
                    {{ config.configValue }}
                  </code>
                  <!-- 普通值显示 -->
                  <span v-else class="text-sm text-gray-900">
                    {{ config.configValue }}
                  </span>

                  <button
                    class="inline-flex items-center px-3 py-1.5 border border-gray-300 text-xs font-medium rounded text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
                    @click="startEdit(config)"
                  >
                    <svg class="w-3.5 h-3.5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                    </svg>
                    编辑
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="configGroups.length === 0 && !loading" class="text-center py-12">
        <svg class="mx-auto h-12 w-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
        <h3 class="mt-2 text-sm font-medium text-gray-900">暂无配置</h3>
        <p class="mt-1 text-sm text-gray-500">系统配置为空</p>
      </div>
    </div>

    <!-- 确认弹窗 -->
    <ConfirmDialog
      :visible="showConfirmDialog"
      title="确认修改配置"
      :message="`确定要将配置 '${pendingUpdate?.key ?? ''}' 的值修改为 '${pendingUpdate?.value ?? ''}' 吗？此操作可能影响系统运行。`"
      confirm-text="确认修改"
      cancel-text="取消"
      type="warning"
      @confirm="handleConfirmSave"
      @cancel="handleCancelConfirm"
      @update:visible="showConfirmDialog = $event"
    />
  </div>
</template>
