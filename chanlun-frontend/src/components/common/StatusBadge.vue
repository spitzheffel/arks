<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status: string
  type?: 'success' | 'warning' | 'danger' | 'info' | 'default'
  size?: 'sm' | 'md'
}>()

const statusConfig = computed(() => {
  const configs = {
    success: 'bg-green-100 text-green-800',
    warning: 'bg-yellow-100 text-yellow-800',
    danger: 'bg-red-100 text-red-800',
    info: 'bg-blue-100 text-blue-800',
    default: 'bg-gray-100 text-gray-800'
  }
  return configs[props.type || 'default']
})

const sizeConfig = computed(() => {
  const configs = {
    sm: 'px-1.5 py-0.5 text-[10px]',
    md: 'px-2.5 py-0.5 text-xs'
  }
  return configs[props.size || 'md']
})
</script>

<template>
  <span
    :class="[
      'inline-flex items-center rounded-full font-medium',
      statusConfig,
      sizeConfig
    ]"
  >
    {{ status }}
  </span>
</template>
