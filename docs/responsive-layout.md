# 响应式布局实现、证据追溯与后续交接

## 1. 适用版本与阅读约定

本文为 T05 / NATI-18（`01a154bb-9805-7dcc-9cbd-2dd1ea3d4cd1`）直接配套维护文档，2026-10-19 UTC。parent=NATI-6（`01a14f2f-885e-7e0a-a944-78a0b02c413d`），stage50，执行前端工程师；父级验收/接续为开发负责人。人工跟进 kael.peng，user_id `c66043dc-76e3-496e-b4e6-f03295bad286`。该 stage 仅在 NATI-6 子项内比较，不能与 NATI-2 子项的 stage50 混为同一组。

代码说明以完整实现终点 **`40236a163cbf8d10255aef43bc0a20373af47db9`（T04）** 为准，包含 T01–T03，不从尚无实现的分析基线 develop `824d840887f574c65abe4923764cef97c048f79f` 推断行为。所有下文源码路径均为仓库相对路径，使用类/函数定位而不冻结易漂移行号。T05 自身准确文档提交、远端核对及静态审计以 NATI-18 最终报告/附件为准，不把旧代码证据重新标成 T05 运行结果。

有效输入为 **NATI-2-A2 v1.1 三完整原文件 + D-NATI12 v1.0 + D-EPOST-ENTRY v1.0**，没有 A3。三文件归档于主 NATI-2（`01a14f0e-3973-7397-8c87-a1fbcbbb0092`），来源 NATI-11；PRD 上游保留 A1、NATI-3/NATI-5 与 NATI-9 实际 member 决定 `01a14f9f-eade-7936-a339-ca2a930a642b`，design 上游为同版 PRD 及上述分析代码，task 上游为同版 design/PRD。

| 原文件 | 主需求附件 ID | SHA-256 |
| --- | --- | --- |
| prd.md v1.1 | `01a14fb1-83a2-7a82-a961-473b232b997e` | `a32e4f35d4b3b1ea3a325e77c215c431293fb06d35c949c0b27ebf5dd9d6a519` |
| design.md v1.1 | `01a14fb1-83b3-7e88-8fd1-e6044a0791b8` | `3ebe30cf648b08b84dce1d71ddf659d8bcadebce3a4c0ffb0501d253e16704aa` |
| task.md v1.1 | `01a14fb1-83be-7b13-8b2d-211fe8ea71b3` | `3c4a10fae796d6bf352cb324d8fb3295733711d7b8a38bac67eda8e845f7caff` |

本页所有附件均通过 `multica attachment download <附件ID> -o ./任务私有目录` 认证获取；Issue 用 `multica issue get <准确ID> --output json` 回读。不要直接访问资源 URL 或把机器上的私有下载路径当作交付地址。

- **D-NATI12 v1.0**：NATI-12 实际 member 决定评论 `01a1544c-3462-78ff-89f6-77b247cbb80e`；主需求已同步，技术组长在 NATI-6 评论 `01a15458-7a4e-7b1c-8a47-9084e5d06cb3` 确认。非 main 任务提交/push 可自行执行，PR 可选；准确远端提交与完整证据必需。替代 A2/旧契约“缺 PR 不得交付”的非 main 文字，不替代真实保护、main 发布 PR/检查/审批或部署授权。
- **D-EPOST-ENTRY v1.0**：主需求附件 `01a154c4-82ea-7374-b0cd-96bd502d1001` / `epost-routing-supplement.md`，SHA-256 `f8fbcd082aa093ae7a54687cb7137adcd22bec3971820de75e79541295fd2e09`；技术组长确认评论 `01a154c6-1062-7223-aaac-c6740f9f3117`，Leader 在 NATI-6 DP12 发布并回读。只修复专项路由，不改三文件字节、哈希、范围或权限，详见第7节。

本文只整理已具事实与静态一致性检查，不执行新的浏览器、构建、独立测试、CI 等待、合并、部署、数据库操作或专项。前置 T01–T04 的 Leader 限定范围核验通过不等于主需求最终验收。

## 2. 布局状态与监听（DS03/DS04）

入口：`frontend/src/App.vue` 的 `compactMedia`、`mediumMedia`、`readViewportMode`、`syncViewportMode`、`toggleSidebar` 与根模板；样式为 `frontend/src/style.css`。继续使用 Vue Composition API / Element Plus 与原锁文件，无框架替换或新依赖。

| 模式 / CSS px | `viewportMode` / `isCompact` | `sidebarWidth` / 导航 | `businessDrawerSize` |
| --- | --- | --- | --- |
| 320–767 | compact / true | `0px`，无常驻侧栏；临时导航默认关 | `100%` |
| 768–1199 | medium / false | 默认 `72px`，可展开 `240px` | `calc(100vw - 实际sidebarWidth)` |
| ≥1200 | desktop / false | 默认 `240px`，可折叠 `72px` | 同上 |

JS 两个 `matchMedia('(max-width: 767px)')` / `'(max-width: 1199px)'` 与 CSS 767/1199 上界一致。不是 UA 识别；最小支持宽度 320，不承诺更窄屏幕。`desktopCollapsed=null` 时 `isCollapsed` 随 medium 默认折叠/desktop 默认展开；用户显式切换后，跨非 compact 档保留选择。compact 只推导零占宽，不擦除显式选择。

根类 `.app-shell--compact/medium/desktop` 和内联 `--sidebar-width` 传递同一语义；侧栏实际宽度和三个业务抽屉不能各自硬编码档位默认值。挂载增加两个 `change` 监听并同步一次；卸载移除两监听并 `stopCompileRunPolling()`。布局回调只改模式、在非 compact 时清 `mobileNavOpen`，不 fetch、不重置查询/表单、不启动轮询。原挂载的四路业务加载与布局监听职责不同。

导航由同一 `navigationItems` 提供三个入口。compact 左抽屉 `.mobile-navigation` 宽 `min(280px, calc(100vw - 48px))`，有当前页 `aria-current`、开关名称/展开状态和关闭/焦点机制。`selectMenu` 关闭临时导航并在 nextTick 后把 `contentArea` 滚到左上；仅关闭导航/旋转不清查询。`restoreNavigationFocus` 在没有业务抽屉时返回导航按钮，不能抢业务抽屉焦点。`openSession/openCompileRun/openProviderCreate/openProviderEdit` 先关闭临时导航；业务抽屉已开时 `toggleSidebar` 不叠加导航；跨≥768强制清临时状态。

保留 `activeMenu/searchText/compileSessionFilter/settingsTab/selectedSession/selectedCompileRun/providerForm` 和未保存设置；不按 viewport 给业务子树设 key，不 destroy/recreate 业务抽屉。全屏图标仍无业务处理，Admin/Worker 装饰不代表新功能。

## 3. 主区、三表与滚动（DS05）

- 原 `html/body/#app` 高度100%、最小320且禁止文档滚动是外壳基线；`.app-shell` 100vh 回退 + 100dvh，`.main-container` 与 `.content-area` 保持 flex/min-width:0/min-height:0。**主区纵向滚动属于 `.content-area`**，页头与侧栏不随内容走；没有为通过矩阵新加全局 overflow-x:hidden 裁切。
- 页头默认72px、compact64px；中间主区内边距28px 24px 40px、compact24px 16px 36px，桌面34px 36px 48px。页头只收起装饰，当前页与导航保留；`.page-heading/.card-header/.provider-toolbar` 可收缩/换行，compact 搜索/筛选全宽。
- `.overview-grid` 用原 el-row/el-col 承载 grid：compact 单列；medium 会话2列/整理3列；desktop 会话4列/整理3列。`:has(> .el-col:nth-child(3):last-child)` 区分三卡概览；不要误恢复 el-col 自带576px分界或负 gutter。
- 会话、整理为 `.session-table`，供应商为 `.provider-table`。保留所有原字段和列宽，**每表唯一横滚所有者是 Element Plus 表体 scrollbar**；不再外套另一层横滚。`scrollbar-tabindex=0` 与 focus-visible 支持聚焦后方向键；大 scrollWidth 仅在明确局部区域合法，不能误当文档溢出，也不能用裁切隐藏末列。
- 整理120px/供应商130px操作列用 `:fixed="isCompact ? false : 'right'"`；compact 保留操作、可滑到末列，并显示 `.table-scroll-hint`（含方向键说明）。组件已按原开发证据自动更新几何，没有额外 doLayout/fetch。会话表无该 fixed 操作列。
- `.session-title small/.provider-name/.provider-table .cell`、状态 tag 和 `.model-tags` 允许断行，所有模型标签保留；会话标题原省略路径可在详情完整阅读，不新增 hover 唯一信息。数据仍 `filteredSessions/filteredCompileRuns/providers`，不创建手机卡片第二套数据逻辑。

## 4. 三业务抽屉、阅读与生命周期（DS06）

三模板共用 `businessDrawerSize` 和 `.business-drawer`，再分别保留 `.event-drawer/.compile-drawer/.provider-drawer`，原 v-model/closed 绑定不变。T03 管外壳、T04 管 provider 内区。

- 根不超100vw，100vh/100dvh；header不收缩，关闭按钮独立44×44。body `min-height:0; overflow:auto`，底部 `24px + env(safe-area-inset-bottom, 0px)`，不是固定 footer。正文滚到最后项而不依赖文档滚动。
- 事件/整理 `.drawer-heading` 文字区可聚焦，`max-height:28vh/28dvh; overflow:auto`，长标题/ID/状态局部滚到尾部，不把关闭按钮挤出短屏。header文字局部滚动与 body 纵滚是有意分工。
- 会话 `.drawer-summary` desktop3列、medium2列、compact1列；整理 `.compile-run-summary` desktop4列、medium2列、compact1列。事件 marker 固定窄宽，`.event-bubble` 可收缩，正文、工具名和参数断连续字符串。
- `.tool-call-field > pre` 局部最大260px；`.trace-entry-content/.failure-message` 最大340px，均 overflow:auto、pre-wrap/overflow-wrap:anywhere，可聚焦读到末行。`.result-summary/.knowledge-result-list li` 同样断行。不得为换行引入 v-html。
- 警告列表及“有警告但无明细”提示、trace/历史无trace、实时状态、写入响应、失败无具体错误兜底、attempts、summary、knowledge/空结果全部保留。事件/整理内只重复显示已有 `apiError`，不改错误归属/清理语义。**零上报条目不是无远端写入**：`returnedKnowledgeWrites` 统计 completed 的 knowledge_write 响应，仍提示重试前核对远端。
- `openCompileRun` 先停旧轮询、选记录/开抽屉并 refresh，pending/running 才 start；`refreshCompileRunDetails` 有刷新互斥及选中ID保护、并行原四详情请求，完成状态 stop；`startCompileRunPolling` 先 stop 再创建原 **1800ms** interval，`closeCompileRunDrawer` 在 closed 清理，卸载也 stop。断点监听不参与该链。
- `retryCompileRun` 只允许 failed 且 `retryingCompileRunId===null`；列表按钮保留 loading/互斥 disabled 与 `@click.stop`，详情也保持互斥。不因适配增加自动重试/重放写入。

## 5. 设置、provider 表单、浮层（DS07）

知识Tab `.settings-tab-header/.settings-tab-actions` compact纵排；`.selected-model-row` 为索引/可换行完整名称+独立操作行，`.selected-model-actions` 44px热区，首上移/末下移由原index禁用。多选全宽、间隔最小1/步长1、Prompt100%；`.settings-card/.settings-tabs` compact取消阻碍短屏滚动的最小高度。`cancelKnowledgeSettingsEditing` 仍 GET 重载原值，不是仅清UI。

provider `.provider-form` label-top、全宽；`.models-fetch-row` compact堆叠，`.readonly-model-list` 全模型断行；`.drawer-actions` 在可滚动 body 内、compact按钮等分，**没有固定底栏或 visualViewport 脚本**。紧凑输入16px，底部安全区保留；Chrome短屏焦点/末动作可达不是实际软键盘通过证据。表格编辑/删除按钮 aria-label 包含完整供应商名、compact44px；原生 window.confirm 取消不 DELETE。

`providerModelsFetched` 新增/编辑初始化均 false；必须获取非空模型才放行保存，不因已有 models 就绕过。新增获取要求类型/Base URL/Key，编辑获取传 providerId 可复用已存Key；`openProviderEdit` 清输入 apiKey，`normalizeProvider` 沿后端 apiKeyMasked，保存仍原payload/空串复用规则。不得把测试中的 MOCK Key 当真实Key验证成功。

三 select（模型多选、整理筛选、接口类型）均 `popper-class="responsive-select-popper ..."`，附区域 `.model-select-popper/.session-filter-popper/.provider-type-popper`。teleport 到 body，不能用表单祖先选择器限制：根最大 `100vw - 24px`，列表最大 `min(274px,45vh/45dvh)`、选项自适应高/换行/局部末项；compact选项至少44px。`responsivePopperOptions` 用锁定 Element Plus2.14.5 已具的 popper-options：preventOverflow `altAxis:true, tether:false, padding:12`，短屏两侧均不足时双轴限制，不新建业务状态/监听。

四处 ElMessage 只加 `customClass:'responsive-feedback'`，类型/文本/时长/关闭保留。最大宽 `100vw - 24px`，最大高 `100vh/100dvh - 32px`；内容可局部滚动（视口高减64px），pre-wrap，关闭44×44且 `flex-shrink:0`。现有全屏Tooltip只是装饰名称，不新增全局Tooltip覆盖或唯一信息依赖。

## 6. 需求—源码—任务—证据索引（DS09/DS10/DS12）

### 6.1 准确代码版本与附件入口

以下提交是串行累积关系，证据各自只属于原准确版本。无 PR、无已合并/CI成功声明；按 D-NATI12 使用远端提交交付。源码仅 T01–T04 修改 App.vue/style.css，T05 仅本页与必要README入口；不升级锁文件，无迁移。

| 任务 / 子Issue准确ID / NATI-6内stage | 远端分支 / 准确提交入口 | 最终报告附件 / 原始zip附件 |
| --- | --- | --- |
| T01 NATI-8 `01a14f5c-c4ae-7ab9-a8dd-713a59e64201` / 10 | `agent/agent/9399c3a09b96` / [1afd32995ee7fadf91f7b11154152e9c3898e034](https://github.com/kael-aiur/conversation-complier/commit/1afd32995ee7fadf91f7b11154152e9c3898e034) | `01a15455-031c-70ad-9159-94de3e809280` / `01a14fcc-4bb9-7b64-b898-b056589f1cc6` |
| T02 NATI-15 `01a1545b-e624-7e9b-beb8-4887920fc730` / 20 | `agent/agent/9c94935d5394` / [9cfc20f233299a5d8951d0d779aee7d8f07e3db6](https://github.com/kael-aiur/conversation-complier/commit/9cfc20f233299a5d8951d0d779aee7d8f07e3db6) | `01a15471-c3d0-70f6-8082-abcd60ce81f0` / `01a15471-c441-779b-aed6-8521c9674d36` |
| T03 NATI-16 `01a15475-7a12-7412-90b6-904e40b1a46d` / 30 | `agent/agent/3ca161327feb` / [b4f732293feab03360abe4499317f2e0e931be02](https://github.com/kael-aiur/conversation-complier/commit/b4f732293feab03360abe4499317f2e0e931be02) | `01a1548c-bdec-7159-b942-3e14a5849f5b` / `01a1548c-be66-7807-931a-343d50ca971d` |
| T04 NATI-17 `01a15490-4dc7-72e7-8456-092cc07c6f66` / 40 | `agent/agent/2c6f4ee0cb4b` / [40236a163cbf8d10255aef43bc0a20373af47db9](https://github.com/kael-aiur/conversation-complier/commit/40236a163cbf8d10255aef43bc0a20373af47db9) | `01a154b7-61f1-777f-beb8-bb6dacbb193d` / `01a154b7-6222-7b48-be54-cf1869c2ec9e` |
| T05 NATI-18 `01a154bb-9805-7dcc-9cbd-2dd1ea3d4cd1` / 50 | `agent/agent/2712b97b1503`；自身准确提交见本Issue最终报告 | 文档原文件/交付报告/静态审计随本Issue最终评论交付；不是新E-DEV |

T01另有2026-10-19收尾审计 `01a15455-0362-7c05-a259-cb5d5ea15505`；其原REPORT/PR失败结论由最终报告和 D-NATI12 替代，原包日志不删改。

### 6.2 R/AC 与 P/J 覆盖

下表源码均为 `frontend/src/App.vue` 与同名语义类的 `frontend/src/style.css`。证据 E1–E4 见6.3，与上表对应准确提交/附件，不声称在T05版本下重跑。

| 需求 / 验收 | 设计 / task及真实子单 | 源码定位 / 页面旅程 | 已有开发证据 / 后续边界 |
| --- | --- | --- | --- |
| R01 / AC01 | DS04 / T01 NATI-8 | navigationItems、toggleSidebar、selectMenu；P01、J01–J04导航段 | E1矩阵/导航焦点/边界；真机转第7节 |
| R02 / AC02 | DS04–06 / T01–03 NATI-8/15/16 | content-area、session/provider-table、business-drawer；全部P | E1主区、E2表格、E3抽屉末项，E4表单/浮层补充 |
| R03 / AC03 | DS05/06/08 / T02/03 NATI-15/16 | filteredSessions、checkApi、openSession、event-bubble/tool-call-field；P02/P03、J01 | E2搜索/清空/健康/正确ID，E3事件长文/空/loading/失败 |
| R04 / AC04 | DS05/06/08 / T02/03 NATI-15/16 | filteredCompileRuns、retryCompileRun、refresh/start/stop/closeCompileRun*、compile-*；P04/P05、J02 | E2筛选/重试stop，E3轮询/互斥/警告/零条目写入响应 |
| R05 / AC05 | DS07/08 / T04 NATI-17 | settings-form、selectedModelKeys、move/removeSelectedModel、save/cancelKnowledgeSettings*；P06、J03 | E4排序/最小间隔/Prompt/payload/保存取消；真实键盘未做 |
| R06 / AC06 | DS05/07/08 / T02/04 NATI-15/17 | provider-table、openProvider*、fetchProviderModels/saveProvider/deleteProvider；P07/P08、J04 | E2表全部模型/操作，E4校验/获取/原生确认；真实写入未做 |
| R07 / AC07 | DS04/06/07 / T01/03/04 NATI-8/16/17 | mobile-navigation、drawer-heading/pre、responsive-select-popper/feedback；P03/P05/P08/P09 | E1导航、E3阅读/关闭、E4浮层与触控；真机另验 |
| R08 / AC08 | DS03–08 / T01–04 NATI-8/15/16/17 | 断点推导与无viewport key、业务状态及原API；全部P | E1–E4桌面/跨档同DOM/值/轮询；不代表全部设备通过 |
| R09 / AC09 | DS10 / T05 NATI-18及T01–04 | 本页6/7节、原报告/原包 | E-DEV与E-POST分层，T05只静态追溯，不生成新截图通过项 |
| R10 / AC10 | DS11 / T05 NATI-18仅交接 | 本页8节、仓库AGENTS/发布配置线索 | 开发不等于发布；G01–G07由后续有权阶段满足，当前未执行 |

页面交叉索引：P01→T01/E1；P02/P04→T02/E2；P03/P05→T03/E3；P06→T04/E4；P07→T02+T04/E2+E4；P08外壳→T03/E3、内表单→T04/E4；P09导航→T01/E1，其余select/Message→T04/E4。J01→E1+E2+E3；J02→E2+E3；J03→E4；J04→E2+E4。真实外部模型/数据库写入不在mock旅程成功含义内。

### 6.3 原E-DEV环境、矩阵、步骤—预期—实际

统一环境：a42、Ubuntu22.04.5 LTS x86_64，Chrome **155.0.8059.84**、chrome-devtools CLI1.11.0；Node24.22.0、pnpm10.15.0，Vue3.5.43/Element Plus2.14.5/Vite8.3.0锁文件。T01为 **2026-10-18** 原运行（2026-10-19仅收尾审计）；T02/T03/T04为 **2026-10-19 UTC** 原运行。Chrome CSS模拟尺寸/模拟横屏，不是真实手机或跨浏览器；不是“验收当日稳定版已核实”。各自隔离mock，无真实后端/Key/模型/数据库写入。T01本机5188、T02 5185/5186、T03 5186、T04 5187为当时隔离端口，不是当前受控部署入口。

**M01** 十组：320×568、375×667、390×844、430×932、667×375、844×390、768×1024、1024×768、1280×800、1440×900。**M02**：767/768、1199/1200边界；T02–T04均另含767×375、768×375、1199×375、1200×375，T01 state保留跨档/模拟旋转序列。全部P的覆盖按任务分工聚合，不能把T01导航截图当其他任务完成，也不能把不同提交的截图当一次集成运行。

以下路径为zip内真实路径（不是运行环境文件链接）。原报告提供逐步脚本及完整值，本表为入口。T05认证读取四报告并核对原包清单/SHA256SUMS，只是静态审阅，未重新运行这些脚本。

| 组 / 包内目录 | 步骤和预期 | 原实际结果 / 数据、截图与日志 |
| --- | --- | --- |
| E1 / `NATI-8-T01/` | 十尺寸逐个三入口，导航关闭无占宽/遮罩，主区滚到末项；跨档搜索/筛选/未保存值不丢，导航ESC/焦点与卸载清理 | 30组矩阵通过；`matrix.json`、`M01-{w}x{h}-{0,1,2}.png`（会话/整理/设置）。`state.json`：单轮询starts1/关闭active0、监听adds/removes2/2、跨档无新fetch；`keyboard.json`、`nav-667x375-keyboard.png`。`browser-commands.log` 取 FINAL SOURCE 1afd329 后记录；`M02-nav-short.png`实为375×667，不能误作375高 |
| E2 / `T02-EDEV/` | 十尺寸+边界三表首/末列及主区到底，compact无fixed；搜索/筛选清空、正确记录、重试不穿透/互斥、provider编辑/删除确认取消，102个标签可读 | 42组矩阵通过，`matrix.json/matrix-audit.txt/matrix-run.log`；`{sessions,runs,providers}-{WxH}-end.png`，320/1440另start图。`interactions.json`、`retry-loading.png`、`provider-action-reachable.png`；`states.json`、`empty/failure/loading-{0,1,2}-{WxH}.png`；`interactions-console.txt/interactions-network.txt`与`network.txt/console.txt` |
| E3 / `NATI-16-T03/` | 三抽屉各尺寸开/关、正文/pre/标题末项、44px关闭；事件/整理长文/空/骨架/503/警告无明细/零条目有写入响应；pending跨档/完成/关闭/切记录、互斥与未保存值 | 42/42样本、504几何断言；`matrix.json/matrix-assertions.json`、`{event,compile,provider}-{WxH}-{top,end}.png`共84张。`states.json`35记录/`state-assertions.json`33断言；`zero-count-confirmation.json`、`zero-count-write-confirmation.png`确认零条目不代表无写入；`event-heading-end.png/event-pre-end.png`；`source-contract.json/console.txt/network.json` |
| E4 / `NATI-17-T04/` | 每尺寸只读/编辑模型下拉末项/Prompt焦点/provider百标签/类型浮层/底部动作；排序/多选/间隔/保存取消/校验/获取/删除；短屏焦点与ESC返回 | 84/84样本、4956断言；`matrix.json/matrix-assertions.json`；`{knowledge-readonly,knowledge-dropdown,knowledge-prompt-end,provider-form,provider-type-dropdown,provider-actions-end}-{WxH}.png`。`states.json`46记录、`state-assertions.json`47断言；`long-message-short-end.png/session-filter-long-last.png`等。`states-resume-run.log`续跑同一40236a1剩余项，`source-contract.json/console-history.json/network-history.json/EDEV-manifest.json` |

构建均是原开发自检：T01原 `pnpm install --frozen-lockfile` / `pnpm build:standalone` / `pnpm build:spring`，`build.log`记两构建exit0；T02指定pnpm10.15.0的npm exec wrapper、T03/T04指定版本npx，install与standalone/spring均报告exit0，原 `install.log/build-standalone.log/build-spring.log`可查。未在Node22容器另跑；JS工程无独立typecheck/test script，不虚构类型/单测通过。**>500kB chunk警告全部保留**，不新增拆包优化；生成物未提交。T05不运行安装/构建。

失败及限制必须随索引保留：T02长状态tag在最终9cfc20f已换行；T03初期折叠菜单harness选取中断后完整重跑。T04 ce734e9短屏长选项越底，在355c71c加双轴约束；长Message关闭被压缩在40236a1加不收缩与局部滚动，最终矩阵/状态以40236a1为准，`preliminary-*`不是通过证据。原生confirm/evaluate自动处理、nextTick/步进/时序等辅助脚本问题保留原记录，不改写成产品通过。受控503/重试失败属刻意负例；最终静态资源记录与mock API分开，不笼统宣称真实端到端无错误。T04 mock注入前探查404不代表真实后端失败或成功。

每条原证据保留 task/R/AC/P/J/M、准确提交、日期/完整环境、模拟视口、步骤/预期/实际、截图/日志、失败/未验证项。需要重现时先读原包说明、检出对应提交，使用自己的隔离context/pageId；此为后续有权任务的重现线索，不是本T05新执行安排。

## 7. E-POST当前唯一入口与最终验收门禁

**当前有效承接 NATI-19 / `01a154c3-b756-7a7f-b7d1-2e7e4740ff2b`**：parent=NATI-2 / `01a14f0e-3973-7397-8c87-a1fbcbbb0092`，stage50，member kael.peng / `c66043dc-76e3-496e-b4e6-f03295bad286`，2026-10-19认证回读 **backlog，未执行**。开发NATI-6是主需求stage30，拟部署stage40尚无执行授权。NATI-18自身的stage50属于另一直接父级。

历史 **NATI-10 / `01a14fa4-abaf-7032-9afa-dad4c7705f95` → NATI-19**：原入口当前认证不可访问，原因未知，不断言删除或权限变更；仅保留历史，不再当可用入口。替代来源为第1节D-EPOST-ENTRY附件/技术组长确认与DP12，A2三原文件及原开发报告里全部旧专项指向按此补充承接，不静默擦历史、不伪称A3；若旧单恢复由技术组长核对归并，防止双重执行。

入口可认证回读+有权补充生效已满足T05路由门禁，**不需要NATI-19 done/设备现在到位/现在执行专项**。但主需求最终验收条件不变：

1. kael.peng部署阶段落实真实执行人账号、设备型号、准确OS/浏览器、分工与时间；当前安排/设备/时间**未落实**，不把人工指派当资源已就绪。
2. 技术组长验收部署交付后，补准确commit/镜像digest、受控环境入口、脱敏数据及权限、逐步清单/预期/证据字段，明确激活NATI-19。部署版本/环境当前**未落实**。
3. 仍覆盖验收当天当前稳定iOS Safari（含手机、平板横屏）、Android Chrome、桌面Chrome/Edge关键旅程及真实软键盘、动态地址栏/视口、旋转、短屏长内容、导航/三表/三抽屉/表单/浮层与跨断点状态。不要求设备×全部尺寸笛卡尔积，不以Chrome模拟替代；稳定版具体版本由实际验收记录，不猜版本号。
4. 骨架：三入口导航→搜索/筛选和表格首末列→会话/整理/provider抽屉末项及关闭→设置/provider校验/获权保存取消→键盘/旋转/动态视口焦点及动作→桌面/空态/错误。每步填P/J/R/AC、预期/实际、执行人/日期/设备OS浏览器/部署版本/环境、截图视频必要日志、未执行项。
5. 结果与安排通过 **NATI-19附件/评论** 回填；失败附复现并由技术组长协调关联整改/复验准确新版本，不直接扩大为生产写入/代码发布。真实模型/Key/写入须受控环境另获明确授权，mock结果不抵扣真实成功项。
6. 所有约定专项和必要整改复验交齐后人工报告并done，由技术组长独立验收；**安排未落实、专项未完成或未通过，不关闭NATI-2**。上线冒烟成功也不等于专项通过。

## 8. DS11 G01–G07部署交接（R10/AC10非开发责任）

最终上线目标保留，但本任务无main合并、部署/服务器/生产数据库/迁移/停机/回滚授权。仓库AGENTS/README与 `.github/workflows/docker-publish.yml`、Dockerfile只提供流程线索，不是已批准部署方法/环境。无deploy.md的基线须由后续部署负责人按协议向人工补方法，不在此编造路径/服务名/窗口。

| 门禁 | 后续负责人必须核实/交回 | 未具备/失败处理 |
| --- | --- | --- |
| G01 分支/版本 | 技术组长核对开发准确提交集合、develop→main发布PR/最终commit/digest及真实保护；非main PR可选不等于main已发布 | 不擅自改投main/混入其他改动；资料缺失阻塞发布 |
| G02 方法/环境 | 部署负责人核实真实目标项目、Compose服务/镜像仓库/受控访问、deploy.md或有权人工方法 | 不猜环境/IP/路径，不公开凭据，不执行远程命令 |
| G03 审批/窗口/风险 | 有权人工明确范围/执行人/窗口/可用性、失败阈值/观察时长、回滚授权/恢复责任 | “要上线”不是生产/数据/停机批准；没有批准不发布 |
| G04 构建/产物 | 核对main对应PR/检查与Action成功、镜像tag/digest/commit，Dockerfile前端打入JAR；留上一稳定digest/配置快照 | 不用漂移latest代替准确版本；构建失败/版本不符停止 |
| G05 数据/配置保护 | 本改动无迁移；SQLite卷和加密密钥保持，部署方核实备份/恢复方法与权限 | 不删除重建数据库/换密钥；额外数据库需求先升级 |
| G06 健康/冒烟 | 获权执行后交容器、`/api/health`（必要时`/health`）、静态资源、三入口/三抽屉关键浏览冒烟；写操作用获权受控数据 | 健康非UP/资源失败/主要动作不可达/严重溢出/回归判失败，停止并报告 |
| G07 观察/失败处置 | 按批准窗口记起止、错误/健康/反馈；失败由已授权部署执行者按已核验方法回前稳定镜像/配置，再验健康和页面 | 无回滚权限先升级；前端回滚不授数据恢复权 |

后续交付须含发布PR/Action、环境和审批、前后digest、健康/冒烟/观察及失败处置证据；当前均**未执行**。G06不是第7节专项替代，部署验收后技术组长才激活NATI-19。开发小队/本文不代部署负责人完成门禁。

## 9. 不变契约、维护风险与接续

- API URL/方法/结构、前端搜索trim/小写、整理sessionId等值筛选、会话/整理100条/事件500条上限不变。详情四路请求、重试failed/互斥/click.stop、1800ms/关闭清理不变。
- 设置 GET/PUT `/api/v1/settings/knowledge`（GET可204）：models顺序/首模型兼容字段、intervalMinutes、原始Prompt、enabled保持；provider原GET/POST/PUT/DELETE与 `/fetch-models` 的 interfaceType/baseUrl/apiKey/providerId/models/modelsFetchedAt不改。布局切换不发保存/删除/重试。
- 无后端/DTO/数据库/权限、依赖/锁文件升级、持久化布局设置、数据采集或迁移。Key沿原脱敏/加密边界，文本插值无v-html扩围；不把密钥/真实对话写入日志截图或公开交付。
- 长文本/多标签可能需要明显局部纵滚/横滚；保留标题/pre/表体/消息各自明确滚动责任，不能以一屏显示全部为由删字段。body浮层用命名根类，升级组件须另获范围并重新核对DOM/API，不能无关全局覆盖。
- 原证据是各自代码版本/Chrome/mock开发自检，非独立测试、真实Key/外部模型/数据库持久化、真机/跨浏览器或最终验收。T05只做文档/静态核查；没有新浏览器/构建/CI或部署通过记录。残余chunk警告与专项风险如实移交。
- NATI-18交齐文档原文件、准确远端提交、报告/静态审计后显式done并回读；父级Leader独立核验覆盖再汇总NATI-6，上级技术组长在NATI-2独立验收，不代关父/主需求、不自动启动部署。通知由平台阶段接续；自动失败由kael.peng从NATI-2恢复技术组长协调NATI-6→本原单。新真实阻塞报Leader，由其沿实际父级协调，不能循环等待已解除的旧入口。
