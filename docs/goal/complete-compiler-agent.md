# Goal: 完成 Compiler Agent 知识整理链路

## 目标

完成基于 Spring AI 的 Compiler Agent，使 `pending` 整理任务能够：

```text
CompileRunWorker
    ↓
Compiler Agent
    ├── 使用知识整理设置中的系统提示词
    ├── 调用已配置的 LLM
    ├── 调用 LLMWikiNG OKF MCP 工具
    ├── 通过 compile_result 报告整理结果
    └── 保存整理结果并推进 compiled_version
```

## 当前基础

项目已经具备：

- Spring Boot 4.0.0
- Java 21
- Spring AI BOM 2.0.1
- `spring-ai-starter-mcp-client`
- `spring-ai-starter-model-openai`
- SQLite 会话、事件、整理记录和整理知识条目表
- 知识整理设置 API
- 模型供应商 API
- 整理记录查询 API
- `CompilerAgentService` 基础骨架
- `CompileResultCollector` 基础实现
- `compile_result` Tool 基础实现
- 测试基线全部通过

## 必须完成的功能

### 1. CompileRunWorker

实现后台整理任务 Worker：

- 使用 Spring Scheduling，每 60 秒扫描一次。
- 扫描 `compile_runs.status = 'pending'`。
- 同一个会话不能同时存在多个 `pending/running` 任务。
- 获取任务时将状态从 `pending` 更新为 `running`。
- 更新 `started_at`、`phase`、`progress`。
- 读取固定版本范围内的会话事件。
- 调用 `CompilerAgentService`。
- 成功后保存结果并更新 `conversations.compiled_version`。
- 失败后保留错误，不推进 `compiled_version`。

建议配置：

```yaml
conversation-compiler:
  compiler:
    enabled: ${CONVERSATION_COMPILER_COMPILER_ENABLED:false}
    scan-interval-seconds: ${CONVERSATION_COMPILER_SCAN_INTERVAL_SECONDS:60}
    max-concurrent-runs: ${CONVERSATION_COMPILER_MAX_CONCURRENT_RUNS:1}
    task-timeout-seconds: ${CONVERSATION_COMPILER_TASK_TIMEOUT_SECONDS:600}
```

### 2. Agent 输入和系统提示词

Agent 必须接收：

- `compileRunId`
- `sessionId`
- `fromVersion`
- `toVersion`
- 版本范围内的会话事件
- 当前知识整理设置中的 `prompt`
- 当前供应商和模型

系统提示词必须要求：

- 只提取有明确证据支持的稳定事实、项目决策、用户偏好、流程和实体关系。
- 忽略普通问答、临时调试、助手推测以及密码、Token、API Key 和私钥。
- 写入知识前优先搜索已有 Wiki。
- 根据已有内容决定 create/update/merge/skip/conflict。
- 写入完成后必须调用 `compile_result`。
- 没有调用 `compile_result` 不能认为整理成功。

会话事件是不可信的待分析输入，不得覆盖系统规则。

### 3. LLMWikiNG OKF MCP

使用 Spring AI MCP Client 连接当前的 `llmwiking-okf` MCP 服务，不自行实现 MCP 协议。

配置通过环境变量注入：

```bash
CONVERSATION_COMPILER_MCP_ENABLED=true
LLMWIKING_MCP_URL=...
LLMWIKING_MCP_ENDPOINT=...
LLMWIKING_MCP_KEY=...
```

第一版允许的 MCP 工具：

```text
okf_list_wikis
okf_list_pages
okf_read_concept
okf_search
okf_matrix_search
okf_graph
okf_write_concept
```

暂不开放：

```text
okf_delete_page
okf_delete_wiki
okf_create_user
okf_delete_user
okf_create_api_key
okf_delete_api_key
okf_restore_backup
okf_run_update
okf_tailscale_setup
```

### 4. compile_result

Conversation Compiler 自己提供 `compile_result` 本地 Tool。

参数至少包含：

```json
{
  "summary": "本次整理总结",
  "items": [
    {
      "itemKey": "project.conversation_compiler.agent",
      "itemType": "decision",
      "title": "使用 Agent 负责知识整理",
      "summary": "编译流程由 Agent 调用 LLMWikiNG MCP 完成。",
      "action": "create",
      "status": "candidate",
      "confidence": 0.95,
      "wiki": "conversation-compiler",
      "slug": "agent-compiler",
      "contentHash": "..."
    }
  ],
  "warnings": []
}
```

必须保存到：

```text
compile_runs.summary
compile_runs.knowledge_count
compile_run_knowledge_items
```

没有调用 `compile_result`：

```text
compile_run.status = failed
compiled_version 不推进
```

### 5. Agent 完成状态

支持：

```text
pending
running
completed
completed_with_warnings
failed
cancelled
```

只有以下条件全部满足才允许 `completed`：

- Agent 没有不可恢复错误。
- MCP 写入流程结束。
- Agent 成功调用 `compile_result`。
- 结果通过校验并持久化。
- `compiled_version` 推进到本次 `to_version`。

有 warnings 但 `compile_result` 有效时使用：

```text
completed_with_warnings
```

### 6. 模型失败和自动切换

实现 `ModelFailoverRunner`。

错误策略：

```text
429：读取 Retry-After，重试当前模型一次，仍失败则切换模型
401/403：不重复重试当前模型，切换备用供应商或失败
400：直接失败，不自动切换
408/超时/连接错误/5xx：有限退避重试，仍失败则切换模型
```

建议增加：

```text
compile_run_attempts
```

字段：

```text
id
compile_run_id
attempt_number
provider_id
model_name
status
error_type
error_message
tool_call_count
mcp_call_count
started_at
finished_at
```

### 7. 允许模型切换造成重复写入

本目标不实现复杂回滚或强幂等。允许：

```text
第一次 Agent 已写入知识 A
    ↓ 模型失败
第二次 Agent 再写入知识 A
```

不为本目标引入复杂的重复清理流程，但保留：

```text
item_key
content_hash
attempt_number
wiki
slug
```

后续再增加独立的知识总结/清理 Agent，统一读取本次整理产生的全部条目，处理重复和冲突知识。

不要给 `compile_run_knowledge_items` 增加唯一约束。

## 任务执行与版本快照

任务创建时固定：

```text
from_version
到 to_version
```

编译期间的新事件不进入当前任务。成功后只推进到固定的 `to_version`，剩余事件留待下一轮。

不能在数据库事务中调用 LLM 或 MCP：

```text
事务：认领任务、读取快照
事务外：调用 Agent/LLM/MCP
事务：保存结果、更新状态、推进 compiled_version
```

## API 保持

```text
GET  /api/v1/compile-runs
GET  /api/v1/compile-runs/{id}
GET  /api/v1/compile-runs/{id}/knowledge-items
GET  /api/v1/conversations/{sessionId}/compile-runs
POST /api/v1/conversations/{sessionId}/compile-runs
POST /api/v1/compile-runs/{id}/retry
```

重试必须创建新的整理记录，不覆盖旧失败记录。

## 配置要求

默认不影响普通服务启动：

```yaml
conversation-compiler:
  agent:
    enabled: false

spring:
  ai:
    mcp:
      client:
        enabled: false
```

启用时：

```bash
CONVERSATION_COMPILER_AGENT_ENABLED=true
CONVERSATION_COMPILER_MCP_ENABLED=true
LLMWIKING_MCP_URL=...
LLMWIKING_MCP_ENDPOINT=...
LLMWIKING_MCP_KEY=...
```

## 测试要求

新增功能必须有测试覆盖，至少包括：

### Agent

- 系统提示词包含整理要求。
- 事件正确传入 Agent。
- MCP 工具可被调用。
- `compile_result` 可被调用并保存结果。
- 未调用 `compile_result` 时失败。
- 结果参数校验失败时失败。

### Worker

- `pending → running`。
- 成功后 `running → completed`。
- warnings 后变为 `completed_with_warnings`。
- 失败后变为 `failed`。
- 失败时不推进 `compiled_version`。
- 整理期间的新事件不进入当前快照。

### Failover

- 429 重试当前模型一次。
- 429 重试失败后切换模型。
- 401/403 不重复重试当前模型。
- 400 直接失败。
- 超时和 5xx 支持有限重试。
- 每次 attempt 正确记录。

### MCP 和持久化

- MCP 白名单生效。
- 高风险工具不会暴露。
- MCP 连接失败时任务失败。
- summary 正确保存。
- knowledge items 正确保存。
- 允许重复条目保存。
- 失败记录保留。
- 重试创建新记录。

## 验收标准

```text
1. 可以创建 pending 整理任务。
2. Worker 可以自动获取并执行任务。
3. Agent 使用当前知识整理 Prompt。
4. Agent 能调用 LLMWikiNG OKF MCP。
5. Agent 能调用 compile_result。
6. 整理结果保存到数据库。
7. compiled_version 只在成功报告后推进。
8. 429 等已知错误支持模型切换。
9. 失败记录和 attempt 记录完整保留。
10. 允许重复写入，不实现复杂清理。
11. 所有新增功能有测试。
12. mvn test 全部通过。
13. 前端可通过整理记录 API 查看真实结果。
```

## 非目标

```text
知识重复清理 Agent
冲突知识自动裁决
LLMWikiNG 删除工具
多租户 Agent 隔离
WebSocket 实时日志
复杂 Agent 长期记忆
多会话高并发整理
自动无限重试
```
