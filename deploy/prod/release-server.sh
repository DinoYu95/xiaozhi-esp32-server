#!/usr/bin/env bash
# B 机（或任意仅 runtime server 节点）拉镜像并启动 xiaozhi-server
#
# 用法：
#   cd deploy/prod && bash release-server.sh
#
# 环境变量：
#   ENV_FILE=./.env.server-b
#   SKIP_PULL=1     跳过 docker pull（本地刚 build 时）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${ENV_FILE:-${SCRIPT_DIR}/.env.server-b}"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.server-only.yml"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "缺少 ${ENV_FILE}，请先: cp .env.server-b.example .env.server-b" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"
if [[ -z "${SERVER_IMAGE:-}" ]]; then
  echo "请在 ${ENV_FILE} 中设置 SERVER_IMAGE" >&2
  exit 1
fi

DATA_YAML="${SCRIPT_DIR}/../data/.config.yaml"
MODEL="${SCRIPT_DIR}/../models/SenseVoiceSmall/model.pt"
if [[ ! -f "$DATA_YAML" ]]; then
  echo "WARN: 未找到 ${DATA_YAML}，容器可能无法连 manager-api" >&2
fi
if [[ ! -f "$MODEL" ]]; then
  echo "WARN: 未找到 ASR 模型 ${MODEL}，请从 A 机 rsync 或 bootstrap-host.sh DOWNLOAD_MODEL=1" >&2
fi

cd "$SCRIPT_DIR"
if [[ "${SKIP_PULL:-0}" != "1" ]]; then
  echo "==> docker pull ${SERVER_IMAGE}"
  docker pull "$SERVER_IMAGE"
fi

echo "==> docker compose up -d --force-recreate server"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --force-recreate server

echo "==> 健康检查（本机 WS 端口）"
sleep 2
if command -v curl >/dev/null 2>&1; then
  curl -sS -o /dev/null -w "HTTP %{http_code}\n" "http://127.0.0.1:${SERVER_HTTP_PORT:-8003}/" || true
fi
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
echo "完成。请从 A 机验证: curl -I http://<B内网>:8003/ ；MQTT chat_servers 指向 B:8000"
