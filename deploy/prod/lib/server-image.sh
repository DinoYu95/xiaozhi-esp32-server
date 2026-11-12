# shellcheck shell=bash
# 由 ACR_REGISTRY + ACR_NAMESPACE + SERVER_APP_TAG 拼出 SERVER_IMAGE
server_image_resolve() {
  local reg="${ACR_REGISTRY:?请设置 ACR_REGISTRY（deploy/prod/server.env）}"
  local ns="${ACR_NAMESPACE:?请设置 ACR_NAMESPACE}"
  local tag="${SERVER_APP_TAG:?请设置 SERVER_APP_TAG}"
  echo "${reg}/${ns}/xiaozhi-server:${tag}"
}

server_env_load() {
  local prod_dir="${1:?}"
  if [[ -f "${prod_dir}/images/acr.env" ]]; then
    # shellcheck disable=SC1090
    source "${prod_dir}/images/acr.env"
  fi
  if [[ -f "${prod_dir}/.env.server-b" ]]; then
    # shellcheck disable=SC1090
    source "${prod_dir}/.env.server-b"
  fi
  if [[ -f "${prod_dir}/server.env" ]]; then
    # shellcheck disable=SC1090
    source "${prod_dir}/server.env"
    # server.env 为 tag 唯一来源：忽略 .env.server-b 里陈旧的整行 SERVER_IMAGE
    if [[ -n "${ACR_REGISTRY:-}" ]] && [[ -n "${ACR_NAMESPACE:-}" ]] && [[ -n "${SERVER_APP_TAG:-}" ]]; then
      SERVER_IMAGE="$(server_image_resolve)"
    fi
  elif [[ -z "${SERVER_IMAGE:-}" ]] && [[ -n "${ACR_REGISTRY:-}" ]] && [[ -n "${SERVER_APP_TAG:-}" ]]; then
    SERVER_IMAGE="$(server_image_resolve)"
  fi
  if [[ -z "${SERVER_IMAGE:-}" ]]; then
    echo "无法确定 SERVER_IMAGE：请配置 deploy/prod/server.env（推荐）或 SERVER_IMAGE" >&2
    return 1
  fi
}

server_env_set_tag() {
  local prod_dir="$1"
  local tag="$2"
  local env_file="${prod_dir}/server.env"
  if [[ ! -f "$env_file" ]]; then
    echo "缺少 ${env_file}，请先: cp server.env.example server.env" >&2
    return 1
  fi
  if grep -q '^SERVER_APP_TAG=' "$env_file"; then
    sed -i "s/^SERVER_APP_TAG=.*/SERVER_APP_TAG=${tag}/" "$env_file"
  else
    echo "SERVER_APP_TAG=${tag}" >> "$env_file"
  fi
  if [[ -f "${prod_dir}/images/acr.env" ]]; then
    if grep -q '^SERVER_APP_TAG=' "${prod_dir}/images/acr.env"; then
      sed -i "s/^SERVER_APP_TAG=.*/SERVER_APP_TAG=${tag}/" "${prod_dir}/images/acr.env"
    fi
  fi
}
