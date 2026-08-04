本文档是开发类文档，如需部署小智服务端，[点击这里查看部署教程](../../README.md#%E9%83%A8%E7%BD%B2%E6%96%87%E6%A1%A3)

# 项目介绍

manager-api 该项目基于SpringBoot框架开发。

开发使用代码编辑器，导入项目时，选择`manager-api`文件夹作为项目目录

# 开发环境
JDK 21
Maven 3.8+
MySQL 8.0+
Redis 5.0+
Vue 3.x

# 接口文档
- **本地开发**（`spring.profiles.active=dev` 且 `XIAOZHI_OPENAPI_ENABLED=true`）：http://localhost:8002/xiaozhi/doc.html  
  默认启用 Knife4j Basic（见 `application-dev.yml`），接口清单另需在文档里 **Authorize** 填智控台登录 Token。
- **生产/公网**：默认 **关闭** doc（`application-prod.yml` 或 `XIAOZHI_OPENAPI_ENABLED=false`），勿对公网暴露 Swagger。

