#!/usr/bin/env bash
# 【仅生产】构建并推送 xiaozhi-web App 到 ACR（编 Vue + Java；日常发版执行）
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
RUNTIME_TAG="${WEB_RUNTIME_BASE_TAG:-latest}"
APP_TAG="${WEB_APP_TAG:?set WEB_APP_TAG}"
RUNTIME_BASE="${REGISTRY}/${NS}/xiaozhi-web-runtime-base:${RUNTIME_TAG}"
IMAGE="${REGISTRY}/${NS}/xiaozhi-web:${APP_TAG}"
DOCKERFILE="${ROOT}/deploy/prod/images/xiaozhi-web/Dockerfile.app"

echo "==> [prod] build web app: ${IMAGE}"
echo "    RUNTIME_BASE=${RUNTIME_BASE}"
docker build -f "${DOCKERFILE}" \
  --build-arg "RUNTIME_BASE=${RUNTIME_BASE}" \
  -t "${IMAGE}" .

if [[ "${PUSH:-1}" == "1" ]]; then
  echo "==> [prod] push web app: ${IMAGE}"
  docker push "${IMAGE}"
fi

echo "Done. deploy/prod/.env.prod:"
echo "WEB_IMAGE=${IMAGE}"
