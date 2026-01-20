<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import { computed } from 'vue'

interface NavItem {
  path: string
  name: string
  icon: string
}

const _props = defineProps<{
  title?: string
}>()

const route = useRoute()

const navItems: NavItem[] = [
  { path: '/', name: '首页', icon: '🏠' },
  { path: '/datasources', name: '数据源', icon: '🔌' },
  { path: '/markets', name: '市场', icon: '📊' },
  { path: '/symbols', name: '交易对', icon: '💱' },
  { path: '/sync', name: '数据同步', icon: '🔄' },
  { path: '/gaps', name: '缺口管理', icon: '🔍' },
  { path: '/config', name: '系统配置', icon: '⚙️' }
]

const currentPath = computed(() => route.path)

const isActive = (path: string): boolean => {
  if (path === '/') {
    return currentPath.value === '/'
  }
  return currentPath.value.startsWith(path)
}
</script>

<template>
  <aside class="w-64 bg-white shadow-md flex flex-col h-screen sticky top-0">
    <!-- Logo 区域 -->
    <div class="p-4 border-b flex-shrink-0">
      <h1 class="text-xl font-bold text-gray-800">{{ _props.title || '缠论交易分析平台' }}</h1>
    </div>
    
    <!-- 导航菜单 -->
    <nav class="flex-1 p-4 overflow-y-auto">
      <ul class="space-y-2">
        <li v-for="item in navItems" :key="item.path">
          <RouterLink
            :to="item.path"
            class="flex items-center gap-3 px-4 py-2 rounded-lg transition-colors"
            :class="isActive(item.path) 
              ? 'bg-blue-50 text-blue-600 font-medium' 
              : 'text-gray-600 hover:bg-gray-50'"
          >
            <span class="text-lg">{{ item.icon }}</span>
            <span>{{ item.name }}</span>
          </RouterLink>
        </li>
      </ul>
    </nav>
    
    <!-- 底部信息 -->
    <div class="p-4 border-t text-xs text-gray-400 flex-shrink-0">
      <p>v0.1.0 - Phase 1</p>
    </div>
  </aside>
</template>
