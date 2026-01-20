<script setup lang="ts">
defineProps<{
  icon?: string
  title: string
  description?: string
}>()
</script>

<template>
  <div class="flex flex-col items-center justify-center py-12 text-center">
    <span class="text-5xl mb-4">{{ icon || '📭' }}</span>
    <h3 class="text-lg font-medium text-gray-800 mb-2">{{ title }}</h3>
    <p v-if="description" class="text-gray-500 max-w-sm">{{ description }}</p>
    <div class="mt-4">
      <slot name="action" />
    </div>
  </div>
</template>
