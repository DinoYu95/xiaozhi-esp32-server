# 语音节点（B 机）部署 xiaozhi-server

A 机跑智控台、MQTT 网关、Agent 等；**B 机只跑 `xiaozhi-server`（8000 WS / 8003 HTTP）**。环境与 A 对齐：**Docker + Compose + GitHub + ACR 拉镜像**，不单独 build Java/web。

---

## 1. 与 A 机要对齐什么

| 项 | A 机 | B 机 |
|----|------|------|
| Docker / Compose | 有 | **bootstrap-host.sh 装同款** |
| 代码目录 | 常 `/opt/xiaozhi-esp32-server` | **相同**（DevOps「代码根目录」+ slug） |
| GitHub | SSH Deploy Key / 密钥对 | **同一把私钥** 或只读 Deploy Key |
| 镜像 | build + push ACR | **只 pull**，`SERVER_IMAGE` 与 A 发版 tag 一致 |
| RDS / Redis | web 连 | **server 不直连库**，配置来自 manager-api |
| `deploy/data/.config.yaml` | 有 | **必有一份**，`manager-api.url` 指 **A 内网** |
| ASR 模型 `model.pt` | 有 | **从 A rsync 或 bootstrap 下载** |

---

## 2. 安全组与网络

- B 与 A **同 VPC**。
- **8000、8003**：对 **A 内网 IP** 和（若用 ALB）**SLB 回源网段** 放行；B 可无公网。
- A 上 **MQTT `chat_servers`** 改为：  
  `ws://<B内网IP>:8000/xiaozhi/v1/?from=mqtt_gateway`

---

## 3. 一键初始化（新 B 机 SSH 上去执行）

```bash
# 若仓库已在 A 上用过，可把 bootstrap 脚本拷过去；或在 clone 后执行：
cd /opt/xiaozhi-esp32-server   # 或先 clone 再 cd
sudo bash deploy/prod/bootstrap-host.sh
```

常用变量：

```bash
export REPOS_ROOT=/opt
export GITHUB_REPO=git@github.com:DinoYu95/xiaozhi-esp32-server.git
export DOWNLOAD_MODEL=1          # 首次在 B 上下载 model.pt
# 编辑 deploy/prod/images/acr.env 后：
export ACR_LOGIN=1
```

**GitHub**：与 DevOps 文档一致——ECS `~/.ssh` 能 `ssh -T git@github.com`；或在 DevOps「主机」里点 **初始化**（逻辑同 `host_bootstrap.py` 的 bootstrap 脚本）。

---

## 4. 配置与启动

```bash
cd /opt/xiaozhi-esp32-server/deploy/prod

cp .env.server-b.example .env.server-b
# 编辑 SERVER_IMAGE（与 A 当前发版一致）

cp server-config.example.yaml ../data/.config.yaml
# 编辑：
#   manager-api.url: http://<A内网>:8002/xiaozhi
#   manager-api.secret: 智控台 server.secret
#   vision_explain: https://api.你的域名/mcp/vision/explain

# 若模型未下载，从 A 同步示例：
# rsync -avz A:/opt/xiaozhi-esp32-server/deploy/models/ B:/opt/xiaozhi-esp32-server/deploy/models/

bash release-server.sh
```

验证：

```bash
# B 上
curl -sS -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8003/

# A 上（内网）
curl -sS -o /dev/null -w "%{http_code}\n" http://<B内网>:8003/
```

---

## 5. 用 DevOps 控制台（可选）

1. **主机** → 导入 B 机 ECS → 角色选 **Runtime** → 代码根目录 `/opt`。
2. **主机** → **检查就绪** / **初始化**（装 Docker、建目录）。
3. **项目** `xiaozhi-esp32-server` → 同步代码到 B（`git pull`）。
4. 为组件 **`xiaozhi-server`** 新建部署目标，绑定 **B 机**；发版时选 **deploy**（需 B 上已有 `.env.server-b` 与 `docker-compose.server-only.yml`）。

目前业务仓库里 **server 发版脚本** 为 `deploy/prod/release-server.sh`；全量 web+server 仍用 A 上 `docker-compose.yml` + `.env.prod`。

---

## 6. 从 A 拷哪些文件最省事

不必整块复制 A 的 `.env.prod`（含 MySQL/Redis）。**最少集**：

- `deploy/prod/.env.server-b`（或手工写 `SERVER_IMAGE`）
- `deploy/data/.config.yaml`（改 `manager-api.url` 为 A 内网）
- `deploy/models/SenseVoiceSmall/model.pt`
- `deploy/prod/images/acr.env`（拉镜像登录，勿提交 Git）

`uploadfile`、MySQL 数据 **不需要** 在 B 上。

---

## 7. 发版流程（建议）

1. 在 **A（Builder）** 或 CI：`deploy/prod/images/xiaozhi-server/build-app.sh` → push ACR。  
2. 更新 **A** `.env.prod` 与 **B** `.env.server-b` 的 `SERVER_IMAGE` tag。  
3. A：`docker compose --env-file .env.prod up -d`（若 web 也变）。  
4. B：`bash deploy/prod/release-server.sh`。

智控台参数、Nginx/ALB、`chat_servers` 在切流量前一次性核对即可。
