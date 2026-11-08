#!/usr/bin/env bash
# 新 ECS 环境初始化：Docker、Git、目录、可选 clone 仓库、可选 ACR 登录、可选拉取 ASR 模型
#
# 用法（在目标机上直接跑，或 DevOps SSH 远程执行）：
#   curl -fsSL …/bootstrap-host.sh | bash
#   bash deploy/prod/bootstrap-host.sh
#
# 环境变量：
#   REPOS_ROOT=/opt          代码根目录（与 DevOps 主机配置一致）
#   REPO_SLUG=xiaozhi-esp32-server
#   GITHUB_REPO=             设为空则跳过 clone（仅装 Docker）
#   SKIP_DOCKER=0
#   SKIP_CLONE=0
#   ACR_LOGIN=1              若 deploy/prod/images/acr.env 存在则 docker login
#   DOWNLOAD_MODEL=0         1 时在仓库 deploy 下下载 SenseVoiceSmall（约 1GB+）
set -euo pipefail

REPOS_ROOT="${REPOS_ROOT:-/opt}"
REPO_SLUG="${REPO_SLUG:-xiaozhi-esp32-server}"
GITHUB_REPO="${GITHUB_REPO:-git@github.com:DinoYu95/xiaozhi-esp32-server.git}"
SKIP_DOCKER="${SKIP_DOCKER:-0}"
SKIP_CLONE="${SKIP_CLONE:-0}"
ACR_LOGIN="${ACR_LOGIN:-1}"
DOWNLOAD_MODEL="${DOWNLOAD_MODEL:-0}"

log() { echo "[bootstrap-host] $*"; }

if [ -f /etc/os-release ]; then
  # shellcheck disable=SC1091
  . /etc/os-release
else
  ID=unknown
fi

install_docker_apt() {
  log "apt 安装依赖..."
  apt-get update -qq
  apt-get install -y ca-certificates curl git gnupg
  if ! command -v docker >/dev/null 2>&1; then
    log "安装 Docker (get.docker.com)..."
    curl -fsSL https://get.docker.com | sh
  fi
}

install_docker_yum() {
  log "yum/dnf 安装依赖..."
  if command -v dnf >/dev/null 2>&1; then
    dnf install -y docker git curl ca-certificates || true
  else
    yum install -y docker git curl ca-certificates || true
  fi
  if ! command -v docker >/dev/null 2>&1; then
    curl -fsSL https://get.docker.com | sh
  fi
}

if [[ "$SKIP_DOCKER" != "1" ]]; then
  case "${ID:-unknown}" in
    ubuntu|debian) install_docker_apt ;;
    alinux|centos|rhel|rocky|almalinux|anolis) install_docker_yum ;;
    *)
      log "未知系统 $ID，尝试 apt 后 yum"
      install_docker_apt || install_docker_yum
      ;;
  esac
  systemctl enable docker 2>/dev/null || true
  systemctl start docker 2>/dev/null || service docker start 2>/dev/null || true
  docker --version
  docker compose version 2>/dev/null || docker-compose --version
fi

command -v git >/dev/null 2>&1 || { log "ERROR: git 未安装"; exit 1; }

mkdir -p "$REPOS_ROOT"
chmod 755 "$REPOS_ROOT"

REPO_DIR="${REPOS_ROOT}/${REPO_SLUG}"
if [[ "$SKIP_CLONE" != "1" && -n "$GITHUB_REPO" ]]; then
  if [[ -d "$REPO_DIR/.git" ]]; then
    log "仓库已存在，git pull: $REPO_DIR"
    git -C "$REPO_DIR" pull --ff-only || log "WARN: pull 失败，请检查 SSH 密钥与网络"
  else
    log "clone $GITHUB_REPO -> $REPO_DIR"
    if [[ "$GITHUB_REPO" == git@* ]]; then
      if [[ ! -f "${HOME}/.ssh/id_rsa" && ! -f "${HOME}/.ssh/id_ed25519" ]]; then
        log "WARN: 未找到 ~/.ssh 私钥，GitHub SSH clone 可能失败"
        log "      请在阿里云 ECS 绑定密钥对，或把 Deploy/DevOps 公钥写入 ~/.ssh/authorized_keys 并配置 GitHub Deploy Key"
      fi
    fi
    git clone "$GITHUB_REPO" "$REPO_DIR"
  fi
fi

if [[ -d "$REPO_DIR/deploy" ]]; then
  DEPLOY_ROOT="$REPO_DIR/deploy"
elif [[ -d "$(dirname "$0")/../data" ]] || [[ -f "$(dirname "$0")/docker-compose.yml" ]]; then
  DEPLOY_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
  REPO_DIR="$(cd "$DEPLOY_ROOT/.." && pwd)"
else
  DEPLOY_ROOT=""
fi

if [[ -n "$DEPLOY_ROOT" ]]; then
  mkdir -p "$DEPLOY_ROOT/data" "$DEPLOY_ROOT/models/SenseVoiceSmall" "$DEPLOY_ROOT/uploadfile"
  if [[ ! -f "$DEPLOY_ROOT/data/.config.yaml" && -f "$DEPLOY_ROOT/prod/server-config.example.yaml" ]]; then
    log "未找到 data/.config.yaml，可从 server-config.example.yaml 复制后编辑"
  fi
fi

if [[ "$ACR_LOGIN" == "1" && -n "$DEPLOY_ROOT" ]]; then
  ACR_ENV="$DEPLOY_ROOT/prod/images/acr.env"
  if [[ -f "$ACR_ENV" ]]; then
    # shellcheck disable=SC1090
    source "$ACR_ENV"
    if [[ -n "${ACR_REGISTRY:-}" ]]; then
      log "ACR 登录提示: 在控制台复制 docker login 命令，或配置 ACR_USERNAME/ACR_PASSWORD 后执行"
      if [[ -n "${ACR_USERNAME:-}" && -n "${ACR_PASSWORD:-}" ]]; then
        echo "$ACR_PASSWORD" | docker login "$ACR_REGISTRY" -u "$ACR_USERNAME" --password-stdin
        log "ACR 登录成功"
      else
        log "  可编辑 $ACR_ENV 增加 ACR_USERNAME / ACR_PASSWORD（勿提交 Git）"
      fi
    fi
  else
    log "未找到 $ACR_ENV，跳过 ACR 登录（请先 cp acr.env.example acr.env）"
  fi
fi

if [[ "$DOWNLOAD_MODEL" == "1" && -n "$DEPLOY_ROOT" ]]; then
  MODEL="$DEPLOY_ROOT/models/SenseVoiceSmall/model.pt"
  if [[ ! -f "$MODEL" ]]; then
    log "下载 ASR 模型到 $MODEL ..."
    curl -fL --retry 3 -o "$MODEL" \
      'https://modelscope.cn/models/iic/SenseVoiceSmall/resolve/master/model.pt'
  else
    log "模型已存在: $MODEL"
  fi
fi

log "完成。下一步（B 机仅 server）："
log "  cd ${REPO_DIR}/deploy/prod"
log "  cp .env.server-b.example .env.server-b   # 填 SERVER_IMAGE、MANAGER_API 等"
log "  cp server-config.example.yaml ../data/.config.yaml  # manager-api.url 改为 A 机内网"
log "  bash release-server.sh"
