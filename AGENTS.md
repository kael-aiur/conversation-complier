# 项目协作与部署约定

## “部署这个项目”的定义

当用户说“部署这个项目”或提出等价的部署请求时，默认表示完成以下完整流程，而不只是更新服务器容器：

1. **整理并提交全部项目代码**：检查工作区、测试和构建结果，审阅完整差异，确认没有密钥、Token、私有域名/IP 或其他不应公开的信息；将本次项目改动一并提交。不要覆盖或丢弃用户已有改动。
2. **通过 PR 合并到 `main`**：先按仓库规则将本轮已验收成果落实到 `develop`，核验准确提交集合和完整差异，再创建以 `develop` 为源、`main` 为目标的 Pull Request，遵守实际分支保护、必需 review 和检查后合并。不要绕过 `develop` 自选其他发布分支；遇到漂移、冲突或无关变更应先报告，不强推或覆盖他人内容。未经用户明确要求，不要直接向 `main` 推送。
3. **等待 GitHub Actions 完成**：确认合并后对应的 GitHub Actions 工作流运行结束且成功。失败时先调查并修复，不要继续部署未经成功构建的版本。
4. **更新 itx 服务器部署**：在 itx 服务器上拉取本次构建的 Docker 镜像，更新 Docker Compose 部署并重启服务。
5. **验证部署结果**：确认容器正常运行、健康检查/服务访问正常，并向用户报告 PR、构建和部署结果；如任一步失败，说明停在哪一步及原因，不得把未完成的流程描述为已部署。

执行部署前应核实当前仓库、目标分支、PR/Action 状态和 itx 上的部署项目，避免误操作其他服务。服务器凭据、访问令牌和其他秘密不得写入代码、日志、提交内容或回复。若缺少完成某一步所必需的权限或关键信息，应停止在安全边界处并明确告知用户。

## 本项目部署指引的知识库检索入口

- **仓库与环境**：对话编译，`kael-aiur/conversation-complier`（仓库名确为 `complier`）；生产 `itx`、`linux/amd64`、现有 Docker Compose 部署。不得套用其他项目或主机的方法。
- **权威完整指引**：LLMWikiNG 服务，知识库 **Conversation Compiler**，wiki slug `conversation-compiler`，页面 slug/ID `release-deployment-pipeline`，标题《Conversation Compiler 构建发布与部署流程》，类型 `Reference`。先读页首 **2026-10-20 已验证更新**；后面的旧分支、单 cp、ERR trap、隧道等仅保留历史来源，不是当前无条件可执行方案。
- **唯一搜索关键词**：`kael-aiur/conversation-complier itx 部署`。2026-10-20 已实际验证下列搜索唯一命中该页，并以完整读取核验，不能把搜索摘要当部署方法：

  ```text
  okf_search(query="kael-aiur/conversation-complier itx 部署", wiki="conversation-compiler")
  okf_read_concept(slug="release-deployment-pipeline", wiki="conversation-compiler")
  ```

- **已验证生产基线**（NATI-25，2026-10-20）：main `2ba1edb50d01dd4b65722f3ce4fbd175da0dbba2`，PR #14，Action `39449000038` attempt1；实际 amd64 manifest `sha256:0b3163a0e48930d7b50b5fec0d85551be910d4b93fbcbc30f7133c81b0f1083f`，真正 config digest `sha256:088a04bbed4ee84a250e8a521b03324ff1dd13f68d585c1001b378c03155a038`。不可把 manifest、Docker image ID、config digest 混称；具体现场映射、备份及只读验证见完整知识页。
- **维护版本与责任**：本索引维护于 2026-10-20（NATI-26），对应知识页已回读更新时间 `2026-10-20T17:09:11Z`。部署工程师按每次实际证据维护同一页，部署负责人核验；每次执行前重读最新页并核对环境/版本/授权。新坑及步骤变化更新同页，入口或适用条件变化同步本索引；无新增则在交付报告记录核对版本，不制造重复文档。
- **安全与授权**：知识页、既有管理员权限、历史成功及本文流程均不替代本次授权。访问不可用、内容过时/不匹配、方法或权限缺失时停止，请部署负责人协调人工指引并明确恢复原执行 Issue，不猜命令或访问参数。凭据沿既有安全渠道，不打印/入库秘密、完整生产配置或真实对话。运行中单 cp 主 DB 不保证 WAL 一致性；采用已核验的一致性备份和独立副本验证。数据库迁移/恢复、卷或主密钥变更、自动回滚、隧道及共享进程清理须另获准。
- **文档不等于再次部署**：本次知识索引按 `develop → main` 文档 PR 落地；即使触发新镜像构建，也未批准替换上述生产基线，不部署文档新镜像或跟随 `latest`。部署后只读冒烟/有限观察不等于 NATI-19 E-POST 真机专项或主需求最终验收。
