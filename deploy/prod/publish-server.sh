#!/usr/bin/env bash
# 构建并推送 xiaozhi-server 到 ACR（构建机 / 任意有 docker 与代码的机器）
#
# 用法：
#   cd deploy/prod && bash publish-server.sh              # 使用 server.env 里的 SERVER_APP_TAG
#   cd deploy/prod && bash publish-server.sh v1.0.2        # 顺便把 tag 写入 server.env
#   SKIP_GIT_PULL=1 bash publish-server.sh v1.0.2
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=lib/server-image.sh
source "${SCRIPT_DIR}/lib/server-image.sh"

NEW_TAG="${1:-}"
if [[ -n "$NEW_TAG" ]]; then
  server_env_set_tag "$SCRIPT_DIR" "$NEW_TAG"
  echo "==> 已设置 SERVER_APP_TAG=${NEW_TAG}（server.env / acr.env）"
fi

if [[ ! -f "${SCRIPT_DIR}/server.env" ]]; then
  echo "请先: cp server.env.example server.env 并检查 ACR 与 tag" >&2
  exit 1
fi

if [[ "${SKIP_GIT_PULL:-0}" != "1" ]] && command -v git >/dev/null && [[ -d "${ROOT}/.git" ]]; then
  echo "==> git pull"
  (cd "$ROOT" && git pull --rebase || git pull)
fi

bash "${SCRIPT_DIR}/images/xiaozhi-server/build-app.sh"

server_env_load "$SCRIPT_DIR"
echo ""
echo "完成。B 机执行:"
echo "  cd deploy/prod && bash deploy-server.sh"
echo "将部署: ${SERVER_IMAGE}"
