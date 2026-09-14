#!/usr/bin/env bash
# 在本机 ~/prjs 目录结构下运行，自动修改 teaching-research 生产 compose 的 ports / network
#
# 用法（在 xiaozhi-esp32-server 仓库根目录）：
#   bash deploy/prod/scripts/configure-teaching-edge-ports.sh
#
# 或指定教研仓库路径：
#   TEACHING_RESEARCH_ROOT=~/prjs/teaching-research bash deploy/prod/scripts/configure-teaching-edge-ports.sh

set -euo pipefail

XIAOZHI_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
TR_ROOT="${TEACHING_RESEARCH_ROOT:-$(dirname "$XIAOZHI_ROOT")/teaching-research}"
COMPOSE_FILE="${TR_COMPOSE_FILE:-$TR_ROOT/deploy/prod/docker-compose.yml}"
ENV_EXAMPLE="$TR_ROOT/deploy/prod/.env.example"
ENV_PROD="$TR_ROOT/deploy/prod/.env.prod"

TEACHING_BIND="${TEACHING_BIND:-127.0.0.1}"
TEACHING_PORT="${TEACHING_PORT:-8100}"
CONTAINER_HTTP_PORT="${TEACHING_CONTAINER_PORT:-80}"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "错误：找不到 teaching-research compose 文件："
  echo "  $COMPOSE_FILE"
  echo "请设置 TEACHING_RESEARCH_ROOT 或 TEACHING_COMPOSE_FILE 后重试。"
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "错误：需要 python3"
  exit 1
fi

BACKUP="${COMPOSE_FILE}.bak.$(date +%Y%m%d%H%M%S)"
cp "$COMPOSE_FILE" "$BACKUP"
echo "已备份：$BACKUP"

python3 - "$COMPOSE_FILE" "$TEACHING_BIND" "$TEACHING_PORT" "$CONTAINER_HTTP_PORT" <<'PY'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
bind = sys.argv[2]
host_port = sys.argv[3]
container_port = sys.argv[4]
text = path.read_text(encoding="utf-8")

# 匹配 ports 下形如 "8100:80" / "0.0.0.0:8100:80" / "127.0.0.1:8100:80" 的行（容器端口为 container_port）
port_line_re = re.compile(
    rf'^(\s*-\s*)"(?:[\d.]+:)?(\d+):{container_port}"\s*$',
    re.MULTILINE,
)
new_port = f'\\1"{bind}:{host_port}:{container_port}"'

new_text, n_ports = port_line_re.subn(new_port, text)
if n_ports == 0:
    # 尝试更宽松：任意 host:container 且 container 为 80/8080
    alt_re = re.compile(
        r'^(\s*-\s*)"(?:[\d.]+:)?(\d+):(80|8080)"\s*$',
        re.MULTILINE,
    )
    new_text, n_ports = alt_re.subn(
        lambda m: f'{m.group(1)}"{bind}:{host_port}:{m.group(3)}"',
        text,
    )

if n_ports == 0:
    print("警告：未自动匹配到 ports 行，请手动改 teaching-research compose 中 Nginx 服务的 ports。")
    print(f'  目标："{bind}:{host_port}:{container_port}"')
else:
    print(f"已更新 {n_ports} 处 ports 映射 -> {bind}:{host_port}:{container_port}")
    text = new_text

# 确保接入 zhiban-prod 外部网络
if "zhiban-prod" not in text:
    net_block = """
networks:
  prod:
    name: zhiban-prod
    external: true
"""
    if re.search(r"^networks:\s*$", text, re.MULTILINE):
        if "external: true" not in text:
            text = re.sub(
                r"(^networks:\s*\n(?:  \w+:\s*\n(?:    .+\n)*))",
                r"\1  prod:\n    name: zhiban-prod\n    external: true\n",
                text,
                count=1,
                flags=re.MULTILINE,
            )
    else:
        text = text.rstrip() + net_block + "\n"
    print("已追加/更新 networks.prod -> zhiban-prod (external)")

path.write_text(text, encoding="utf-8")
PY

append_env_var() {
  local file="$1"
  local key="$2"
  local val="$3"
  [[ -f "$file" ]] || return 0
  if grep -q "^${key}=" "$file" 2>/dev/null; then
    sed -i "s|^${key}=.*|${key}=${val}|" "$file"
  else
    printf '\n%s=%s\n' "$key" "$val" >> "$file"
  fi
}

for envf in "$ENV_EXAMPLE" "$ENV_PROD"; do
  if [[ -f "$envf" ]]; then
    append_env_var "$envf" "TEACHING_BIND" "$TEACHING_BIND"
    append_env_var "$envf" "TEACHING_PORT" "$TEACHING_PORT"
    echo "已更新 $envf"
  fi
done

echo ""
echo "完成。请在 teaching-research 目录执行："
echo "  cd $TR_ROOT/deploy/prod && docker compose --env-file .env.prod up -d"
echo ""
echo "edge-nginx 中 teach.newgravtech.com 应反代到 127.0.0.1:${TEACHING_PORT}"
