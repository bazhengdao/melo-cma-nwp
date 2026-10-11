<template>
  <section class="map-panel">
    <div class="map-toolbar">
      <div class="region-control"><span class="region-label">地图区域:</span><div class="regions"><button v-for="item in regions" :key="item" type="button" class="region" :class="{ active: forecast.region === item }" @click="forecast.region = item">{{ item }}</button></div></div>
      <div class="map-actions"><button class="download-button" type="button" title="下载图片" :disabled="downloading" @click="downloadImage">下载图片</button></div>
    </div>
    <div class="map-stage">
      <div ref="mapEl" class="leaflet-map" aria-label="气象图层地图"></div>
      <div class="map-zoom"><button type="button" title="放大" @click="forecast.zoomIn">+</button><button type="button" title="缩小" @click="forecast.zoomOut">−</button></div><div class="coordinates">{{ coordinates }}</div>
      <div v-if="mapStatus" class="map-status" role="status">{{ mapStatus }}</div>
      <div class="map-caption"><strong>{{ forecast.selected.name }}</strong><span>{{ forecastDate }}</span></div>
      <div v-if="activeLayer" class="legend" :class="{ compact: paletteLabels.length > 9 }"><div class="legend-title">{{ activeLayer?.unit || paletteUnits[activeLayer?.color?.ident] || '' }}</div><div class="legend-body"><div class="legend-bar" :style="{ background: legendGradient }"></div><div class="legend-labels"><span v-for="tick in paletteLabels" :key="tick.value" :style="{ top: `${tick.position}%` }">{{ tick.value }}</span></div></div></div>
    </div>
  </section>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import html2canvas from 'html2canvas'
import { feature, mesh } from 'topojson-client'
import { L, BinLayer, WindLayer, ContourLayer, Meta3 } from '../lib/mapEngine'
import { palettes, paletteUnits } from '../config/runtimeConfig'
import { regions } from '../composables/useForecast'

const props = defineProps({ forecast: { type: Object, required: true } })
const coordinates = ref('经度: 116.38°E  纬度: 39.94°N')
const mapEl = ref(null)
let map
let baseLayer
let regionBoundaryLayer
let countryHighlightLayer
let regionTopology
let dataLayers = []
let resizeObserver
let loadId = 0
const mapStatus = ref('')
const downloading = ref(false)
const regionBounds = {
  全国: [[18, 73], [55, 135]], 东北: [[38, 118], [54, 135]], 华北: [[34, 105], [43, 121]],
  西北: [[31, 73], [49, 112]], 西南: [[21, 78], [35, 110]], 华中: [[24, 108], [35, 117]],
  华东: [[23, 113], [38, 124]], 华南: [[18, 104], [27, 119]],
}
const regionKeys = {
  全国: 'all', 东北: 'dongbei', 华北: 'huabei', 西北: 'xibei',
  西南: 'xinan', 华中: 'huazhong', 华东: 'huadong', 华南: 'huanan',
}
const activeLayer = computed(() => props.forecast.selected.layers?.find((layer) => layer.legend === true) || null)
const palette = computed(() => {
  const color = activeLayer.value?.color
  return (typeof color === 'string' ? palettes[color] : color) || { gradient: [] }
})
const isSentinelTick = (value) => Math.abs(Math.abs(value % 1) - 0.01) < 0.000001
const isHiddenLegendStop = (value) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return false
  const fractional = numeric - Math.floor(numeric)
  return Math.abs(fractional - 0.99) < 0.02
}
const paletteStops = computed(() => {
  const entries = palette.value.gradient
  const sentinelIndexes = entries.reduce((indexes, [value], index) => {
    if (isSentinelTick(value)) indexes.push(index)
    return indexes
  }, [])
  const hidden = sentinelIndexes.length < 2
    ? new Set()
    : new Set([sentinelIndexes[0], sentinelIndexes[sentinelIndexes.length - 1]])
  return entries.filter(([value], index) => !hidden.has(index) && !isHiddenLegendStop(value))
})
const paletteLabels = computed(() => {
  const entries = [...paletteStops.value].reverse()
  return entries.flatMap(([value], index) => (
    isHiddenLegendStop(value)
      ? []
      : [{ value, position: ((index + 0.5) / entries.length) * 100 }]
  ))
})
const legendGradient = computed(() => {
  const gradient = [...paletteStops.value].reverse()
  const size = gradient.length || 1
  const steps = []
  for (const [index, [, [r, g, b, alpha]]] of gradient.entries()) {
    const color = `rgba(${r}, ${g}, ${b}, ${alpha / 255})`
    const start = `${((index / size) * 100).toFixed(3)}%`
    const end = `${(((index + 1) / size) * 100).toFixed(3)}%`
    steps.push(`${color} ${start}`, `${color} ${end}`)
  }
  return `linear-gradient(to bottom, ${steps.join(', ')})`
})
const forecastDate = computed(() => props.forecast.isProcessRain
  ? props.forecast.processRangeLabel || '请选择起止时效'
  : props.forecast.timeSlots[props.forecast.hourIndex]?.date || '--')

const safeFilename = (value) => String(value || '').replace(/[\\/:*?"<>|\s]+/g, '-').replace(/^-+|-+$/g, '')
const downloadImage = async () => {
  if (!mapEl.value || downloading.value) return
  downloading.value = true
  const previousStatus = mapStatus.value
  mapStatus.value = ''
  try {
    const stage = mapEl.value.closest('.map-stage')
    const bounds = stage.getBoundingClientRect()
    const canvas = await html2canvas(stage, {
      backgroundColor: '#dce4e5',
      scale: Math.min(window.devicePixelRatio || 1, 2),
      useCORS: true,
      logging: false,
      onclone: (document) => {
        const clonedStage = document.querySelector('.map-stage')
        if (clonedStage) {
          clonedStage.style.width = `${Math.round(bounds.width)}px`
          clonedStage.style.height = `${Math.round(bounds.height)}px`
        }
        document.querySelectorAll('.map-zoom, .coordinates, .map-status').forEach((element) => {
          element.style.display = 'none'
        })
        // Raster capture must apply Leaflet's SVG positioning only once.
        // Keep the positioning on the image, outside the serialized SVG.
        clonedStage?.querySelectorAll('svg').forEach((svg) => {
          const source = svg.cloneNode(true)
          source.removeAttribute('style')
          source.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
          const image = document.createElement('img')
          image.className = svg.getAttribute('class') || ''
          image.style.cssText = svg.style.cssText
          image.width = Number(svg.getAttribute('width'))
          image.height = Number(svg.getAttribute('height'))
          image.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(new XMLSerializer().serializeToString(source))}`
          svg.replaceWith(image)
        })
      },
    })
    const link = document.createElement('a')
    const filename = [props.forecast.selected.name, forecastDate.value].map(safeFilename).filter(Boolean).join('_')
    link.download = `${filename || 'cma-nwp'}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
  } catch (error) {
    console.error('Failed to export map image', error)
    mapStatus.value = '图片生成失败，请稍后重试'
  } finally {
    downloading.value = false
    if (!mapStatus.value) mapStatus.value = previousStatus
  }
}

const isContourLayer = (config) => config.style === 'height_contour'
const isWindLayer = (config) => config.type === 'wind'
const formatLongitude = (longitude) => {
  const normalized = ((longitude + 540) % 360) - 180
  const direction = normalized < 0 ? 'W' : 'E'
  return `${Math.abs(normalized).toFixed(2)}°${direction}`
}

const shiftLongitudes = (geoJson, offset) => {
  if (!offset) return geoJson
  const copy = JSON.parse(JSON.stringify(geoJson))
  const shiftCoordinates = (coordinates) => {
    if (typeof coordinates[0] === 'number') {
      coordinates[0] += offset
      return coordinates
    }
    return coordinates.map(shiftCoordinates)
  }
  const visit = (node) => {
    if (!node) return
    if (node.type === 'FeatureCollection') node.features.forEach(visit)
    else if (node.type === 'Feature') visit(node.geometry)
    else if (node.type === 'GeometryCollection') node.geometries.forEach(visit)
    else if (node.coordinates) node.coordinates = shiftCoordinates(node.coordinates)
  }
  visit(copy)
  return copy
}

const repeatLongitudes = (geoJson) => {
  if (!geoJson || geoJson.type !== 'FeatureCollection') return geoJson
  return {
    ...geoJson,
    features: [-360, 0, 360].flatMap((offset) => geoJson.features.map((item) => shiftLongitudes(item, offset))),
  }
}

const updateRegionBoundary = (fitView = false) => {
  if (!map) return
  const showCountryHighlight = props.forecast.region === '全国'
  if (countryHighlightLayer) {
    if (showCountryHighlight && !map.hasLayer(countryHighlightLayer)) countryHighlightLayer.addTo(map)
    if (!showCountryHighlight && map.hasLayer(countryHighlightLayer)) countryHighlightLayer.remove()
  }
  regionBoundaryLayer.clearLayers()
  const region = props.forecast.region
  const object = regionTopology?.objects[regionKeys[region]]
  if (!object) {
    if (fitView) map.fitBounds(regionBounds[region], { animate: false })
    return
  }
  const geometry = feature(regionTopology, object)
  let bounds
  for (const offset of [-360, 0, 360]) {
    const layer = L.geoJSON(shiftLongitudes(geometry, offset), {
      interactive: false,
      pane: 'regionBoundaryPane',
      style: { color: '#f05a28', weight: 2, fill: false },
    }).addTo(regionBoundaryLayer)
    // Fit the original region, not the bounds of all world copies.
    if (offset === 0) bounds = layer.getBounds()
  }
  if (fitView) map.fitBounds(bounds, { padding: [12, 12], animate: false })
}

const addMarkerStyles = (option, config) => {
  if (!config.marker) return
  option.marker = { ...config.marker }
  if (config.marker.visible === 'visible_rain') {
    option.marker.visible = (feature) => feature.properties.val > 0
  }
  if (config.marker.style === 'wind_style') {
    option.marker.style = (feature) => Math.hypot(feature.properties.u, feature.properties.v) > 15
      ? { color: '#FF0000', weight: 2 }
      : { color: '#000' }
  }
}

const createContourOption = (option, path) => {
  const contourPath = path.split('/')
  option.url = `/api/cma-nwp/contour/${['cma-gfs', ...contourPath].map(encodeURIComponent).join('/')}`
  option.style = (feature) => ({
    weight: 2,
    color: [90, 144, 296, 316, 588].includes(feature.properties.level) ? 'red' : '#0202FF',
  })
  return option
}

const createLayerOption = (layer) => {
  const validTime = layer.processRain ? props.forecast.confirmedProcessEndHour : props.forecast.currentValidTime?.validTime
  const path = layer.dataPath && props.forecast.selectedInitTime && validTime
    ? `${layer.dataPath}/${props.forecast.selectedInitTime}.${validTime}`
    : ''
  const option = { ...layer, color: layer.color, opacity: Number(props.forecast.opacity ?? 82) / 100 }
  if (!path) return null
  if (isContourLayer(layer)) return createContourOption(option, path)
  addMarkerStyles(option, layer)
  if (layer.processRain) {
    const start = props.forecast.confirmedProcessStartHour
    const end = props.forecast.confirmedProcessEndHour
    if (!start || !end || Number(start) >= Number(end)) return null
    const dataPath2 = `${layer.dataPath}/${props.forecast.selectedInitTime}.${start}`
    const query = new URLSearchParams({ dataPath: path, dataPath2 }).toString()
    option.meta = new Meta3(`/api/cma-nwp/meteo/minusTile?${new URLSearchParams({ dataPath: `cma-gfs/${path}`, dataPath2: `cma-gfs/${dataPath2}` })}&z={z}&x={x}&y={y}`, `/api/cma-nwp/meteo/minusMeta?${new URLSearchParams({ dataPath: `cma-gfs/${path}`, dataPath2: `cma-gfs/${dataPath2}` })}`)
    return option
  }
  // The engine splits the UV pair on a path segment, then requests each tile.
  const metaPath = path.replace(/^([^/*]+)\*[^/]+/, '$1')
  option.meta = new Meta3(`/api/cma-nwp/tile/cma-gfs/${path}/{z}/{x}/{y}.bin`, `/api/cma-nwp/meta?dataPath=${encodeURIComponent(`cma-gfs/${metaPath}`)}`)
  return option
}

const fetchLayerData = async (option) => {
  const response = await fetch(option.url || option.meta.metaUrl, { cache: 'no-store' })
  if (!response.ok) throw new Error('当前时效暂无数据')
  const data = await response.json()
  if (!option.url && !data.bounds) throw new Error('数据范围无效')
  return data
}

const createMapLayer = (config, option) => {
  if (isContourLayer(config)) return new ContourLayer(option)
  return new BinLayer(option)
}

const finishMapLayer = (layer, config, contourData) => {
  layer.addTo(map)
  if (isWindLayer(config) && !(layer.glLayer instanceof WindLayer)) {
    throw new Error('风场图层构造失败')
  }
  if (layer.glLayer?.setColor && config.color) layer.glLayer.setColor(config.color, true)
  if (isContourLayer(config)) layer.setData(repeatLongitudes(contourData))
}

const clearDataLayers = () => {
  for (const layer of dataLayers) {
    try { map?.removeLayer(layer) } catch { /* bundle layers may not be attached yet */ }
  }
  dataLayers = []
}

const addConfiguredLayers = async () => {
  if (!map) return
  const currentLoad = ++loadId
  if (props.forecast.isProcessRain) {
    clearDataLayers()
    if (props.forecast.loadingTimes) { mapStatus.value = '正在加载时效...'; return }
    if (!props.forecast.processRangeLabel) { mapStatus.value = '请选择起止时效并点击确定'; return }
  }
  mapStatus.value = '正在加载数据...'
  const errors = []
  const pendingLayers = []
  for (const config of props.forecast.selected.layers || []) {
    const option = createLayerOption(config)
    if (!option) { errors.push('当前产品暂无数据'); continue }
    try {
      const data = await fetchLayerData(option)
      if (currentLoad !== loadId) return
      if (option.url) option.url = null
      const layer = createMapLayer(config, option)
      if (isWindLayer(config) && !(layer.glLayer instanceof WindLayer)) {
        throw new Error('风场图层构造失败')
      }
      pendingLayers.push({ layer, config, contourData: data })
    } catch (error) {
      if (currentLoad !== loadId) return
      errors.push(`${config.label || '图层'}：${error.message}`)
      console.warn('气象图层加载失败', config.dataPath, error)
    }
  }
  if (currentLoad !== loadId) return
  if (errors.length) {
    mapStatus.value = errors.join('；')
    return
  }

  // Mount replacements directly on the map because the rendering engine needs
  // the map's renderer context. Remove the old set only after all new layers mount.
  const previousLayers = dataLayers
  const mountedLayers = []
  try {
    for (const pending of pendingLayers) {
      finishMapLayer(pending.layer, pending.config, pending.contourData)
      mountedLayers.push(pending.layer)
    }
    for (const layer of previousLayers) {
      try { map.removeLayer(layer) } catch { /* old layer may already be detached */ }
    }
    dataLayers = mountedLayers
    mapStatus.value = ''
  } catch (error) {
    for (const layer of mountedLayers) {
      try { map.removeLayer(layer) } catch { /* layer may already be detached */ }
    }
    mapStatus.value = `图层替换失败：${error.message}`
  }
}

onMounted(async () => {
  if (!mapEl.value) return
  map = L.map(mapEl.value, { center: [35, 105], zoom: 3, zoomControl: false, attributionControl: false, minZoom: 1, maxZoom: 9, zoomSnap: 0.25, worldCopyJump: true, continuousWorld: true })
  const mapBounds = [[18, 73], [55, 135]]
  baseLayer = L.layerGroup().addTo(map)
  regionBoundaryLayer = L.layerGroup().addTo(map)
  countryHighlightLayer = L.layerGroup().addTo(map)
  const boundaryPane = map.createPane('countryBoundaryPane')
  boundaryPane.style.zIndex = 720
  boundaryPane.style.pointerEvents = 'none'
  const provinceBoundaryPane = map.createPane('provinceBoundaryPane')
  provinceBoundaryPane.style.zIndex = 710
  provinceBoundaryPane.style.pointerEvents = 'none'
  const regionBoundaryPane = map.createPane('regionBoundaryPane')
  regionBoundaryPane.style.zIndex = 715
  regionBoundaryPane.style.pointerEvents = 'none'
  const worldBoundaryPane = map.createPane('worldBoundaryPane')
  worldBoundaryPane.style.zIndex = 610
  worldBoundaryPane.style.pointerEvents = 'none'
  map.fitBounds(mapBounds, { padding: [0, 0], animate: false })
  map.on('mousemove', ({ latlng }) => { coordinates.value = `经度: ${formatLongitude(latlng.lng)}  纬度: ${latlng.lat.toFixed(2)}°N` })
  resizeObserver = new ResizeObserver(() => map?.invalidateSize({ animate: false }))
  resizeObserver.observe(mapEl.value)
  addConfiguredLayers()
  await Promise.all(['land', 'provinces', 'regions'].map(async (name) => {
    const response = await fetch(`${import.meta.env.BASE_URL}maps/${name}.json`)
    if (!response.ok) throw new Error(`${name}: ${response.status}`)
    const topology = await response.json()
    if (!map) return
    if (name === 'regions') {
      regionTopology = topology
      updateRegionBoundary()
      return
    }
    for (const object of Object.values(topology.objects)) {
      const isBoundary = name === 'provinces'
      const geometry = feature(topology, object)
      const countryBoundary = isBoundary ? mesh(topology, object, (a, b) => a === b) : null
      for (const offset of [-360, 0, 360]) {
        const copy = shiftLongitudes(geometry, offset)
        L.geoJSON(copy, {
          interactive: false,
          pane: isBoundary ? 'provinceBoundaryPane' : 'tilePane',
          style: {
            color: isBoundary ? '#5f6d69' : '#78857c',
            weight: 1,
            fill: !isBoundary,
            fillColor: '#eff0eb',
            fillOpacity: isBoundary ? 0 : 1,
          },
        }).addTo(baseLayer)
        if (isBoundary) {
          L.geoJSON(shiftLongitudes(countryBoundary, offset), {
            interactive: false,
            pane: 'countryBoundaryPane',
            style: { color: '#f05a28', weight: 2, fill: false, fillOpacity: 0 },
          }).addTo(countryHighlightLayer)
        }
        if (name === 'land') {
          L.geoJSON(copy, {
            interactive: false,
            pane: 'worldBoundaryPane',
            style: { color: '#8d9890', weight: 1, fill: false, fillOpacity: 0 },
          }).addTo(baseLayer)
        }
      }
    }
  })).catch((error) => { mapStatus.value = '底图加载失败'; console.warn(error) })
})
watch([() => props.forecast.selected, () => props.forecast.mapValidTime?.validTime, () => props.forecast.selectedInitTime, () => props.forecast.confirmedProcessStartHour, () => props.forecast.confirmedProcessEndHour, () => props.forecast.loadingTimes], addConfiguredLayers)
watch(() => props.forecast.opacity, (value) => {
  for (const layer of dataLayers) layer.glLayer?.setOpacity?.(Number(value) / 100)
})
watch(() => props.forecast.region, () => updateRegionBoundary(true))
watch(() => props.forecast.zoom, (next, previous) => {
  if (!map || next === previous) return
  map.setZoom(Math.max(1, Math.min(9, map.getZoom() + (next > previous ? 1 : -1))))
})
onBeforeUnmount(() => { loadId++; resizeObserver?.disconnect(); clearDataLayers(); regionBoundaryLayer?.remove(); countryHighlightLayer?.remove(); regionTopology = null; baseLayer?.remove(); map?.remove(); map = null })
</script>
