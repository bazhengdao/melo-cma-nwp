<template>
  <div class="shell">
    <TopBar :forecast="forecast" />
    <main class="workspace" :class="{ 'process-workspace': forecast.isProcessRain }">
      <ProductSidebar :forecast="forecast" />
      <MapWorkspace :forecast="forecast" />
    </main>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useForecast } from './composables/useForecast'
import TopBar from './components/TopBar.vue'
import ProductSidebar from './components/ProductSidebar.vue'
import MapWorkspace from './components/MapWorkspace.vue'
const forecast = reactive(useForecast())
</script>

<style scoped>
.workspace.process-workspace { grid-template-columns: 330px minmax(0, 1fr); }
</style>
