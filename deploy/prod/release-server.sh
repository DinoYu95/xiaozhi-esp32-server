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
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.server-only.yml"

# shellcheck source=lib/server-image.sh
source "${SCRIPT_DIR}/lib/server-image.sh"

ENV_FILE="${ENV_FILE:-${SCRIPT_DIR}/.env.server-b.runtime}"
if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
elif [[ -f "${SCRIPT_DIR}/server.env" ]]; then
  server_env_load "$SCRIPT_DIR" || exit 1
  export SERVER_IMAGE
elif [[ -f "${SCRIPT_DIR}/.env.server-b" ]]; then
  # shellcheck disable=SC1090
  source "${SCRIPT_DIR}/.env.server-b"
else
  echo "缺少 server.env，请: cp server.env.example server.env 或 bash deploy-server.sh" >&2
  exit 1
fi

if [[ -z "${SERVER_IMAGE:-}" ]]; then
  server_env_load "$SCRIPT_DIR" || exit 1
fi
export SERVER_IMAGE

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
