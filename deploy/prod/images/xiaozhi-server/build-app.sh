#!/usr/bin/env bash
# 【仅生产】构建并推送 xiaozhi-server App 到 ACR（快；日常发版执行）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
cd "$ROOT"

ENV_FILE="${ROOT}/deploy/prod/images/acr.env"
if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
fi

REGISTRY="${ACR_REGISTRY:?set ACR_REGISTRY in deploy/prod/images/acr.env}"
NS="${ACR_NAMESPACE:?set ACR_NAMESPACE}"
BASE_TAG="${SERVER_BASE_TAG:-latest}"
APP_TAG="${SERVER_APP_TAG:?set SERVER_APP_TAG}"
BASE_IMAGE="${REGISTRY}/${NS}/xiaozhi-server-base:${BASE_TAG}"
IMAGE="${REGISTRY}/${NS}/xiaozhi-server:${APP_TAG}"
DOCKERFILE="${ROOT}/deploy/prod/images/xiaozhi-server/Dockerfile.app"

echo "==> [prod] build app: ${IMAGE}"
echo "    FROM ${BASE_IMAGE}"
docker build -f "${DOCKERFILE}" \
  --build-arg "BASE_IMAGE=${BASE_IMAGE}" \
  -t "${IMAGE}" .

if [[ "${PUSH:-1}" == "1" ]]; then
  echo "==> [prod] push app: ${IMAGE}"
  docker push "${IMAGE}"
fi

echo "Done. deploy/prod/.env.prod:"
echo "SERVER_IMAGE=${IMAGE}"
