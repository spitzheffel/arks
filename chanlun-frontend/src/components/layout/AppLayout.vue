<script setup lang="ts">
import AppSidebar from './AppSidebar.vue'

defineProps<{
  title?: string
}>()
</script>

<template>
  <div class="min-h-screen bg-gray-100 flex">
    <!-- 侧边栏 -->
    <AppSidebar :title="title" />
    
    <!-- 主内容区 -->
    <main class="flex-1 min-w-0 overflow-auto">
      <slot />
    </main>
  </div>
</template>
