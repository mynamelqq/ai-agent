# Aigent Backend

![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-6DB33F)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.2-0A7B83)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791)
![License](https://img.shields.io/badge/license-custom-lightgrey)

基于 Spring Boot 3 与 Spring AI 构建的智能体后端项目，提供 AI 对话、Agent 执行、RAG 检索增强、文件上传、图像生成、通知推送、OCR、TTS/ASR、视频理解与用户鉴权等能力。

这个仓库适合作为 AI 应用、工具调用型 Agent 平台、聊天助手后端服务的基础工程。

## Preview

- 服务端口：`8123`
- 上下文路径：`/api`
- Swagger UI：`http://localhost:8123/api/swagger-ui.html`
- Knife4j：`http://localhost:8123/api/doc.html`

## Features

- AI 对话：支持普通对话、流式输出、重新生成、标题生成
- Agent 运行：支持会话创建、消息记录、SSE 流式执行、任务取消
- RAG 检索增强：支持文档读取、查询重写、向量检索与上下文增强
- 多模态处理：支持 OCR、视频理解、图片生成、文本朗读
- 文件能力：支持上传、云存储签名、资源下载与附件管理
- 用户体系：支持注册、登录、JWT 鉴权、刷新令牌、设置管理
- 通知中心：支持单发、群发、广播、未读统计与已读状态管理
- 实时通信：支持 WebSocket 与 SSE
- API 文档：集成 Swagger / Knife4j

## Tech Stack

- Java 21
- Spring Boot 3.4.4
- Spring AI 1.1.2
- MyBatis-Plus
- MySQL
- PostgreSQL + pgvector
- RabbitMQ
- WebSocket / SSE
- Tencent COS
- Maven
- Docker

## Architecture

```text
Client / Frontend
       |
       v
Spring Boot REST API (/api)
       |
       +-- AI Chat Controller
       +-- Agent Controller
       +-- User/Auth Controller
       +-- Upload Controller
       +-- Notification Controller
       +-- Image Controller
       |
       v
Service Layer / Agent Runtime / Tool Registry / RAG Pipeline
       |
       +-- MySQL
       +-- PostgreSQL + pgvector
       +-- RabbitMQ
       +-- Tencent COS
       +-- LLM Providers / MCP Tools
```

## Project Structure

```text
src/main/java/com/ai/aigent
├─ advisor        # 对话记忆与向量增强 advisor
├─ agent          # Agent 核心实现与运行状态模型
├─ app            # AI 调用编排入口
├─ config         # 数据源、WebSocket、CORS、JSON、Agent 运行配置
├─ consumer       # RabbitMQ 消费逻辑
├─ controller     # REST API 控制器
├─ entity         # DTO / VO / Domain 实体
├─ exception      # 全局异常与业务异常
├─ interceptor    # JWT 鉴权过滤器
├─ mapper         # MyBatis Mapper
├─ rag            # RAG、向量库、文档读取与查询增强
├─ service        # 业务服务层
├─ tools          # 可供 Agent 调用的工具集合
└─ utils          # OCR、JWT、PDF、音频等工具类
```

## Main APIs

### AI Chat

根路径：`/api/ai`

- `POST /api/ai/chat`
- `POST /api/ai/chat/async/`
- `POST /api/ai/chat/regenerate`
- `POST /api/ai/chat/cancel`
- `GET /api/ai/chat/title`
- `POST /api/ai/ocr`
- `POST /api/ai/read`
- `POST /api/ai/stop`
- `POST /api/ai/video/understand`

### Agent

根路径：`/api/agent`

- `POST /api/agent/session`
- `GET /api/agent/sessions`
- `GET /api/agent/messages`
- `POST /api/agent/run`
- `POST /api/agent/cancel`
- `POST /api/agent/delete`

### User & Auth

根路径：`/api/user`

- `POST /api/user/register`
- `POST /api/user/login`
- `POST /api/user/refresh`
- `POST /api/user/logout`
- `POST /api/user/password/update`
- `POST /api/user/password/forgot/update`
- `GET /api/user/setting/{userId}`
- `POST /api/user/setting/update`

### Upload

根路径：`/api/upload`

- `POST /api/upload/file`
- `GET /api/upload/sign`
- `GET /api/upload/sign/avatar`
- `POST /api/upload/confirm`

### Notification

根路径：`/api/notifications`

- `POST /api/notifications/send/user`
- `POST /api/notifications/send/users`
- `POST /api/notifications/broadcast`
- `GET /api/notifications`
- `POST /api/notifications/{id}/read`
- `POST /api/notifications/read-all`
- `GET /api/notifications/unread-count`

### Image

根路径：`/api/image`

- `GET /api/image/list`
- `POST /api/image/generate`

## Authentication

项目使用 JWT 进行接口鉴权，过滤器位于：

- `src/main/java/com/ai/aigent/interceptor/jwtHandler.java`

调用受保护接口时，请在请求头中传入：

```http
Authorization: Bearer <your-access-token>
```

默认白名单接口包括但不限于：

- 注册、登录、刷新令牌
- 忘记密码重置
- OCR、朗读相关接口
- OpenAPI / Knife4j 文档接口
- 部分 WebSocket 接口

## Quick Start

### 1. Clone

```bash
git clone https://github.com/mynamelqq/aigent.git
cd aigent
```

### 2. Prepare Dependencies

启动前建议准备以下基础依赖：

- JDK 21
- Maven 3.9+
- MySQL 8
- PostgreSQL 15+ 并启用 `pgvector`
- RabbitMQ
- 可用的大模型 API Key / 服务端点
- 腾讯云 COS 配置

### 3. Copy Example Config

项目根目录已提供示例配置文件：

- `application-local.example.yml`

你可以基于它整理自己的本地配置，再同步到 `src/main/resources/application.yml` 或通过环境变量注入。

### 4. Run in Development

Windows:

```bash
mvnw.cmd spring-boot:run
```

macOS / Linux:

```bash
./mvnw spring-boot:run
```

### 5. Package

Windows:

```bash
mvnw.cmd -DskipTests package
```

macOS / Linux:

```bash
./mvnw -DskipTests package
```

启动后访问：

- `http://localhost:8123/api/swagger-ui.html`

## Docker

项目已包含 `Dockerfile`，可直接构建镜像：

```bash
docker build -t aigent-backend .
docker run -p 8123:8123 --name aigent-backend aigent-backend
```

说明：

- Dockerfile 默认使用 `SPRING_PROFILES_ACTIVE=prod`
- 生产环境建议通过环境变量或挂载配置文件覆盖敏感配置

## Example Requests

### 1. User Login

```bash
curl -X POST "http://localhost:8123/api/user/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "demo@example.com",
    "password": "123456"
  }'
```

### 2. Stream Chat

```bash
curl -N -X POST "http://localhost:8123/api/ai/chat/async/" \
  -H "Content-Type: application/json" \
  -d '{
    "chatId": "chat_001",
    "message": "帮我总结这段文档",
    "token": "Bearer <access-token>"
  }'
```

### 3. Create Agent Session

```bash
curl -X POST "http://localhost:8123/api/agent/session?message=帮我生成一个部署方案&SessionId=session_001" \
  -H "Authorization: Bearer <access-token>"
```

### 4. Run Agent

```bash
curl -N -X POST "http://localhost:8123/api/agent/run" \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "session_001",
    "message": "分析当前项目并给出优化建议",
    "token": "Bearer <access-token>",
    "attachments": []
  }'
```

### 5. Upload File

```bash
curl -X POST "http://localhost:8123/api/upload/file" \
  -H "Authorization: Bearer <access-token>" \
  -F "file=@./example.pdf"
```

## Configuration

当前项目主配置文件：

- `src/main/resources/application.yml`
- `src/main/resources/application-prod.yml`

配置内容涉及：

- MySQL 数据源
- PostgreSQL 数据源
- RabbitMQ
- Spring AI / MCP
- Swagger / Knife4j
- 端口与上下文路径
- Agent 模型与运行时参数

建议将敏感信息改为环境变量，例如：

```yaml
spring:
  datasource:
    mysql:
      jdbc-url: ${MYSQL_URL}
      username: ${MYSQL_USERNAME}
      password: ${MYSQL_PASSWORD}
    postgresql:
      jdbc-url: ${POSTGRES_URL}
      username: ${POSTGRES_USERNAME}
      password: ${POSTGRES_PASSWORD}
  rabbitmq:
    host: ${RABBITMQ_HOST}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME}
    password: ${RABBITMQ_PASSWORD}
```

## API Documentation

项目已集成：

- Swagger UI
- Knife4j

访问地址：

- `http://localhost:8123/api/swagger-ui.html`
- `http://localhost:8123/api/doc.html`
- `http://localhost:8123/api/v3/api-docs`

## Testing

macOS / Linux:

```bash
./mvnw test
```

Windows:

```bash
mvnw.cmd test
```

仓库中已经包含聊天、工具调用、RAG、Web 搜索、网页抓取等测试类，可作为联调参考。

## Roadmap

- 将敏感配置彻底迁移到环境变量或配置中心
- 增加数据库初始化脚本与示例数据
- 补充 `docker-compose.yml`
- 增加请求响应样例与错误码说明
- 为 Agent 工具调用链补充更完整的文档

## License

如需开源发布，建议补充明确的 License 文件，例如 `MIT`、`Apache-2.0` 或团队内部协议。
