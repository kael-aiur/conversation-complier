<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound,
  Clock,
  Connection,
  DataAnalysis,
  Document,
  Tickets,
  Fold,
  FullScreen,
  Menu as MenuIcon,
  Monitor,
  Search,
  Edit,
  Plus,
  Delete,
  Setting,
  User,
} from '@element-plus/icons-vue'

const isCollapsed = ref(false)
const activeMenu = ref('sessions')
const apiStatus = ref('未检查')
const checking = ref(false)
const loadingSessions = ref(false)
const loadingEvents = ref(false)
const loadingCompileRuns = ref(false)
const loadingCompileDetail = ref(false)
const apiError = ref('')
const searchText = ref('')
const drawerVisible = ref(false)
const selectedSession = ref(null)
const selectedEvents = ref([])
const compileDrawerVisible = ref(false)
const selectedCompileRun = ref(null)
const settingsTab = ref('knowledge')
const providerDrawerVisible = ref(false)
const providerDrawerMode = ref('create')
const providerDrawerError = ref('')
const fetchingProviderModels = ref(false)
const providerModelsFetched = ref(false)
const selectedProviderId = ref('provider-openai')
const selectedModel = ref('gpt-4o-mini')
const compileInterval = ref(30)
const compilePrompt = ref('提取稳定事实、已确认的项目决策、用户明确表达的偏好和可复用流程。忽略普通问答、临时调试、助手推测和敏感凭据。')
const knowledgeSettingsEditing = ref(false)
const savingKnowledgeSettings = ref(false)
const providerForm = ref(createProviderForm())
const compileSessionFilter = ref('')

const providers = ref([])

function createProviderForm() {
  return { id: '', name: '', type: 'responses', baseUrl: '', apiKey: '', models: [], modelsFetchedAt: '' }
}

const sessions = ref([])
const compileRuns = ref([])


const filteredSessions = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  if (!keyword) return sessions.value
  return sessions.value.filter((session) => `${session.id} ${session.title} ${session.source}`.toLowerCase().includes(keyword))
})

const metrics = computed(() => [
  { label: '活跃会话', value: String(sessions.value.filter((session) => session.status === 'active').length), icon: ChatDotRound, color: 'blue', trend: '实时数据' },
  { label: '待编译', value: String(sessions.value.filter((session) => session.status === 'stale').length), icon: Clock, color: 'orange', trend: '等待空闲' },
  { label: '编译任务', value: String(sessions.value.filter((session) => session.status === 'compiling').length), icon: DataAnalysis, color: 'purple', trend: '当前执行中' },
  { label: '知识条目', value: '--', icon: Document, color: 'green', trend: '接口待接入' },
])

const pageTitle = computed(() => ({ sessions: '会话列表', 'compile-runs': '整理记录', settings: '知识整理设置' }[activeMenu.value] || '会话列表'))
const filteredCompileRuns = computed(() => compileSessionFilter.value
  ? compileRuns.value.filter((run) => run.sessionId === compileSessionFilter.value)
  : compileRuns.value)
const selectedProvider = computed(() => providers.value.find((provider) => provider.id === selectedProviderId.value))
const providerModels = computed(() => selectedProvider.value?.models || [])

async function checkApi() {
  checking.value = true
  try {
    const response = await fetch('/api/health')
    if (!response.ok) throw new Error('health check failed')
    const data = await response.json()
    apiStatus.value = data.status === 'UP' ? '后端已连接' : '后端异常'
  } catch {
    apiStatus.value = '无法连接后端'
  } finally {
    checking.value = false
  }
}

async function loadSessions() {
  loadingSessions.value = true
  apiError.value = ''
  try {
    const response = await fetch('/api/v1/conversations?limit=100&offset=0')
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    sessions.value = data.map((conversation) => ({
      id: conversation.sessionId,
      title: conversation.title || conversation.sessionId,
      source: conversation.source || 'agent-trace',
      status: conversation.status || 'active',
      events: conversation.eventCount || 0,
      progress: conversation.progress ?? 0,
      updated: formatUpdatedAt(conversation.updatedAt || conversation.lastEventAt),
    }))
    apiStatus.value = '后端已连接'
  } catch (error) {
    apiError.value = `会话加载失败：${error.message}`
    apiStatus.value = '无法连接后端'
  } finally {
    loadingSessions.value = false
  }
}

async function loadCompileRuns() {
  loadingCompileRuns.value = true
  try {
    const response = await fetch('/api/v1/compile-runs?limit=100&offset=0')
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    compileRuns.value = data.map(normalizeCompileRun)
  } catch (error) {
    apiError.value = `整理记录加载失败：${error.message}`
  } finally {
    loadingCompileRuns.value = false
  }
}

async function loadProviders() {
  try {
    const response = await fetch('/api/v1/model-providers')
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    providers.value = data.map(normalizeProvider)
    if (!selectedProviderId.value || !providers.value.some((p) => p.id === selectedProviderId.value)) {
      selectedProviderId.value = providers.value[0]?.id || ''
    }
    // Startup loads providers and persisted knowledge settings in parallel. Keep
    // the persisted model when it is still available instead of overwriting it
    // with the first model returned by the provider list.
    const availableModels = selectedProvider.value?.models || []
    if (!selectedModel.value || !availableModels.includes(selectedModel.value)) {
      selectedModel.value = availableModels[0] || ''
    }
  } catch (error) {
    apiError.value = `模型供应商加载失败：${error.message}`
  }
}

async function loadKnowledgeSettings() {
  try {
    const response = await fetch('/api/v1/settings/knowledge')
    if (response.status === 204) return
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    selectedProviderId.value = data.providerId || selectedProviderId.value
    selectedModel.value = data.modelName || selectedModel.value
    compileInterval.value = data.intervalMinutes || 30
    compilePrompt.value = data.prompt || ''
    knowledgeSettingsEditing.value = false
  } catch (error) {
    apiError.value = `知识整理设置加载失败：${error.message}`
  }
}

function normalizeProvider(provider) {
  return { ...provider, type: provider.interfaceType, apiKey: provider.apiKeyMasked || '', models: provider.models || [] }
}

function normalizeCompileRun(run) {
  return {
    ...run,
    sessionTitle: run.sessionTitle || run.sessionId,
    knowledgeCount: run.knowledgeCount || 0,
    startedAt: formatUpdatedAt(run.startedAt),
    duration: run.durationSeconds ? `${Math.floor(run.durationSeconds / 60)} 分 ${run.durationSeconds % 60} 秒` : (run.status === 'running' ? '进行中' : '暂无'),
    knowledge: [],
  }
}

async function openSession(row) {
  selectedSession.value = row
  selectedEvents.value = []
  drawerVisible.value = true
  loadingEvents.value = true
  try {
    const response = await fetch(`/api/v1/conversations/${encodeURIComponent(row.id)}/events?limit=500&offset=0`)
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    selectedEvents.value = data.map((event) => normalizeEvent(event))
  } catch (error) {
    apiError.value = `事件加载失败：${error.message}`
  } finally {
    loadingEvents.value = false
  }
}

function normalizeEvent(event) {
  const payload = parsePayload(event.payloadJson)
  const data = payload.data || {}
  const type = event.eventType || 'event'
  const isToolCall = type === 'tool_call'
  const tool = data.tool_name || data.tool || data.name || ''
  const argumentsValue = data.arguments ?? data.input ?? data.parameters ?? null
  return {
    type,
    styleType: type === 'assistant_response' ? 'assistant_message' : type,
    label: eventLabel(type),
    time: formatEventTime(event.eventTimestamp || event.receivedAt),
    content: isToolCall ? `调用工具：${tool || '未命名工具'}` : extractEventContent(data, payload),
    tool,
    arguments: isToolCall ? formatArguments(argumentsValue) : '',
  }
}

function parsePayload(payloadJson) {
  try {
    const normalized = String(payloadJson || '{}').trim().replace(/\\n\s*$/, '')
    return JSON.parse(normalized)
  } catch { return {} }
}


function formatArguments(value) {
  if (value === null || value === undefined || value === '') return '无参数'
  if (typeof value === 'string') return value
  try { return JSON.stringify(value, null, 2) } catch { return String(value) }
}

function extractEventContent(data, payload) {
  for (const key of ['prompt', 'content', 'message', 'text', 'result', 'command']) {
    if (typeof data[key] === 'string' && data[key].trim()) return data[key]
  }
  return payload.event_type ? `收到 ${payload.event_type} 事件` : '事件暂无可展示内容'
}

function eventLabel(type) {
  return {
    user_prompt: '用户消息',
    assistant_response: '助手回复',
    assistant_message: '助手回复',
    tool_call: '工具调用',
    tool_result: '工具结果',
    llm_request: '模型请求',
    llm_response: '模型响应',
    file_read: '读取文件',
    file_write: '写入文件',
    error: '错误',
    session_start: '会话开始',
    session_end: '会话结束',
  }[type] || type
}

function formatEventTime(value) {
  if (!value) return '--:--:--'
  const date = typeof value === 'number' ? new Date(value * 1000) : new Date(value)
  return Number.isNaN(date.getTime()) ? '--:--:--' : date.toLocaleTimeString('zh-CN', { hour12: false })
}

function formatUpdatedAt(value) {
  if (!value) return '暂无'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

onMounted(() => { loadSessions(); loadCompileRuns(); loadProviders(); loadKnowledgeSettings() })

function toggleSidebar() {
  isCollapsed.value = !isCollapsed.value
}

function statusLabel(status) {
  return { active: '采集中', stale: '待编译', compiling: '编译中', compiled: '已完成' }[status] || status
}

function statusType(status) {
  return { active: 'success', stale: 'warning', compiling: 'primary', compiled: 'info' }[status] || 'info'
}

function compileStatusLabel(status) {
  return { completed: '已完成', running: '整理中', failed: '失败' }[status] || status
}

function compileStatusType(status) {
  return { completed: 'success', running: 'primary', failed: 'danger' }[status] || 'info'
}

function openProviderCreate() {
  providerDrawerMode.value = 'create'
  providerDrawerError.value = ''
  providerModelsFetched.value = false
  providerForm.value = createProviderForm()
  providerDrawerVisible.value = true
}

function openProviderEdit(provider) {
  providerDrawerMode.value = 'edit'
  providerDrawerError.value = ''
  providerModelsFetched.value = false
  providerForm.value = { ...provider, apiKey: '', models: [...provider.models] }
  providerDrawerVisible.value = true
}

async function fetchProviderModels() {
  if (!providerForm.value.baseUrl.trim() || !providerForm.value.type || !providerForm.value.apiKey.trim()) {
    providerDrawerError.value = '请填写接口类型、Base URL 和 API Key 后获取模型'
    return
  }
  fetchingProviderModels.value = true
  providerDrawerError.value = ''
  try {
    const response = await fetch('/api/v1/model-providers/fetch-models', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ interfaceType: providerForm.value.type, baseUrl: providerForm.value.baseUrl, apiKey: providerForm.value.apiKey }),
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    providerForm.value.models = data.models || []
    providerForm.value.modelsFetchedAt = data.fetchedAt || '刚刚'
    providerModelsFetched.value = providerForm.value.models.length > 0
  } catch (error) {
    providerDrawerError.value = `模型获取失败：${error.message}`
  } finally {
    fetchingProviderModels.value = false
  }
}

async function saveProvider() {
  if (!providerModelsFetched.value) { providerDrawerError.value = '请先点击“获取”加载可用模型列表后再保存'; return }
  if (!providerForm.value.name.trim() || !providerForm.value.baseUrl.trim()) { providerDrawerError.value = '请完整填写供应商名称和 Base URL'; return }
  const payload = { name: providerForm.value.name, interfaceType: providerForm.value.type, baseUrl: providerForm.value.baseUrl, apiKey: providerDrawerMode.value === 'edit' && providerForm.value.apiKey.includes('••••') ? '' : providerForm.value.apiKey, models: providerForm.value.models, modelsFetchedAt: providerForm.value.modelsFetchedAt, enabled: true }
  try {
    const url = providerDrawerMode.value === 'create' ? '/api/v1/model-providers' : `/api/v1/model-providers/${providerForm.value.id}`
    const response = await fetch(url, { method: providerDrawerMode.value === 'create' ? 'POST' : 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    providerDrawerVisible.value = false
    await loadProviders()
  } catch (error) { providerDrawerError.value = `保存失败：${error.message}` }
}

async function deleteProvider(provider) {
  if (!window.confirm(`确认删除供应商“${provider.name}”吗？`)) return
  try {
    const response = await fetch(`/api/v1/model-providers/${provider.id}`, { method: 'DELETE' })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    await loadProviders()
  } catch (error) { apiError.value = `删除供应商失败：${error.message}` }
}

function startKnowledgeSettingsEditing() {
  knowledgeSettingsEditing.value = true
  apiError.value = ''
}

async function cancelKnowledgeSettingsEditing() {
  knowledgeSettingsEditing.value = false
  await loadKnowledgeSettings()
}

async function saveKnowledgeSettings() {
  savingKnowledgeSettings.value = true
  try {
    const response = await fetch('/api/v1/settings/knowledge', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ providerId: selectedProviderId.value, modelName: selectedModel.value, intervalMinutes: compileInterval.value, prompt: compilePrompt.value, enabled: true }) })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    apiError.value = ''
    knowledgeSettingsEditing.value = false
    ElMessage({ message: '知识整理设置保存成功', type: 'success', duration: 2200, showClose: true })
  } catch (error) {
    apiError.value = `知识整理设置保存失败：${error.message}`
    ElMessage({ message: `保存失败：${error.message}`, type: 'error', duration: 3200, showClose: true })
  } finally {
    savingKnowledgeSettings.value = false
  }
}

function handleProviderChange(providerId) {
  selectedModel.value = providers.value.find((provider) => provider.id === providerId)?.models[0] || ''
}

async function openCompileRun(run) {
  selectedCompileRun.value = run
  selectedCompileRun.value.knowledge = []
  compileDrawerVisible.value = true
  loadingCompileDetail.value = true
  try {
    const [detailResponse, itemsResponse] = await Promise.all([
      fetch(`/api/v1/compile-runs/${run.id}`),
      fetch(`/api/v1/compile-runs/${run.id}/knowledge-items`),
    ])
    if (!detailResponse.ok || !itemsResponse.ok) throw new Error('整理记录详情加载失败')
    const detail = await detailResponse.json()
    const items = await itemsResponse.json()
    selectedCompileRun.value = { ...normalizeCompileRun(detail), knowledge: items.map((item) => item.summary || item.title) }
  } catch (error) {
    apiError.value = error.message
  } finally {
    loadingCompileDetail.value = false
  }
}

function eventIcon(type) {
  return { user_prompt: 'U', assistant_response: 'A', assistant_message: 'A', tool_call: '↗', tool_result: '✓', llm_request: 'L', llm_response: 'R', error: '!', session_start: '▶', session_end: '■' }[type] || '•'
}
</script>

<template>
  <el-container class="app-shell">
    <el-aside :width="isCollapsed ? '72px' : '240px'" class="app-sidebar">
      <div class="brand" :class="{ 'brand--collapsed': isCollapsed }">
        <div class="brand-mark"><Connection :size="20" /></div>
        <div v-if="!isCollapsed" class="brand-copy"><strong>Conversation</strong><span>Compiler</span></div>
      </div>

      <el-menu :default-active="activeMenu" :collapse="isCollapsed" class="sidebar-menu" @select="activeMenu = $event">
        <el-menu-item index="sessions">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>会话列表</template>
        </el-menu-item>
        <el-menu-item index="compile-runs">
          <el-icon><Tickets /></el-icon>
          <template #title>整理记录</template>
        </el-menu-item>
        <el-menu-item index="settings">
          <el-icon><Setting /></el-icon>
          <template #title>知识整理设置</template>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <div class="worker-status"><span class="status-dot"></span><span v-if="!isCollapsed">编译 Worker 运行中</span></div>
        <div v-if="!isCollapsed" class="version-label">MVP Prototype</div>
      </div>
    </el-aside>

    <el-container class="main-container">
      <el-header class="topbar">
        <div class="topbar-left">
          <el-button text class="collapse-button" @click="toggleSidebar"><el-icon :size="20"><Fold v-if="!isCollapsed" /><MenuIcon v-else /></el-icon></el-button>
          <div class="breadcrumb"><span>控制台</span><span class="breadcrumb-separator">/</span><strong>{{ pageTitle }}</strong></div>
        </div>
        <div class="topbar-right">
          <el-tooltip content="全屏" placement="bottom"><el-button text class="topbar-icon"><FullScreen /></el-button></el-tooltip>
          <el-divider direction="vertical" />
          <el-avatar :size="32" class="user-avatar"><User /></el-avatar>
          <span class="user-name">Admin</span>
        </div>
      </el-header>

      <el-main class="content-area">
        <div class="page-heading">
          <div>
            <p class="eyebrow">CONVERSATION COMPILER</p>
            <h1>{{ pageTitle }}</h1>
            <p class="page-description">{{ activeMenu === 'sessions' ? '查看采集到的 Agent 会话，以及知识编译进度。' : '查看每个会话的知识整理任务、处理范围和输出结果。' }}</p>
          </div>
          <el-button v-if="activeMenu === 'sessions'" type="primary" :loading="checking" @click="checkApi"><el-icon><Monitor /></el-icon>检查后端连接</el-button>
        </div>

        <template v-if="activeMenu === 'sessions'">
          <el-row :gutter="16" class="overview-grid">
            <el-col v-for="metric in metrics" :key="metric.label" :xs="24" :sm="12" :lg="6">
              <el-card shadow="never" class="metric-card">
                <div class="metric-icon" :class="`metric-icon--${metric.color}`"><el-icon><component :is="metric.icon" /></el-icon></div>
                <div class="metric-copy"><span class="metric-label">{{ metric.label }}</span><strong>{{ metric.value }}</strong><small>{{ metric.trend }}</small></div>
              </el-card>
            </el-col>
          </el-row>

          <el-card shadow="never" class="session-card">
            <template #header>
              <div class="card-header">
                <div><strong>最近会话</strong><span>共 {{ filteredSessions.length }} 条记录</span></div>
                <el-input v-model="searchText" clearable placeholder="搜索会话" :prefix-icon="Search" class="search-input" />
              </div>
            </template>
            <el-table v-loading="loadingSessions" :data="filteredSessions" class="session-table" row-class-name="session-row" @row-click="openSession">
              <el-table-column label="会话" min-width="300">
                <template #default="{ row }"><div class="session-title"><span class="session-dot" :class="`session-dot--${row.status}`"></span><div><strong>{{ row.title }}</strong><small>{{ row.id }}</small></div></div></template>
              </el-table-column>
              <el-table-column prop="source" label="来源" width="125" />
              <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="statusType(row.status)" effect="light" round>{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
              <el-table-column label="事件数" width="100"><template #default="{ row }"><span class="event-count">{{ row.events }}</span></template></el-table-column>
              <el-table-column label="编译进度" width="170"><template #default="{ row }"><el-progress :percentage="row.progress" :status="row.progress === 100 ? 'success' : undefined" :stroke-width="7" /></template></el-table-column>
              <el-table-column prop="updated" label="最近更新" width="120" />
            </el-table>
          </el-card>
        </template>

        <template v-else-if="activeMenu === 'compile-runs'">
          <el-row :gutter="16" class="overview-grid">
            <el-col :xs="24" :sm="8"><el-card shadow="never" class="metric-card"><div class="metric-icon metric-icon--purple"><el-icon><Tickets /></el-icon></div><div class="metric-copy"><span class="metric-label">整理次数</span><strong>{{ compileRuns.length }}</strong><small>全部会话</small></div></el-card></el-col>
            <el-col :xs="24" :sm="8"><el-card shadow="never" class="metric-card"><div class="metric-icon metric-icon--green"><el-icon><Document /></el-icon></div><div class="metric-copy"><span class="metric-label">生成知识条目</span><strong>{{ compileRuns.reduce((sum, run) => sum + run.knowledgeCount, 0) }}</strong><small>模拟统计</small></div></el-card></el-col>
            <el-col :xs="24" :sm="8"><el-card shadow="never" class="metric-card"><div class="metric-icon metric-icon--orange"><el-icon><Clock /></el-icon></div><div class="metric-copy"><span class="metric-label">整理中</span><strong>{{ compileRuns.filter((run) => run.status === 'running').length }}</strong><small>当前任务</small></div></el-card></el-col>
          </el-row>

          <el-card shadow="never" class="session-card compile-card">
            <template #header>
              <div class="card-header">
                <div><strong>整理记录</strong><span>共 {{ filteredCompileRuns.length }} 条记录</span></div>
                <el-select v-model="compileSessionFilter" clearable filterable placeholder="按会话筛选" class="session-filter">
                  <el-option v-for="run in compileRuns" :key="run.sessionId" :label="run.sessionTitle" :value="run.sessionId" />
                </el-select>
              </div>
            </template>
            <el-table v-loading="loadingCompileRuns" :data="filteredCompileRuns" class="session-table" @row-click="openCompileRun">
              <el-table-column label="整理记录" min-width="300"><template #default="{ row }"><div class="session-title"><span class="run-status-dot" :class="`run-status-dot--${row.status}`"></span><div><strong>{{ row.sessionTitle }}</strong><small>{{ row.id }} · {{ row.sessionId }}</small></div></div></template></el-table-column>
              <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="compileStatusType(row.status)" effect="light" round>{{ compileStatusLabel(row.status) }}</el-tag></template></el-table-column>
              <el-table-column label="处理范围" width="150"><template #default="{ row }"><span class="version-range">v{{ row.fromVersion }} → v{{ row.toVersion }}</span></template></el-table-column>
              <el-table-column label="知识条目" width="110"><template #default="{ row }"><span class="event-count">{{ row.knowledgeCount }}</span></template></el-table-column>
              <el-table-column prop="startedAt" label="开始时间" width="125" />
              <el-table-column prop="duration" label="耗时" width="110" />
            </el-table>
          </el-card>
        </template>

        <template v-else>
          <el-card shadow="never" class="settings-card">
            <el-tabs v-model="settingsTab" class="settings-tabs">
              <el-tab-pane label="知识整理" name="knowledge">
                <div class="settings-tab-header">
                  <div>
                    <strong>知识整理规则</strong>
                    <span>{{ knowledgeSettingsEditing ? '编辑模式：修改后点击保存' : '当前为只读状态' }}</span>
                  </div>
                  <div class="settings-tab-actions">
                    <el-button v-if="!knowledgeSettingsEditing" type="primary" plain @click="startKnowledgeSettingsEditing"><el-icon><Edit /></el-icon>编辑</el-button>
                    <template v-else>
                      <el-button @click="cancelKnowledgeSettingsEditing">取消</el-button>
                      <el-button type="primary" :loading="savingKnowledgeSettings" @click="saveKnowledgeSettings">保存设置</el-button>
                    </template>
                  </div>
                </div>
                <el-form label-position="top" class="settings-form" :class="{ 'settings-form--readonly': !knowledgeSettingsEditing }">
                  <el-form-item label="模型选择">
                    <div class="cascading-model-select">
                      <el-select v-model="selectedProviderId" class="provider-select" placeholder="选择供应商" :disabled="!knowledgeSettingsEditing" @change="handleProviderChange">
                        <el-option v-for="provider in providers" :key="provider.id" :label="provider.name" :value="provider.id" />
                      </el-select>
                      <span class="select-arrow">/</span>
                      <el-select v-model="selectedModel" class="model-select" popper-class="model-select-popper" placeholder="选择模型" :disabled="!knowledgeSettingsEditing || !selectedProvider">
                        <el-option v-for="model in providerModels" :key="model" :label="model" :value="model" />
                      </el-select>
                    </div>
                    <span class="form-help">先选择供应商，再选择该供应商已获取的模型。</span>
                  </el-form-item>
                  <el-form-item label="整理间隔">
                    <div class="interval-input"><el-input-number v-model="compileInterval" :min="1" :step="1" controls-position="right" :disabled="!knowledgeSettingsEditing" /><span>分钟</span></div>
                    <span class="form-help">会话超过该空闲时间后，进入待整理队列。</span>
                  </el-form-item>
                  <el-form-item label="整理要求">
                    <el-input v-model="compilePrompt" type="textarea" :rows="8" placeholder="输入知识整理 Prompt" :disabled="!knowledgeSettingsEditing" />
                    <span class="form-help">这些要求会作为编译器的基础提示词。</span>
                  </el-form-item>
                </el-form>
              </el-tab-pane>
              <el-tab-pane label="模型供应商" name="providers">
                <div class="provider-toolbar"><div><strong>模型供应商</strong><span>管理用于知识整理的模型接口</span></div><el-button type="primary" @click="openProviderCreate"><el-icon><Plus /></el-icon>新增供应商</el-button></div>
                <el-table :data="providers" class="provider-table">
                  <el-table-column label="供应商" min-width="190"><template #default="{ row }"><div class="provider-name"><span class="provider-mark">{{ row.name.slice(0, 1) }}</span><div><strong>{{ row.name }}</strong><small>{{ row.type }}</small></div></div></template></el-table-column>
                  <el-table-column prop="baseUrl" label="Base URL" min-width="270" />
                  <el-table-column label="可用模型" min-width="220"><template #default="{ row }"><div class="model-tags"><el-tag v-for="model in row.models" :key="model" size="small" effect="plain">{{ model }}</el-tag></div></template></el-table-column>
                  <el-table-column prop="modelsFetchedAt" label="最近获取" width="120" />
                  <el-table-column label="操作" width="130" fixed="right"><template #default="{ row }"><el-button text type="primary" @click="openProviderEdit(row)"><el-icon><Edit /></el-icon></el-button><el-button text type="danger" @click="deleteProvider(row)"><el-icon><Delete /></el-icon></el-button></template></el-table-column>
                </el-table>
              </el-tab-pane>
            </el-tabs>
          </el-card>
        </template>

        <p class="api-status">后端状态：{{ apiStatus }} <span v-if="apiError" class="api-error">{{ apiError }}</span><span v-else class="mock-hint">数据来自后端 API</span></p>
      </el-main>
    </el-container>

    <el-drawer v-model="drawerVisible" :title="selectedSession?.title || '会话事件'" :size="isCollapsed ? 'calc(100vw - 72px)' : 'calc(100vw - 240px)'" direction="rtl" class="event-drawer">
      <template #header>
        <div class="drawer-heading">
          <div><strong>{{ selectedSession?.title || '会话事件' }}</strong><span>{{ selectedSession?.id }}</span></div>
          <el-tag v-if="selectedSession" :type="statusType(selectedSession.status)" effect="light" round>{{ statusLabel(selectedSession.status) }}</el-tag>
        </div>
      </template>
      <div class="drawer-summary">
        <div><span>来源</span><strong>{{ selectedSession?.source }}</strong></div>
        <div><span>事件数</span><strong>{{ selectedSession?.events }}</strong></div>
        <div><span>最近更新</span><strong>{{ selectedSession?.updated }}</strong></div>
      </div>
      <el-divider content-position="left">事件时间线</el-divider>
      <div class="event-timeline">
        <div v-if="loadingEvents" class="events-loading"><el-skeleton :rows="6" animated /></div>
      <el-empty v-else-if="selectedEvents.length === 0" description="暂无事件" />
      <div v-else v-for="event in selectedEvents" :key="`${event.time}-${event.type}`" class="event-item">
          <div class="event-marker" :class="`event-marker--${event.styleType}`">{{ eventIcon(event.type) }}</div>
          <div class="event-bubble" :class="`event-bubble--${event.styleType}`">
            <div class="event-meta"><strong>{{ event.label }}</strong><time>{{ event.time }}</time></div>
            <p>{{ event.content }}</p>
            <div v-if="event.type === 'tool_call'" class="tool-call-details">
              <div class="tool-call-field"><span>工具</span><code>{{ event.tool || '未命名工具' }}</code></div>
              <div class="tool-call-field"><span>参数</span><pre>{{ event.arguments }}</pre></div>
            </div>
            <code v-else-if="event.tool">{{ event.tool }}</code>
          </div>
        </div>
      </div>
    </el-drawer>
    <el-drawer v-model="compileDrawerVisible" :size="isCollapsed ? 'calc(100vw - 72px)' : 'calc(100vw - 240px)'" direction="rtl" class="compile-drawer">
      <template #header>
        <div class="drawer-heading">
          <div><strong>{{ selectedCompileRun?.sessionTitle || '整理记录' }}</strong><span>{{ selectedCompileRun?.id }} · {{ selectedCompileRun?.sessionId }}</span></div>
          <el-tag v-if="selectedCompileRun" :type="compileStatusType(selectedCompileRun.status)" effect="light" round>{{ compileStatusLabel(selectedCompileRun.status) }}</el-tag>
        </div>
      </template>
      <template v-if="selectedCompileRun">
        <el-skeleton v-if="loadingCompileDetail" :rows="6" animated />
        <template v-else>
        <div class="compile-run-summary">
          <div><span>整理范围</span><strong>v{{ selectedCompileRun.fromVersion }} → v{{ selectedCompileRun.toVersion }}</strong></div>
          <div><span>开始时间</span><strong>{{ selectedCompileRun.startedAt }}</strong></div>
          <div><span>耗时</span><strong>{{ selectedCompileRun.duration }}</strong></div>
          <div><span>生成条目</span><strong>{{ selectedCompileRun.knowledgeCount }}</strong></div>
        </div>
        <el-divider content-position="left">整理结果</el-divider>
        <section class="result-section">
          <div class="result-section-title"><span class="result-icon result-icon--summary">∑</span><strong>结果总结</strong></div>
          <p class="result-summary">{{ selectedCompileRun.summary }}</p>
        </section>
        <section class="result-section">
          <div class="result-section-title"><span class="result-icon result-icon--knowledge">✓</span><strong>识别出的知识条目</strong><span class="result-count">{{ selectedCompileRun.knowledge.length }}</span></div>
          <el-empty v-if="selectedCompileRun.knowledge.length === 0" description="本次未生成知识条目" />
          <ul v-else class="knowledge-result-list"><li v-for="item in selectedCompileRun.knowledge" :key="item"><span class="knowledge-bullet">•</span>{{ item }}</li></ul>
        </section>
        </template>
      </template>
    </el-drawer>

    <el-drawer v-model="providerDrawerVisible" :title="providerDrawerMode === 'create' ? '新增模型供应商' : '修改模型供应商'" :size="isCollapsed ? 'calc(100vw - 72px)' : 'calc(100vw - 240px)'" direction="rtl" class="provider-drawer">
      <el-form label-position="top" class="provider-form">
        <el-form-item label="供应商名称"><el-input v-model="providerForm.name" placeholder="例如：OpenAI" /></el-form-item>
        <el-form-item label="接口类型"><el-select v-model="providerForm.type" class="full-width"><el-option label="Completions" value="completions" /><el-option label="Responses" value="responses" /><el-option label="Anthropic" value="anthropic" /></el-select></el-form-item>
        <el-form-item label="Base URL"><el-input v-model="providerForm.baseUrl" placeholder="https://api.example.com/v1" /></el-form-item>
        <el-form-item label="API Key"><el-input v-model="providerForm.apiKey" type="password" show-password placeholder="输入 API Key" /></el-form-item>
        <el-form-item label="可用模型列表">
          <div class="models-fetch-row"><el-button type="primary" plain :loading="fetchingProviderModels" @click="fetchProviderModels"><el-icon><Connection /></el-icon>获取</el-button><span v-if="providerModelsFetched" class="fetch-success">已获取 {{ providerForm.models.length }} 个模型</span><span v-else class="form-help">必须先获取模型列表才能保存</span></div>
          <div v-if="providerForm.models.length" class="readonly-model-list"><el-tag v-for="model in providerForm.models" :key="model" effect="plain">{{ model }}</el-tag></div>
        </el-form-item>
        <el-alert v-if="providerDrawerError" :title="providerDrawerError" type="error" :closable="false" show-icon />
        <div class="drawer-actions"><el-button @click="providerDrawerVisible = false">取消</el-button><el-button type="primary" :disabled="!providerModelsFetched" @click="saveProvider">保存</el-button></div>
      </el-form>
    </el-drawer>
  </el-container>
</template>
