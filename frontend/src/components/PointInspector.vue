<template>
  <aside class="inspector" :class="{ hidden: forecast.inspectorHidden }"><div class="inspector-title"><span>点位信息</span><button @click="forecast.inspectorHidden = true">×</button></div><div class="point-card"><div class="point-icon">⌖</div><div><strong>北京 · 海淀</strong><span>116.38°E, 39.94°N</span></div></div><div class="metric"><span>{{ forecast.selected.name }}</span><strong>{{ forecast.pointValue }} <small>mm</small></strong><em>较上一时次 +4.2%</em></div><div class="detail-list"><div><span>预报时效</span><b>{{ forecast.timeLabel }}</b></div><div><span>起报时次</span><b>{{ forecast.initTimeLabel }}</b></div><div><span>数据图层</span><b>{{ (forecast.selected.layers || []).map((layer) => layer.key).join(' + ') }}</b></div></div><div class="chart"><div class="chart-title">未来 24 小时趋势</div><div class="bars"><i v-for="height in barHeights" :key="height" :style="{ height: `${height}%` }"></i></div><div class="chart-axis"><span>20:00</span><span>02:00</span><span>08:00</span><span>14:00</span><span>20:00</span></div></div></aside>
</template>
<script setup>
import { ref } from 'vue'
defineProps({ forecast: { type: Object, required: true } })
const barHeights = ref([28, 42, 34, 60, 75, 53, 35, 20])
</script>
