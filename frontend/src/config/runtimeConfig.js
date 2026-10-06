import { Color } from '../lib/mapEngine'

export let palettes = {}
export let paletteUnits = {}
export let menuGroups = []

export async function loadPublicConfig() {
  const [paletteResponse, menuResponse] = await Promise.all([
    fetch(`${import.meta.env.BASE_URL}config/palettes.json`),
    fetch(`${import.meta.env.BASE_URL}config/menu.json`),
  ])
  if (!paletteResponse.ok || !menuResponse.ok) {
    throw new Error('前端图层配置加载失败')
  }
  const paletteConfig = await paletteResponse.json()
  const menuConfig = await menuResponse.json()
  palettes = Object.fromEntries(Object.entries(paletteConfig.palettes || {}).map(([key, value]) => {
    const palette = new Color(value.ident, value.size, value.gradient)
    palette.method = 'floor'
    palette.colors = null
    return [key, palette]
  }))
  paletteUnits = paletteConfig.paletteUnits || {}
  menuGroups = (menuConfig.groups || []).map((group) => ({
    ...group,
    items: (group.items || []).map((item) => ({
      ...item,
      layers: (item.layers || []).map((layer) => ({
        ...layer,
        color: palettes[layer.color] || layer.color,
      })),
    })),
  }))
}
