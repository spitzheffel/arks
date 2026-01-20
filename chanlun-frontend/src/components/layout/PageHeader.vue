<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div class="mb-6">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-800">{{ title }}</h1>
        <p v-if="description" class="mt-1 text-gray-600">{{ description }}</p>
      </div>
      <div class="flex items-center gap-3">
        <slot name="actions" />
      </div>
    </div>
    <slot name="extra" />
  </div>
</template>
