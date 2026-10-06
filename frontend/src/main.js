import { createApp } from 'vue'
import './style.css'
import 'leaflet/dist/leaflet.css'
import { loadMapEngine } from './lib/mapEngine'

async function bootstrap() {
  await loadMapEngine()
  const { default: App } = await import('./App.vue')
  createApp(App).mount('#app')
}

bootstrap().catch((error) => {
  console.error('地图初始化失败', error)
  document.querySelector('#app').textContent = '地图资源加载失败，请刷新重试。'
})
