# 生产 App 镜像（仅 prod/images 脚本使用；测试环境仍用 Dockerfile-server-standalone）
ARG BASE_IMAGE=registry.cn-beijing.aliyuncs.com/zhiban/xiaozhi-server-base:latest
FROM ${BASE_IMAGE}

COPY main/xiaozhi-server .

CMD ["python", "app.py"]
