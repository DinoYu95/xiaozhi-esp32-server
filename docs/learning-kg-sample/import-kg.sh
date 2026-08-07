#!/usr/bin/env bash
# 知识图谱 CSV 导入（需智控台超管 Token）
# 用法见 docs/learning-kg-sample/README.md
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SAMPLE="${ROOT}/learning-kg-sample"

BASE_URL="${BASE_URL:-http://127.0.0.1:8002/xiaozhi}"
TOKEN="${ADMIN_TOKEN:?请先 export ADMIN_TOKEN=智控台登录后的 Bearer token}"

# subject: math | chinese | english
SUBJECT="${1:-math}"

case "$SUBJECT" in
  math)
    VERSION="${VERSION_LABEL:-2026.02-math-g1g3-full}"
    GRADE_MIN="${GRADE_MIN:-1}"
    GRADE_MAX="${GRADE_MAX:-3}"
    NODES="${NODES_CSV:-${SAMPLE}/nodes-g1-g3-math-full.csv}"
    EDGES="${EDGES_CSV:-${SAMPLE}/edges-g1-g3-math-full.csv}"
    ;;
  chinese)
    VERSION="${VERSION_LABEL:-2026.02-chinese-g1-pilot}"
    GRADE_MIN="${GRADE_MIN:-1}"
    GRADE_MAX="${GRADE_MAX:-1}"
    NODES="${NODES_CSV:-${SAMPLE}/nodes-g1-chinese-pilot.csv}"
    EDGES="${EDGES_CSV:-${SAMPLE}/edges-g1-chinese-pilot.csv}"
    ;;
  english)
    VERSION="${VERSION_LABEL:-2026.02-english-g1-pilot}"
    GRADE_MIN="${GRADE_MIN:-1}"
    GRADE_MAX="${GRADE_MAX:-1}"
    NODES="${NODES_CSV:-${SAMPLE}/nodes-g1-english-pilot.csv}"
    EDGES="${EDGES_CSV:-${SAMPLE}/edges-g1-english-pilot.csv}"
    ;;
  *)
    echo "未知学科: $SUBJECT（支持 math / chinese / english）" >&2
    exit 1
    ;;
esac

auth=(-H "Authorization: Bearer ${TOKEN}")

echo "==> 创建 draft: subject=${SUBJECT} version=${VERSION} grades=${GRADE_MIN}-${GRADE_MAX}"
CREATE_RESP=$(curl -sS -X POST "${auth[@]}" \
  "${BASE_URL}/admin/learning/kg/release?versionLabel=${VERSION}&subject=${SUBJECT}&gradeMin=${GRADE_MIN}&gradeMax=${GRADE_MAX}")
echo "$CREATE_RESP"

RELEASE_ID=$(python3 -c 'import json,sys; d=json.loads(sys.argv[1]); 
assert d.get("code")==0, d; print(d["data"])' "$CREATE_RESP")

echo "==> releaseId=${RELEASE_ID} 导入 nodes: ${NODES}"
curl -sS -X POST "${auth[@]}" \
  -F "file=@${NODES}" \
  "${BASE_URL}/admin/learning/kg/release/${RELEASE_ID}/import-nodes"

echo "==> 导入 edges: ${EDGES}"
curl -sS -X POST "${auth[@]}" \
  -F "file=@${EDGES}" \
  "${BASE_URL}/admin/learning/kg/release/${RELEASE_ID}/import-edges"

echo "==> validate"
curl -sS -X POST "${auth[@]}" \
  "${BASE_URL}/admin/learning/kg/release/${RELEASE_ID}/validate"

echo "==> publish（同学科旧 published 会归档）"
curl -sS -X POST "${auth[@]}" \
  "${BASE_URL}/admin/learning/kg/release/${RELEASE_ID}/publish"

echo "==> 当前 active"
curl -sS "${auth[@]}" \
  "${BASE_URL}/admin/learning/kg/release/active?subject=${SUBJECT}"
echo
