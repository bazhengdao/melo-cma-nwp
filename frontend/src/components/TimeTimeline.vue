<template>
  <div class="timeline"><div class="timeline-head"><div><span class="eyebrow">预报时效</span><strong>{{ forecast.timeLabel }}</strong></div><button class="play-button" @click="forecast.togglePlay">{{ forecast.playing ? 'Ⅱ' : '▶' }} <span>{{ forecast.playing ? '暂停' : '播放' }}</span></button><label class="opacity-control">图层透明度 <input v-model="forecast.opacity" type="range" min="30" max="100" /><span>{{ forecast.opacity }}%</span></label></div><div class="ticks"><button v-for="item in forecast.timeSlots" :key="item.index" class="tick" :class="{ active: forecast.hourIndex === item.index }" @click="forecast.selectHour(item.index)"><strong>{{ item.validTime }}</strong><span>{{ item.date }}</span></button></div></div>
</template>
<script setup>
defineProps({ forecast: { type: Object, required: true } })
</script>
