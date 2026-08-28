# 【仅生产 ACR】xiaozhi-web App：编前端 + Java，组装到 runtime base
ARG RUNTIME_BASE=registry.cn-beijing.aliyuncs.com/zhiban/xiaozhi-web-runtime-base:latest

FROM node:18 AS web-builder
WORKDIR /app
COPY main/manager-web/package*.json ./
RUN npm install
COPY main/manager-web .
RUN npm run build

FROM maven:3.9.4-eclipse-temurin-21 AS api-builder
WORKDIR /app
COPY main/manager-api/pom.xml .
COPY main/manager-api/src ./src
RUN mvn clean package -Dmaven.test.skip=true

FROM ${RUNTIME_BASE}
COPY docs/docker/nginx.conf /etc/nginx/nginx.conf
COPY main/manager-api/src/main/resources/static/growth-portrait /usr/share/nginx/growth-portrait
COPY main/manager-api/src/main/resources/static/mind-portrait /usr/share/nginx/mind-portrait
COPY --from=web-builder /app/dist /usr/share/nginx/html
COPY --from=api-builder /app/target/xiaozhi-esp32-api.jar /app/xiaozhi-esp32-api.jar
CMD ["/start.sh"]
