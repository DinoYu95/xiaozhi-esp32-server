#!/usr/bin/env bash
# B 机（语音节点）拉镜像并启动 — 版本号只认 deploy/prod/server.env
#
# 用法：
#   cd deploy/prod && bash deploy-server.sh
#   SKIP_PULL=1 bash deploy-server.sh    # 本机刚 publish 过、镜像已在本地
#   SKIP_GIT_PULL=1 bash deploy-server.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=lib/server-image.sh
source "${SCRIPT_DIR}/lib/server-image.sh"

if [[ "${SKIP_GIT_PULL:-0}" != "1" ]] && command -v git >/dev/null && [[ -d "${ROOT}/.git" ]]; then
  echo "==> git pull（同步 server.env / 配置）"
  (cd "$ROOT" && git pull --rebase || git pull)
fi

if [[ ! -f "${SCRIPT_DIR}/server.env" ]]; then
  echo "缺少 server.env，请: cp server.env.example server.env（与构建机保持一致）" >&2
  exit 1
fi

server_env_load "$SCRIPT_DIR" || exit 1
export SERVER_IMAGE

echo "==> 将部署镜像: ${SERVER_IMAGE}"

# 写入临时 env 供 compose 使用（保留端口等变量）
COMPOSE_ENV="${SCRIPT_DIR}/.env.server-b.runtime"
{
  echo "SERVER_IMAGE=${SERVER_IMAGE}"
  echo "SERVER_WS_PORT=${SERVER_WS_PORT:-8000}"
  echo "SERVER_HTTP_PORT=${SERVER_HTTP_PORT:-8003}"
} > "$COMPOSE_ENV"

ENV_FILE="$COMPOSE_ENV" SKIP_PULL="${SKIP_PULL:-0}" bash "${SCRIPT_DIR}/release-server.sh"
