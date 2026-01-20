<script setup lang="ts">
import { RouterView } from 'vue-router'
import { AppLayout } from './components'
</script>

<template>
  <AppLayout>
    <div class="p-6">
      <RouterView />
    </div>
  </AppLayout>
</template>
