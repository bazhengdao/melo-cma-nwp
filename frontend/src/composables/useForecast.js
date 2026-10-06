import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { menuGroups } from '../config/runtimeConfig'

export { menuGroups as productGroups }
export const regions = ['全国', '东北', '华北', '西北', '西南', '华中', '华东', '华南']
const productItems = menuGroups.flatMap((group) => group.items.map((item) => ({ ...item, group: group.name, groupKey: group.key })))

const api = (path, params = {}) => {
  const query = new URLSearchParams(params).toString()
  return `/api/cma-nwp/${path}${query ? `?${query}` : ''}`
}

const fallbackInitTimes = [{ utc: '2026092900', beijing: '2026092908' }]
const fallbackValidTimes = ['024', '027', '030', '033', '036', '039', '042', '045', '048', '051', '054', '057'].map((validTime) => {
  const forecast = new Date(Date.UTC(2026, 8, 29, Number(validTime)))
  const fcstTime = `${forecast.getUTCFullYear()}${pad2(forecast.getUTCMonth() + 1)}${pad2(forecast.getUTCDate())}${pad2(forecast.getUTCHours())}`
  return { validTime, time: `2026092900.${validTime}`, fcstTime }
})

export function useForecast() {
  const selected = ref(productItems[0])
  const region = ref('全国')
  const initTimes = ref([])
  const validTimes = ref([])
  const selectedInitTime = ref('')
  const hourIndex = ref(0)
  const processStartHour = ref('')
  const processEndHour = ref('')
  const processClickHistory = ref([])
  const confirmedProcessStartHour = ref('')
  const confirmedProcessEndHour = ref('')
  const isProcessRain = computed(() => selected.value.layers?.some((layer) => layer.processRain))
  const playing = ref(false)
  const opacity = ref(82)
  const collapsed = ref(false)
  const inspectorHidden = ref(false)
  const refreshing = ref(false)
  const loadingTimes = ref(false)
  const timesError = ref('')
  const zoom = ref(1)
  let timer
  let timesLoadId = 0

  const currentValidTime = computed(() => validTimes.value[hourIndex.value] || (isProcessRain.value ? null : fallbackValidTimes[hourIndex.value] || fallbackValidTimes[0]))
  const mapValidTime = computed(() => isProcessRain.value
    ? validTimes.value.find((item) => item.validTime === confirmedProcessEndHour.value) || null
    : currentValidTime.value)
  const hours = computed(() => validTimes.value.map((item) => item.validTime).filter(Boolean))
  const dates = computed(() => validTimes.value.map((item) => formatForecastTime(item)))
  const timeSlots = computed(() => validTimes.value.map((item, index) => ({ ...item, index, date: formatForecastTime(item) })))
  const processRangeLabel = computed(() => {
    const start = timeSlots.value.find((item) => item.validTime === confirmedProcessStartHour.value)
    const end = timeSlots.value.find((item) => item.validTime === confirmedProcessEndHour.value)
    if (!start || !end || Number(start.validTime) >= Number(end.validTime)) return ''
    return `${start.date} → ${end.date} 北京时间（${Number(end.validTime) - Number(start.validTime)}小时）`
  })
  const processSelectionValid = computed(() => {
    return Boolean(processStartHour.value && processEndHour.value && Number(processStartHour.value) < Number(processEndHour.value))
  })
  const selectedInit = computed(() => initTimes.value.find((item) => item.utc === selectedInitTime.value) || {})
  const hour = computed(() => currentValidTime.value?.validTime || '')
  const timeLabel = computed(() => hour.value ? `T+${Number(hour.value)}h` : 'T+--h')
  const initTimeLabel = computed(() => selectedInit.value.beijing ? `${formatInitTime(selectedInit.value.beijing)} 北京时间` : '--')
  const initDate = computed(() => selectedInit.value.beijing ? formatDateInput(selectedInit.value.beijing) : '')
  const initHour = computed(() => selectedInit.value.beijing?.slice(8, 10) || '')
  const initHours = computed(() => initTimes.value.filter((item) => item.beijing?.startsWith((selectedInit.value.beijing || '').slice(0, 8))).map((item) => item.beijing.slice(8, 10)))
  const pointValue = computed(() => (30 + hourIndex.value * 1.7).toFixed(1))

  const selectHour = (index) => {
    hourIndex.value = Math.max(0, Math.min(Math.max(validTimes.value.length - 1, 0), index))
    if (isProcessRain.value) {
      const end = validTimes.value[hourIndex.value]?.validTime
      if (processStartHour.value && end && Number(end) > Number(processStartHour.value)) processEndHour.value = end
    }
  }
  const canShiftProcessRange = (offset) => {
    if (!isProcessRain.value || !Number.isInteger(offset) || !processStartHour.value || !processEndHour.value) return false
    const startIndex = validTimes.value.findIndex((item) => item.validTime === processStartHour.value)
    const endIndex = validTimes.value.findIndex((item) => item.validTime === processEndHour.value)
    return startIndex >= 0 && endIndex >= 0
      && startIndex + offset >= 0
      && endIndex + offset < validTimes.value.length
  }
  const shiftProcessRange = (offset) => {
    if (!canShiftProcessRange(offset)) return false
    const startIndex = validTimes.value.findIndex((item) => item.validTime === processStartHour.value)
    const endIndex = validTimes.value.findIndex((item) => item.validTime === processEndHour.value)
    const start = validTimes.value[startIndex + offset]?.validTime
    const end = validTimes.value[endIndex + offset]?.validTime
    if (!start || !end) return false
    processClickHistory.value = [start, end]
    processStartHour.value = start
    processEndHour.value = end
    hourIndex.value = endIndex + offset
    if (confirmedProcessStartHour.value && confirmedProcessEndHour.value) {
      confirmedProcessStartHour.value = start
      confirmedProcessEndHour.value = end
    }
    return true
  }
  const selectProcessTime = (index) => {
    const value = validTimes.value[index]?.validTime
    if (!value) return
    processClickHistory.value = [...processClickHistory.value, value].slice(-2)
    const clicks = processClickHistory.value
    if (clicks.length === 1) {
      processStartHour.value = clicks[0]
      processEndHour.value = ''
    } else {
      const [start, end] = [...clicks].sort((left, right) => Number(left) - Number(right))
      processStartHour.value = start
      processEndHour.value = end
    }
    hourIndex.value = index
  }
  const selectProcessStart = (value) => {
    const index = validTimes.value.findIndex((item) => item.validTime === value)
    if (index >= 0) selectProcessTime(index)
  }
  const confirmProcessRange = () => {
    if (!processSelectionValid.value) return false
    confirmedProcessStartHour.value = processStartHour.value
    confirmedProcessEndHour.value = processEndHour.value
    return true
  }
  const clearProcessSelection = () => {
    processClickHistory.value = []
    processStartHour.value = ''
    processEndHour.value = ''
  }
  const clearProcessConfirmation = () => {
    confirmedProcessStartHour.value = ''
    confirmedProcessEndHour.value = ''
  }
  const normalizeProcessSelection = () => {
    if (!isProcessRain.value || loadingTimes.value) return
    if (validTimes.value.length < 2) { clearProcessSelection(); return }
    const valid = new Set(validTimes.value.map((item) => item.validTime))
    processClickHistory.value = processClickHistory.value.filter((value) => valid.has(value)).slice(-2)
    if (!processClickHistory.value.length) {
      processStartHour.value = ''
      processEndHour.value = ''
      return
    }
    if (processClickHistory.value.length === 1) {
      processStartHour.value = processClickHistory.value[0]
      processEndHour.value = ''
      return
    }
    const [start, end] = [...processClickHistory.value].sort((left, right) => Number(left) - Number(right))
    processStartHour.value = start
    processEndHour.value = end
    hourIndex.value = validTimes.value.findIndex((item) => item.validTime === processClickHistory.value[1])
  }
  watch([validTimes, isProcessRain, loadingTimes], normalizeProcessSelection, { flush: 'sync' })
  const selectProduct = async (product, group) => {
    const next = { ...product, group: group?.name || product.group, groupKey: group?.key || product.groupKey }
    if (selectedInitTime.value) loadingTimes.value = true
    clearProcessConfirmation()
    clearProcessSelection()
    selected.value = next
    if (selectedInitTime.value) await loadValidTimes(selectedInitTime.value, next)
  }
  const selectInitTime = async (value) => {
    if (!value || value === selectedInitTime.value) return
    selectedInitTime.value = value
    clearProcessConfirmation()
    clearProcessSelection()
    await loadValidTimes(value, selected.value)
  }
  const shiftInitTime = async (offset) => {
    const index = initTimes.value.findIndex((item) => item.utc === selectedInitTime.value)
    const next = initTimes.value[index + offset]
    if (next) await selectInitTime(next.utc)
  }
  const selectInitDate = (value) => {
    const date = value.replaceAll('-', '')
    const next = initTimes.value.find((item) => item.beijing?.startsWith(date))
    if (next) selectInitTime(next.utc)
  }
  const selectInitHour = (value) => {
    const date = (selectedInit.value.beijing || '').slice(0, 8)
    const next = initTimes.value.find((item) => item.beijing === `${date}${value}`)
    if (next) selectInitTime(next.utc)
  }
  const zoomIn = () => { zoom.value = Math.min(1.45, +(zoom.value + 0.1).toFixed(2)) }
  const zoomOut = () => { zoom.value = Math.max(0.8, +(zoom.value - 0.1).toFixed(2)) }
  const togglePlay = () => {
    playing.value = !playing.value
    clearInterval(timer)
    if (playing.value) timer = setInterval(() => selectHour((hourIndex.value + 1) % Math.max(validTimes.value.length, 1)), 1000)
  }
  const refresh = async () => { refreshing.value = true; await loadInitTimes(selectedInitTime.value || undefined); refreshing.value = false }

  async function loadInitTimes(preferred) {
    loadingTimes.value = true
    timesError.value = ''
    try {
      const response = await fetch(api('inittimes', { dataCode: 'cma-gfs' }))
      if (!response.ok) throw new Error(`inittimes ${response.status}`)
      initTimes.value = await response.json()
      const next = initTimes.value.find((item) => item.utc === preferred)?.utc || initTimes.value[0]?.utc
      if (next) {
        selectedInitTime.value = next
        await loadValidTimes(next, selected.value)
      }
    } catch (error) {
      timesError.value = '时间接口暂不可用，显示示例时次'
      initTimes.value = fallbackInitTimes
      selectedInitTime.value = fallbackInitTimes[0].utc
      validTimes.value = isProcessRain.value ? [] : fallbackValidTimes
      if (isProcessRain.value) timesError.value = '过程降水时效加载失败，请刷新重试'
    } finally {
      loadingTimes.value = false
    }
  }

  async function loadValidTimes(inittime, product = selected.value) {
    const requestId = ++timesLoadId
    loadingTimes.value = true
    timesError.value = ''
    if (product?.layers?.some((layer) => layer.processRain)) clearProcessConfirmation()
    const previousValidTime = currentValidTime.value?.validTime
    try {
      const dataPath = product?.layers?.find((layer) => layer.dataPath)?.dataPath
      const response = await fetch(api('validtimes', {
        dataCode: 'cma-gfs',
        inittime,
        ...(dataPath ? { dataPath } : {}),
      }))
      if (!response.ok) throw new Error(`validtimes ${response.status}`)
      const times = await response.json()
      if (requestId !== timesLoadId) return
      validTimes.value = times
      hourIndex.value = findClosestValidTimeIndex(validTimes.value, previousValidTime)
      normalizeProcessSelection()
    } catch (error) {
      if (requestId !== timesLoadId) return
      timesError.value = '预报时效接口暂不可用，显示示例时效'
      validTimes.value = product.layers?.some((layer) => layer.processRain) ? [] : fallbackValidTimes
      if (!validTimes.value.length) timesError.value = '过程降水时效加载失败，请刷新重试'
      hourIndex.value = findClosestValidTimeIndex(validTimes.value, previousValidTime)
      normalizeProcessSelection()
    } finally {
      if (requestId === timesLoadId) loadingTimes.value = false
    }
  }

  onMounted(() => loadInitTimes())
  onBeforeUnmount(() => clearInterval(timer))
  return { isProcessRain, processStartHour, processEndHour, selectProcessTime, selectProcessStart, canShiftProcessRange, shiftProcessRange, confirmProcessRange, processSelectionValid, processRangeLabel, confirmedProcessStartHour, confirmedProcessEndHour, mapValidTime, menuGroups, selected, selectProduct, region, initTimes, validTimes, timeSlots, selectedInitTime, loadingTimes, timesError, hourIndex, playing, opacity, collapsed, inspectorHidden, refreshing, zoom, hours, dates, hour, timeLabel, initTimeLabel, initDate, initHour, initHours, pointValue, currentValidTime, selectHour, selectInitTime, shiftInitTime, selectInitDate, selectInitHour, zoomIn, zoomOut, togglePlay, refresh }
}

function findClosestValidTimeIndex(times, target) {
  if (!times.length || target == null || target === '') return 0
  const targetNumber = Number(target)
  if (!Number.isFinite(targetNumber)) return 0
  let closestIndex = 0
  let closestDistance = Infinity
  times.forEach((item, index) => {
    const distance = Math.abs(Number(item.validTime) - targetNumber)
    if (Number.isFinite(distance) && distance < closestDistance) {
      closestDistance = distance
      closestIndex = index
    }
  })
  return closestIndex
}

function formatInitTime(value) {
  if (!/^\d{10}$/.test(value)) return value
  return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)} ${value.slice(8, 10)}:00`
}

function formatForecastTime(item) {
  if (item?.fcstTime && /^\d{10}$/.test(item.fcstTime)) {
    const utc = Date.UTC(Number(item.fcstTime.slice(0, 4)), Number(item.fcstTime.slice(4, 6)) - 1, Number(item.fcstTime.slice(6, 8)), Number(item.fcstTime.slice(8, 10)))
    const beijing = new Date(utc + 8 * 60 * 60 * 1000)
    return `${pad2(beijing.getUTCMonth() + 1)}-${pad2(beijing.getUTCDate())} ${pad2(beijing.getUTCHours())}`
  }
  return item?.validTime ? `T+${Number(item.validTime)}h` : '--'
}

function pad2(value) {
  return String(value).padStart(2, '0')
}

function formatDateInput(value) {
  return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)}`
}
