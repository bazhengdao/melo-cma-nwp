import L from 'leaflet'

export { L }
export let Color
export let BinLayer
export let WindLayer
export let ContourLayer
export let Meta3

let loading

// Load the original engine before evaluating the palette and menu modules.
export function loadMapEngine() {
  if (!loading) {
    globalThis.L = L
    loading = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = `${import.meta.env.BASE_URL}vendor/bundle.js`
      script.onload = () => {
        ;({ Color, BinLayer, WindLayer, ContourLayer, Meta3 } = window)
        if ([Color, BinLayer, WindLayer, ContourLayer, Meta3].some((value) => typeof value !== 'function')) {
          reject(new Error('bundle.js 未导出完整的地图图层构造器'))
          return
        }
        resolve()
      }
      script.onerror = () => reject(new Error('bundle.js 加载失败'))
      document.head.appendChild(script)
    })
  }
  return loading
}
