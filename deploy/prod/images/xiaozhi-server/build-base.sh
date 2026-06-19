#!/usr/bin/env bash
# 【仅生产】构建并推送 xiaozhi-server-base 到 ACR（慢；requirements 变更时执行）
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
TAG="${SERVER_BASE_TAG:-latest}"
IMAGE="${REGISTRY}/${NS}/xiaozhi-server-base:${TAG}"

echo "==> [prod] build base: ${IMAGE}"
docker build -f Dockerfile-server-base -t "${IMAGE}" .

if [[ "${PUSH:-1}" == "1" ]]; then
  echo "==> [prod] push base: ${IMAGE}"
  docker push "${IMAGE}"
fi

echo "Done. BASE_IMAGE=${IMAGE}"
