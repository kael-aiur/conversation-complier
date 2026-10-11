# conversation-compiler

Conversation Compiler 是一个面向 Agent 会话的采集、增量编译和知识管理服务。

当前项目包含：

- Spring Boot 3.3 + Java 21 后端
- Vue 3 + Vite + Element Plus 管理控制台
- agent-trace 兼容采集接口
- SQLite 会话与事件存储
- 会话列表和事件详情查询

## 项目结构

- `src/main/java`：Spring Boot 后端源码
- `src/main/resources/schema.sql`：SQLite 初始化脚本
- `frontend`：Vue 3 + Vite 前端源码，使用 pnpm
- `src/test/java`：后端单元测试和集成测试

## 前端开发

```bash
cd frontend
pnpm install
pnpm dev
```

前端开发服务器默认运行在 `http://127.0.0.1:5173`，`/api` 请求代理到 Spring Boot 的 `http://127.0.0.1:8080`。

## 前端构建

```bash
cd frontend
pnpm install

# 构建到 Spring Boot 页面目录
pnpm build:spring

# 构建到前端独立部署目录
pnpm build:standalone
```

`pnpm build` 默认等同于 `pnpm build:standalone`。

## 后端运行

模型供应商的 API Key 会使用 AES-GCM 加密保存，因此启动后端时必须配置加密密钥。该密钥不要提交到 Git，也不要与模型 API Key 混用。

本地开发可以使用一个仅保存在本机环境中的随机值：

```bash
export CONVERSATION_COMPILER_SECRET_KEY="$(openssl rand -hex 32)"
mvn spring-boot:run
```

生产环境必须使用稳定且安全保存的密钥；更换该密钥后，已保存的模型供应商 API Key 将无法解密。

如果没有配置该密钥，服务仍可启动并浏览会话，但保存模型供应商时会返回 `503`，而不是 `500`。

```bash
# 或先构建再运行
CONVERSATION_COMPILER_SECRET_KEY="<secret>" mvn clean package
CONVERSATION_COMPILER_SECRET_KEY="<secret>" java -jar target/conversation-compiler-0.0.1-SNAPSHOT.jar
```

SQLite 数据库默认保存到：

```text
./data/conversation-compiler.db
```

可以通过环境变量指定其他路径：

```bash
CONVERSATION_COMPILER_DB=/path/to/conversation-compiler.db mvn spring-boot:run
```

## API

### 健康检查

```http
GET /health
GET /api/health
```

### agent-trace 兼容接口

```http
POST /sessions
Content-Type: application/json
```

```json
{
  "session_id": "session001",
  "started_at": 1720000000,
  "agent_name": "openai-codex",
  "command": "...",
  "workspace_id": "project-a"
}
```

```http
POST /events
Content-Type: application/x-ndjson
```

每行一个事件：

```json
{"event_type":"user_prompt","event_id":"evt-1","session_id":"session001","timestamp":1720000000,"data":{"prompt":"Hello"}}
```

服务会自动创建缺少元数据的会话，并按照 `session_id + event_id` 做轻量去重。

如果配置了 `AGENT_TRACE_AUTH_KEY`，采集接口需要携带：

```http
Authorization: Bearer <configured-key>
```

### 控制台查询接口

```http
GET /api/v1/conversations
GET /api/v1/conversations/{sessionId}
GET /api/v1/conversations/{sessionId}/events
```

## 知识整理输入

整理器不强校验历史对话完整性。它按用户消息边界选择一批已采集的事件，并转换成固定的可读文本模板（不是需要解析的严格 YAML）：

```text
# 对话记录
用户: |-
  用户消息
Agent: |-
  助手回复
ToolCall: |-
  工具：工具名称
  参数：参数内容
ToolResult: |-
  工具：工具名称
  结果：返回内容
```

这段文本作为一条 `user` 消息发送给知识整理 Agent。用户消息中明确说明记录可能缺失，只从已有内容提取知识，不补全缺失消息、不重放历史工具调用。Agent 自己调用 MCP 的工具消息历史仍按模型协议正确保留，与被整理的历史文本相互独立。

- `CONVERSATION_COMPILER_MAX_TURNS_PER_RUN`：默认每批最多 20 个用户轮次；缺少用户消息的开头片段也可以处理。
- `CONVERSATION_COMPILER_MAX_CHARS_PER_RUN`：默认对话模板最多 200,000 字符；超出的后续轮次留给下一批。单轮过长时可以按事件拆分，单条超长事件会明确标注内容截断，避免永远卡住。
- 缺少助手回复或工具结果不会使整理任务失败；空事件输入、模型调用失败仍会报告错误。知识库工具调用异常会作为工具失败结果返回模型，由模型自行纠正。
- 完成一批后只推进该批实际覆盖的事件版本；尚有剩余事件时保持待整理状态。
- `CONVERSATION_COMPILER_MODEL_REQUEST_TIMEOUT_SECONDS`：每次模型请求的超时秒数，默认 600（10 分钟）；与整次整理任务的 `CONVERSATION_COMPILER_TASK_TIMEOUT_SECONDS`（默认 1800，即 30 分钟）分开控制。
- 模型尝试和执行过程中的错误保留脱敏后的异常原因链，便于区分 SDK 的 `Request failed`、连接错误和实际超时。

## 工具错误恢复与执行诊断

- 所有已暴露的 MCP 工具和本地 `compile_result` 都使用统一执行边界。参数解析、权限、限流、连接、超时、写入冲突等普通执行异常会转换成带 `toolCallFailed=true` 的脱敏工具结果，保留原工具调用 ID 对应的结果消息，由模型决定如何纠正；程序不自动重放失败写入。
- 未知或不可用工具名只返回失败信息，不会解析或执行未暴露的真实工具。错误信息不能扩大工具权限或覆盖系统规则。
- 无效的 `compile_result` 参数或校验失败也会返回给模型继续修正；只有合法报告才保持直接结束工具循环的行为。
- 任务取消、中断以及 JVM 严重错误不会作为可恢复工具结果吞掉；整次任务超时保护仍有效。远端写入失败可能已有副作用，模型在重试写入前必须先搜索或读取核对，不能原样无限重试。
- 每轮增加独立的 `model_diagnostics` 过程记录：响应 ID、模型、结束原因、SDK 映射的 Token 用量、请求中的输出预算、实际工具参数长度、JSON 完整性和追踪预览是否截断。未设置输出预算不等于输出无限制，SDK 零用量也不能证明上游实际没有消耗。
- 每次写入工具正常返回都会立即追加独立的 `knowledge_write` 响应记录，即使后续工具失败或未上报 `compile_result` 也会保留。它只表示工具返回，并不自动认定远端写入成功，也不会伪造知识条目或推进已整理版本。抽屉提示核对写入响应，避免把“上报条目为 0”误读成“没有发生写入”。
- 过程正文仍最多保留 12,000 个脱敏后的字符，并明确标注原始长度；该截断只作用于追踪预览，不改变发给模型或工具的实际内容。已有历史记录不会被回填新诊断。
- 整理记录列表与详情 API 返回 `warnings` 数组，从既有 `result_json.warnings` 读取并脱敏；详情抽屉在执行过程前逐条展示。缺失或损坏的历史结果返回空数组，带警告完成的记录会明确提示明细不可用。此功能只补齐展示，不重新分类、去重或清理警告，不重算状态，也不改写历史结果。

## 测试

```bash
mvn test
```

当前测试覆盖 Controller、Service、Manager、Repository、SQLite 持久化、健康检查和 agent-trace 鉴权。

编译默认显示弃用及待移除 API 警告，并通过 `-Werror` 将编译警告视为错误；应使用替代 API，而不是添加弃用警告抑制。测试 JVM 在启动时显式加载 Mockito agent，避免运行时动态附加；仅测试 JVM 关闭 CDS，不影响生产运行参数。

## 设计方向

```text
agent-trace
    ↓
Collector API
    ↓
SQLite + 原始事件
    ↓
空闲检测与增量编译
    ↓
本地知识管理
    ↓
后续接入 LLMWikiNG MCP
```

当前版本优先验证采集、持久化和控制台查询链路，编译 Worker、知识 Sink 和 LLMWikiNG 接入将在后续阶段实现。

## Docker 镜像

项目提供多阶段 `Dockerfile`，会自动构建 Vue 前端、Spring Boot JAR，并生成运行时镜像。

本地构建：

```bash
docker build -t conversation-complier:local .
docker run --rm -p 8080:8080 -v conversation-compiler-data:/app/data conversation-complier:local
```

### GitHub Actions 推送到 Docker Hub

`.github/workflows/docker-publish.yml` 会在 `main` 分支收到提交时自动构建并推送镜像。合并 Pull Request 到 `main` 也会产生一次 `push` 事件，因此不会重复推送。

在 GitHub 仓库的 **Settings → Secrets and variables → Actions** 中配置：

- `DOCKERHUB_USERNAME`：Docker Hub 用户名
- `DOCKERHUB_TOKEN`：Docker Hub Access Token，不要使用 Docker Hub 登录密码

工作流默认推送到：

```text
<DOCKERHUB_USERNAME>/conversation-complier:latest
<DOCKERHUB_USERNAME>/conversation-complier:sha-<commit>
```

Docker Hub 中需要预先创建名为 `conversation-complier` 的镜像仓库，并确保 Access Token 具备推送权限。
