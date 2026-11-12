#!/usr/bin/env bash
# 【仅生产】构建并推送 xiaozhi-server App 到 ACR（快；日常发版执行）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
cd "$ROOT"

PROD_DIR="${ROOT}/deploy/prod"
# shellcheck source=../../lib/server-image.sh
source "${PROD_DIR}/lib/server-image.sh"
server_env_load "$PROD_DIR" || {
  echo "请配置 deploy/prod/server.env（cp server.env.example server.env）" >&2
  exit 1
}

REGISTRY="${ACR_REGISTRY:?set ACR_REGISTRY in server.env}"
NS="${ACR_NAMESPACE:?set ACR_NAMESPACE}"
BASE_TAG="${SERVER_BASE_TAG:-latest}"
APP_TAG="${SERVER_APP_TAG:?set SERVER_APP_TAG}"
BASE_IMAGE="${REGISTRY}/${NS}/xiaozhi-server-base:${BASE_TAG}"
IMAGE="${REGISTRY}/${NS}/xiaozhi-server:${APP_TAG}"
DOCKERFILE="${ROOT}/deploy/prod/images/xiaozhi-server/Dockerfile.app"

echo "==> [prod] build app: ${IMAGE}"
echo "    FROM ${BASE_IMAGE}"
# 个人版 ACR 不兼容 BuildKit 推送的 OCI attestation 清单，固定用传统 build/push
export DOCKER_BUILDKIT=0
docker build -f "${DOCKERFILE}" \
  --build-arg "BASE_IMAGE=${BASE_IMAGE}" \
  -t "${IMAGE}" .

if [[ "${PUSH:-1}" == "1" ]]; then
  echo "==> [prod] push app: ${IMAGE}"
  docker push "${IMAGE}"
fi

echo "Done. deploy/prod/.env.prod:"
echo "SERVER_IMAGE=${IMAGE}"
