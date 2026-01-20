<script setup lang="ts">
import { PageHeader } from '@/components'
</script>

<template>
  <div>
    <PageHeader 
      title="欢迎使用缠论交易分析平台" 
      description="请从左侧菜单选择功能模块开始使用"
    />
    
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <!-- 快捷入口卡片 -->
      <RouterLink to="/datasources" class="card hover:shadow-lg transition-shadow">
        <div class="flex items-center gap-4">
          <span class="text-3xl">🔌</span>
          <div>
            <h3 class="font-semibold text-gray-800">数据源管理</h3>
            <p class="text-sm text-gray-500">配置交易所 API 连接</p>
          </div>
        </div>
      </RouterLink>
      
      <RouterLink to="/markets" class="card hover:shadow-lg transition-shadow">
        <div class="flex items-center gap-4">
          <span class="text-3xl">📊</span>
          <div>
            <h3 class="font-semibold text-gray-800">市场管理</h3>
            <p class="text-sm text-gray-500">管理现货和合约市场</p>
          </div>
        </div>
      </RouterLink>
      
      <RouterLink to="/symbols" class="card hover:shadow-lg transition-shadow">
        <div class="flex items-center gap-4">
          <span class="text-3xl">💱</span>
          <div>
            <h3 class="font-semibold text-gray-800">交易对管理</h3>
            <p class="text-sm text-gray-500">配置交易对同步</p>
          </div>
        </div>
      </RouterLink>
      
      <RouterLink to="/sync" class="card hover:shadow-lg transition-shadow">
        <div class="flex items-center gap-4">
          <span class="text-3xl">🔄</span>
          <div>
            <h3 class="font-semibold text-gray-800">数据同步</h3>
            <p class="text-sm text-gray-500">查看同步任务状态</p>
          </div>
        </div>
      </RouterLink>
      
      <RouterLink to="/gaps" class="card hover:shadow-lg transition-shadow">
        <div class="flex items-center gap-4">
          <span class="text-3xl">🔍</span>
          <div>
            <h3 class="font-semibold text-gray-800">缺口管理</h3>
            <p class="text-sm text-gray-500">检测和回补数据缺口</p>
          </div>
        </div>
      </RouterLink>
      
      <RouterLink to="/config" class="card hover:shadow-lg transition-shadow">
        <div class="flex items-center gap-4">
          <span class="text-3xl">⚙️</span>
          <div>
            <h3 class="font-semibold text-gray-800">系统配置</h3>
            <p class="text-sm text-gray-500">管理系统参数</p>
          </div>
        </div>
      </RouterLink>
    </div>
  </div>
</template>
