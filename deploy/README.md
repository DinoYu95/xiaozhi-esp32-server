# 单机部署编排

本目录包含一键部署 **manager-api、manager-web、xiaozhi-server** 及 **MySQL、Redis** 的 Docker Compose 配置。各服务所需环境与依赖均在镜像内（或通过 Dockerfile 从源码安装）。

## 快速开始

1. 复制并编辑配置：`cp .env.example .env`，修改 `MYSQL_ROOT_PASSWORD` 等。
2. 在**同一目录**下创建 `data/`、`models/SenseVoiceSmall/`，并下载语音模型到 `models/SenseVoiceSmall/model.pt`。
3. （可选）首次与本地 MySQL 一致：在本地用 `mysqldump` 导出库，将 SQL 放到 `init-db/`（如 `init-db/01-dump.sql`），再在远程**第一次**执行 up。
4. 启动：
   - **从源码构建并启动**（推荐）：`docker compose up -d --build`（会按 `Dockerfile-web`、`Dockerfile-server-standalone` 安装前端/Java/Python 依赖并启动）。
   - **使用预构建镜像**：在 `.env` 中设置 `WEB_IMAGE`、`SERVER_IMAGE` 后执行 `docker compose up -d`。

详细步骤、各服务启动方式与依赖说明见：[../docs/阿里云单机部署.md](../docs/阿里云单机部署.md)。
