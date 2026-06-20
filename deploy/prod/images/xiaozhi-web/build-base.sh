#!/usr/bin/env bash
# 【仅生产】构建并推送 xiaozhi-web-runtime-base 到 ACR（nginx/ffmpeg/字体等变更时执行）
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
TAG="${WEB_RUNTIME_BASE_TAG:-latest}"
IMAGE="${REGISTRY}/${NS}/xiaozhi-web-runtime-base:${TAG}"
DOCKERFILE="${ROOT}/deploy/prod/images/xiaozhi-web/Dockerfile.base"

echo "==> [prod] build web runtime base: ${IMAGE}"
docker build -f "${DOCKERFILE}" -t "${IMAGE}" .

if [[ "${PUSH:-1}" == "1" ]]; then
  echo "==> [prod] push web runtime base: ${IMAGE}"
  docker push "${IMAGE}"
fi

echo "Done. RUNTIME_BASE=${IMAGE}"
