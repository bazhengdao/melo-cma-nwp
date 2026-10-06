<template>
  <aside class="sidebar" :class="{ collapsed: forecast.collapsed, 'process-sidebar': forecast.isProcessRain }">
    <div class="sidebar-columns">
      <div id="product-list"><section v-for="group in forecast.menuGroups" :key="group.key" class="product-group"><h3>{{ group.name }}</h3><button v-for="item in group.items" :key="item.ele" class="product-item" :class="{ selected: forecast.selected.ele === item.ele }" @click="forecast.selectProduct(item, group)"><span>{{ item.name }}</span></button></section></div>
      <div class="time-column">
        <div class="interval-tabs"><button v-for="interval in intervals" :key="interval" :class="{ active: activeInterval === interval, all: interval === 'all' }" @click="activeInterval = interval">{{ interval === 'all' ? '全部' : `${interval}小时` }}</button></div>
        <div v-if="forecast.isProcessRain" class="process-time-headings"><span>起止时效</span></div>
        <div class="time-lists" :class="{ 'process-time-lists': forecast.isProcessRain }">
          <div ref="timeListEl" class="time-list" :aria-label="forecast.isProcessRain ? '起止时效列表' : '预报时效列表'">
            <button v-for="item in visibleSlots" :key="item.index" :data-time-index="item.index" :class="{ active: !forecast.isProcessRain && forecast.hourIndex === item.index, 'process-start': forecast.isProcessRain && forecast.processStartHour === item.validTime, 'process-end': forecast.isProcessRain && forecast.processEndHour === item.validTime }" :disabled="forecast.loadingTimes" @click="forecast.isProcessRain ? forecast.selectProcessTime(item.index) : forecast.selectHour(item.index)"><b>{{ item.validTime }}</b><span>{{ item.date }}</span><em v-if="forecast.isProcessRain && forecast.processStartHour === item.validTime">起</em><em v-else-if="forecast.isProcessRain && forecast.processEndHour === item.validTime">止</em></button>
            <div v-if="forecast.loadingTimes" class="time-state">加载中...</div><div v-else-if="forecast.timesError" class="time-state">{{ forecast.timesError }}</div>
          </div>
        </div>
        <div v-if="forecast.isProcessRain" class="process-confirm-row">
          <button class="process-confirm" type="button" :disabled="forecast.loadingTimes || !forecast.processSelectionValid" @click="forecast.confirmProcessRange">确定</button>
        </div>
        <div class="time-arrows"><button :title="forecast.isProcessRain ? '上一段过程' : '上一条时效'" :disabled="!canMove(-1)" @click="moveTime(-1)">‹</button><button :title="forecast.isProcessRain ? '下一段过程' : '下一条时效'" :disabled="!canMove(1)" @click="moveTime(1)">›</button></div>
      </div>
    </div>
  </aside>
</template>
<script setup>
import { computed, nextTick, ref, watch } from 'vue'
const props = defineProps({ forecast: { type: Object, required: true } })
const intervals = ['3', '6', '12', '24', 'all']
const activeInterval = ref('24')
const timeListEl = ref(null)
const visibleSlots = computed(() => activeInterval.value === 'all' ? props.forecast.timeSlots : props.forecast.timeSlots.filter((item) => Number(item.validTime) % Number(activeInterval.value) === 0))
const currentIndex = computed(() => {
  const exact = visibleSlots.value.findIndex((item) => item.index === props.forecast.hourIndex)
  if (exact >= 0) return exact
  const next = visibleSlots.value.findIndex((item) => item.index > props.forecast.hourIndex)
  return next >= 0 ? next : visibleSlots.value.length - 1
})
const canMove = (offset) => {
  if (props.forecast.isProcessRain) return props.forecast.canShiftProcessRange(offset)
  const next = visibleSlots.value[currentIndex.value + offset]
  return !props.forecast.loadingTimes && currentIndex.value >= 0 && Boolean(next)
}
const moveTime = (offset) => {
  if (props.forecast.isProcessRain) {
    props.forecast.shiftProcessRange(offset)
    return
  }
  const next = visibleSlots.value[currentIndex.value + offset]
  if (next) props.forecast.selectHour(next.index)
}
const ensureSelectedVisible = async () => {
  await nextTick()
  const item = timeListEl.value?.querySelector(`[data-time-index="${props.forecast.hourIndex}"]`)
  item?.scrollIntoView({ block: 'nearest' })
}
watch(() => props.forecast.hourIndex, ensureSelectedVisible)
watch(visibleSlots, ensureSelectedVisible)
watch(() => props.forecast.isProcessRain, (value) => { if (value) activeInterval.value = 'all' })
</script>

<style scoped>
.time-lists { height: calc(100% - 109px); min-height: 0; }
.time-lists .time-list { height: 100%; }
.process-sidebar { width: 330px; flex-shrink: 0; }
.process-sidebar.collapsed { width: 0; }
.process-sidebar .sidebar-columns { grid-template-columns: 201px 129px; }
.process-time-headings { display: grid; grid-template-columns: 1fr; height: 28px; align-items: center; text-align: center; color: #a6c3d3; font-size: 12px; }
.process-time-lists { height: calc(100% - 177px); }
.time-list button:disabled { opacity: .35; cursor: not-allowed; background: transparent; }
.time-list button.process-start { background: #315d75; }
.time-list button.process-end { background: #487b52; }
.time-list button.process-start.process-end { background: #5d5680; }
.time-list button em { margin-left: auto; min-width: 18px; color: #d9f5ff; font-size: 11px; font-style: normal; text-align: center; }
.process-confirm-row { height: 40px; padding: 7px 12px; box-sizing: border-box; }
.process-confirm { width: 100%; height: 26px; border: 1px solid #4f9fc4; border-radius: 2px; color: #e7f7ff; background: #1f6f91; font-size: 12px; cursor: pointer; }
.process-confirm:hover:not(:disabled) { background: #2b84a7; }
.process-confirm:disabled { color: #78909c; border-color: #3d464c; background: #263238; cursor: not-allowed; }
</style>
