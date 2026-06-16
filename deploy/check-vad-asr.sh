#!/bin/bash
# 在 deploy 目录执行：bash check-vad-asr.sh
# 检查 xiaozhi-server 容器内 VAD/ASR 模型与依赖是否完整

set -e
CONTAINER="${1:-xiaozhi-server}"

echo "========== 1. 容器是否存在 =========="
docker ps --filter "name=${CONTAINER}" --format '{{.Names}} {{.Status}}'

echo ""
echo "========== 2. Silero VAD 模型文件（应在镜像内，不依赖 volume）=========="
docker exec "${CONTAINER}" ls -lh \
  /opt/xiaozhi-esp32-server/models/snakers4_silero-vad/src/silero_vad/data/ 2>/dev/null \
  || echo "FAIL: 目录不存在"

echo ""
echo "========== 3. FunASR SenseVoice 模型（依赖宿主机 volume 挂载）=========="
docker exec "${CONTAINER}" ls -lh \
  /opt/xiaozhi-esp32-server/models/SenseVoiceSmall/model.pt 2>/dev/null \
  || echo "FAIL: model.pt 不存在（检查 deploy/models/SenseVoiceSmall/model.pt）"

echo ""
echo "========== 4. 宿主机挂载的 model.pt =========="
ls -lh ./models/SenseVoiceSmall/model.pt 2>/dev/null || echo "FAIL: 宿主机 deploy/models/SenseVoiceSmall/model.pt 不存在"

echo ""
echo "========== 5. 系统 libopus =========="
docker exec "${CONTAINER}" sh -c "ldconfig -p 2>/dev/null | grep opus || dpkg -l libopus0 2>/dev/null | tail -1"

echo ""
echo "========== 6. Python 依赖（opuslib / torch）=========="
docker exec "${CONTAINER}" python -c "
import torch, opuslib_next
print('torch', torch.__version__)
print('opuslib_next OK')
"

echo ""
echo "========== 7. 启动日志中的 VAD/ASR 初始化 =========="
docker logs "${CONTAINER}" 2>&1 | grep -E "初始化组件: (vad|asr)|SileroVAD|实例化组件失败" | tail -10

echo ""
echo "========== 8. 运行时加载的 VAD 配置 =========="
docker exec "${CONTAINER}" python -c "
from config.settings import load_config
c = load_config()
print('VAD module:', c.get('selected_module',{}).get('VAD'))
print('VAD config:', c.get('VAD',{}).get('VAD_SileroVAD'))
print('ASR module:', c.get('selected_module',{}).get('ASR'))
print('ASR config:', c.get('ASR',{}).get('ASR_FunASR'))
"

echo ""
echo "========== 9. test_page 版本（镜像内，与宿主机 python http.server 无关）=========="
docker exec "${CONTAINER}" grep -o 'v=[0-9]*' /opt/xiaozhi-esp32-server/test/js/app.js | head -3

echo ""
echo "========== 完成 =========="
