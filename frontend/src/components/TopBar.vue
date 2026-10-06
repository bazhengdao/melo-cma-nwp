<template>
  <header class="topbar">
    <div class="top-controls"><label class="date-control"><strong>起报时间:</strong><input type="date" :value="forecast.initDate" :disabled="forecast.loadingTimes" @change="forecast.selectInitDate($event.target.value)" /></label><label class="select-control"><select aria-label="起报时次" :value="forecast.initHour" :disabled="forecast.loadingTimes" @change="forecast.selectInitHour($event.target.value)"><option v-for="hour in forecast.initHours" :key="hour" :value="hour">{{ hour }}</option></select></label><div class="stepper"><button title="上一起报时次" :disabled="forecast.loadingTimes" @click="forecast.shiftInitTime(-1)">‹</button><button title="下一起报时次" :disabled="forecast.loadingTimes" @click="forecast.shiftInitTime(1)">›</button></div><button class="model-badge" type="button">CMA-GFS</button><button class="icon-button" :class="{ spin: forecast.refreshing }" title="刷新预报" @click="forecast.refresh">↻</button></div>
  </header>
</template>
<script setup>
defineProps({ forecast: { type: Object, required: true } })
</script>
