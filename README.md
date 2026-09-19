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

```bash
mvn spring-boot:run
# 或先构建再运行
mvn clean package
java -jar target/conversation-compiler-0.0.1-SNAPSHOT.jar
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

## 测试

```bash
mvn test
```

当前测试覆盖 Controller、Service、Manager、Repository、SQLite 持久化、健康检查和 agent-trace 鉴权。

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
