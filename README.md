# Melo CMA NWP

CMA 数值预报 B/S 系统，包含 Spring Boot 后端和 Vue 3 前端。

## 项目结构

- `melo-cma-nwp/`: 后端服务，提供 GRIB2 元数据、瓦片、等值线和消息接口。
- `frontend/`: 前端地图工作台。
- `CMA-GFS-DATA/`: 本地开发数据目录，不应提交到 Git。

## 本地运行

后端需要 Java 21：

```bash
cd melo-cma-nwp
mvn test
mvn spring-boot:run
```

前端需要 Node.js：

```bash
cd frontend
npm install
npm run dev
```

后端数据目录可通过 `CMA_GFS_ROOT` 指定。接口中的 `dataPath` 第一段必须是已配置的数据源，例如 `cma-gfs/hgt/100/500/2026092900.024`。

## GitHub 发布前检查

- 不提交 `target/`、`node_modules/`、日志和气象数据文件。
- 生产环境通过环境变量或部署配置挂载数据目录。
- 根据实际部署地址更新前端 API 代理配置。

## Docker 部署

将数据放入 `CMA-GFS-DATA` 和 `CMA-MESO-DATA` 后执行：

```bash
docker compose up -d --build
```

应用通过 `http://localhost/` 访问，前端容器会将 `/api` 请求转发到后端容器。
# 内网部署

生产内网机器不需要连接互联网。联网构建机执行：

```bash
docker compose build
docker save -o melo-cma-nwp-images.tar tongh-backend:latest tongh-frontend:latest
```

将 `melo-cma-nwp-images.tar`、`docker-compose.yml`、`CMA-GFS-DATA`、`CMA-MESO-DATA`、`melo-cma-nwp/src/main/resources` 和 `frontend/public/config` 复制到内网机器，在内网执行：

```bash
docker load -i melo-cma-nwp-images.tar
docker compose up -d
```

Compose 会把后端 YAML 配置目录挂载到 `/app/config`，把前端 JSON 配置目录挂载到 Nginx 的 `/usr/share/nginx/html/config`。修改这些目录中的配置后执行 `docker compose restart backend frontend` 即可生效，不需要重新构建镜像。气象数据目录保持可写，以便 GRIB 库生成索引文件。
