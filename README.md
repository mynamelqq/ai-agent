# Aigent Backend

![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-6DB33F)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.2-0A7B83)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791)
![License](https://img.shields.io/badge/license-custom-lightgrey)

基于 Java 21、Spring Boot 3.4.4 与 Spring AI 1.1.2 构建的 AI 应用后端。项目同时覆盖聊天助手与工具调用型 Agent：从流式交互、会话记忆和检索增强，到代码执行、文件交付、用户体系及异步任务，串起一条可以继续扩展的业务链路。

**一句话认识项目：** 用户提出任务后，Agent 可以分步推理并调用网页搜索、Python、终端或文件工具；执行过程以 SSE 返回，生成的文件通过 COS 提供下载链接。普通聊天则提供独立的流式对话、历史摘要与多模态能力。

> 本仓库是后端工程。部分功能依赖模型服务、Docker、数据库、消息队列和 COS；文中的能力以源码实现为依据，不代表在任意环境下开箱即用。

## 项目亮点

| 亮点 | 实现方式 | 解决的问题 |
| --- | --- | --- |
| 可执行的 ReAct Agent | `BaseAgent` / `ReActAgent` / `ToolCallAgent` 分层，模型流式决定工具调用，执行结果回填上下文 | 让复杂任务能够“思考 → 调用工具 → 观察结果 → 继续处理”，不止生成一段文本 |
| 会话级代码沙箱 | Python 与 Shell 工具共用会话 Docker 容器；附件只读挂载，工作区可写，容器限制网络、用户和资源 | 将模型生成的代码与 Java 服务进程分开，并让多步任务能通过工作区文件衔接 |
| 可追踪的运行过程 | Agent 按事件推送推理文本、工具调用和工具结果；消息按 `sessionId`、`runId` 关联持久化 | 前端能够展示过程，用户也能回看某一轮任务发生了什么 |
| 长会话上下文管理 | 模型上下文与 UI 消息分开存储，保留工具调用及响应；超过阈值时尝试压缩历史 | 减少长任务因上下文增长而难以继续的问题，同时保留可展示的过程记录 |
| 流式聊天续接 | 聊天事件带递增序号写入 Redis，提供快照与重新订阅接口 | 页面重连后可以按序号补收缓存中的事件 |
| 异步历史摘要 | MySQL 批次记录、RabbitMQ 发布与消费，配合重试和死信队列 | 将摘要生成移出用户请求链路，并为失败处理保留入口 |
| 多工具与多模态入口 | 内置网页搜索/抓取、图片生成、文档处理与上传工具，支持 Spring AI MCP 客户端 | 便于按任务组合能力，也给后续接入工具留下扩展点 |

### Agent 一次任务如何完成

```text
创建会话 / 上传附件
        ↓
POST /api/agent/run（sessionId + runId + message）
        ↓
校验用户、会话归属与运行状态 → 加载历史上下文和附件
        ↓
模型流式输出 → 判断是否需要工具 → 调用工具 → 保存结果 → 下一步
        ↓
SSE 推送过程事件；消息与模型上下文分别落库
        ↓
生成文件时，经上传工具发布产物并返回下载链接
```

这里的 `runId` 标识会话中的一轮执行：接口拒绝同一会话内重复使用的 `runId`，工具消息与上下文也能按轮次关联。Agent 还设置了最大执行步数，避免无界循环；用户可以通过取消接口中止运行。

### 值得看的工程设计

- **工具执行边界：** `SandboxManager` 在执行前校验会话归属，首次调用时创建容器，同一会话复用容器。Python 与 Shell 共享 `/workspace` 文件，但每次调用都是独立进程；容器设置非 root 用户、只读根文件系统、无网络以及 CPU、内存、PID 限制。
- **产物交付边界：** Agent 在工作区生成文件后，由 Java 侧读取受控文件快照并上传 COS。上传工具检查相对路径与文件大小，COS 凭据留在服务端，而不是传给执行容器。
- **上下文与展示解耦：** `agent_context` 保存供模型继续推理的消息和工具响应；`agent_message` 保存面向界面的过程记录。长上下文压缩在模型视图上进行，不需要把前端展示完全绑定到模型输入格式。
- **异步链路：** 聊天历史摘要通过数据库批次、发布器和 RabbitMQ 消费者处理。发布确认、重试与死信队列体现了对消息丢失和处理失败的考虑。

### 三条值得展开的实现链路

**1. Agent 的运行状态与工具协议。** `AgentController` 按会话登记正在运行的 Agent，并拒绝同一会话的并发执行；每轮由客户端提供正整数 `runId`，用于关联用户消息、模型消息和工具结果。`ToolCallAgent` 聚合模型流中的工具调用后再执行，关闭 Spring AI 的内部自动工具执行，由自己控制调用、事件推送和持久化。构造工具上下文时写入服务端的会话标识（`chatId`）、`userId` 与沙箱运行令牌，工具据此校验本轮权限。模型上下文保留工具调用和响应的结构，页面记录另存为可展示的事件与消息。

**2. 聊天的断线续接。** `SpringAiCode` 为流式事件分配递增 `seq`，同时推送给当前连接并写入 Redis；`/api/ai/snapshot` 返回当前文本、推理内容、序号和状态；`/api/ai/resubscribe` 根据 `lastSeq` 读取之后的事件，在 `done`、`error` 或 `cancelled` 时结束。缓存有效期为 30 分钟，因此这是短时重连能力，历史消息仍要从持久化接口读取。

**3. 摘要任务的可靠处理。** 聊天消息攒满 10 条后，`SummaryBatchService` 在事务中创建批次并预留消息；`SummaryPublisher` 扫描未发布批次，收到 RabbitMQ 发布确认后才记录发布时间。消费者先领取带租约的批次，再调用模型；摘要追加、消息归档与批次成功状态在一个事务中提交。重复投递会检查批次状态，失败可延迟重试，达到次数上限后进入死信队列。同一会话的后续批次等待前一批完成，避免摘要顺序错乱。

### 从哪里开始读源码

| 想了解的问题 | 建议入口 |
| --- | --- |
| 一次 Agent 请求如何进入运行循环？ | [`AgentController`](src/main/java/com/ai/aigent/controller/AgentController.java) → [`Manus`](src/main/java/com/ai/aigent/agent/Manus.java) → [`ToolCallAgent`](src/main/java/com/ai/aigent/agent/ToolCallAgent.java) |
| 模型生成的代码在哪里执行、如何交付文件？ | [`ToolsRegistration`](src/main/java/com/ai/aigent/tools/ToolsRegistration.java) → [`SandboxManager`](src/main/java/com/ai/aigent/sandbox/SandboxManager.java) → [`ArtifactSnapshot`](src/main/java/com/ai/aigent/sandbox/ArtifactSnapshot.java) |
| 为什么断线后还能补收聊天内容？ | [`AiController`](src/main/java/com/ai/aigent/controller/AiController.java) → [`SpringAiCode`](src/main/java/com/ai/aigent/app/SpringAiCode.java) → [`CachedService`](src/main/java/com/ai/aigent/service/impl/CachedService.java) |
| 摘要如何避免重复处理和乱序？ | [`SummaryBatchService`](src/main/java/com/ai/aigent/service/impl/SummaryBatchService.java) → [`SummaryPublisher`](src/main/java/com/ai/aigent/consumer/SummaryPublisher.java) → [`RabbitConsumer`](src/main/java/com/ai/aigent/consumer/RabbitConsumer.java) |

面试中可以按上面三条链路介绍：先说明业务问题，再展示关键状态与数据流，最后解释为什么需要会话互斥、运行令牌、事件序号和消息批次。

### 适用范围与当前边界

沙箱依赖可访问的 Docker daemon 与预构建镜像，目录权限、模型密钥、COS 和数据库需按部署环境配置。当前代码把容器内存限制为 128 MiB，单次执行等待输出最长 100 秒；这两个值应以 [`SandboxManager`](src/main/java/com/ai/aigent/sandbox/SandboxManager.java) 为准。容器隔离不能等同于虚拟机级隔离，部署到公网前仍应评估资源配额与恶意代码风险。[沙箱实现报告](SANDBOX_IMPLEMENTATION_REPORT.md) 与 [上下文设计](AGENT_CONTEXT_PERSISTENCE_DESIGN.md) 记录了实现过程，部分历史参数可能与当前代码不同。

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
- Redis
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
- `GET /api/ai/snapshot`
- `GET /api/ai/resubscribe`
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

应用配置统一使用 `application.yml` 和可选的本地 `application-local.yml`，不需要额外 Spring Profile。启动前准备：

- JDK 21
- Maven 3.9+
- MySQL 8
- PostgreSQL 15+ 并启用 `pgvector`
- Redis
- RabbitMQ

PostgreSQL 默认连接 `localhost:5432/postgres`，可通过 `POSTGRES_URL`、`POSTGRES_USERNAME`、`POSTGRES_PASSWORD` 覆盖。DashScope 向量嵌入和联网搜索需要在本地 `application-local.yml` 或环境变量中配置对应密钥。Agent 代码执行还需要 Docker daemon 与沙箱镜像；启动前可运行 `docker build -t aigent-python:1 sandbox` 构建镜像。

模型请求通过 `application-local.yml` 中的 OpenAI 兼容接口配置。已有用户的聊天模型名保存在数据库的 `user_setting.chat_model` 中，可在用户设置中改成接口支持的模型名。标题生成也使用这个接口。COS 上传、签名和附件读取默认启用，需在本地配置中提供有效的 COS 凭证和存储桶。

PowerShell 示例：

```powershell
$env:POSTGRES_URL = 'jdbc:postgresql://localhost:5432/postgres'
$env:POSTGRES_USERNAME = 'postgres'
$env:POSTGRES_PASSWORD = 'your-postgres-password'
.\mvnw.cmd spring-boot:run
```


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
    "runId": 1001,
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
