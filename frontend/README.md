# CMA-GFS 前端工作台

一个面向 CMA-GFS 数值预报数据的 Vue 3.5 + Vite 前端可视化工程，组件按顶部控制栏、产品菜单、地图工作区、时效轴和点位信息拆分，后续可以把地图图层和点位信息替换为真实 API 数据。

菜单配置位于 `src/config/menuConfig.js`，来源于 `grapes/src/assets/js/PlotConfig.js` 的产品定义。每个菜单项都包含 `ele` 和 `layers`，其中 `layers` 是该产品需要显示的图层键；组合图层会按数组顺序声明，例如 `500hPa高度925hPa风场` 为 `wind_925_c + height_500`。

## 开发

```bash
npm install
npm run dev
```

## 构建

```bash
npm run build
```

主要入口位于 `src/main.js` 和 `src/style.css`。地图使用 Leaflet 和 Grapes 的二进制图层引擎，开发环境将 `/api` 代理到本机 8080 端口的 `melo-cma-gfs` 服务。

## 过程累计降水

选择“暴雨 → 过程累计降水”后，时效列表分为“起始时效”和“结束时效”两列，默认展示全部时效。两端都由用户选择，起始时效必须早于结束时效。例如结束选择 `096`、起始选择 `039`，图层显示同一起报时次的 `APCP(096) - APCP(039)`，地图下方标注 57 小时过程及北京时间范围。

图层沿用 Grapes 的过程降水色标和数值标记，单位 mm。元数据和瓦片分别请求 `/api/cma-nwp/meteo/minusMeta`、`/api/cma-nwp/meteo/minusTile`，参数 `dataPath` 为结束 APCP 路径，`dataPath2` 为起始 APCP 路径；瓦片额外传入 `z/x/y`。示例路径为 `apcp/1/0/2026092900.096` 和 `apcp/1/0/2026092900.039`。
