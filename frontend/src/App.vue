<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  ChatDotRound,
  Clock,
  Connection,
  DataAnalysis,
  Document,
  Fold,
  FullScreen,
  Menu as MenuIcon,
  Monitor,
  Search,
  User,
} from '@element-plus/icons-vue'

const isCollapsed = ref(false)
const activeMenu = ref('sessions')
const apiStatus = ref('未检查')
const checking = ref(false)
const loadingSessions = ref(false)
const loadingEvents = ref(false)
const apiError = ref('')
const searchText = ref('')
const drawerVisible = ref(false)
const selectedSession = ref(null)
const selectedEvents = ref([])

const sessions = ref([])


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

const pageTitle = computed(() => activeMenu.value === 'sessions' ? '会话列表' : '会话列表')

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
  return {
    type,
    label: eventLabel(type),
    time: formatEventTime(event.eventTimestamp || event.receivedAt),
    content: extractEventContent(data, payload),
    tool: data.tool_name || data.tool || data.name || '',
  }
}

function parsePayload(payloadJson) {
  try {
    const normalized = String(payloadJson || '{}').trim().replace(/\\n\s*$/, '')
    return JSON.parse(normalized)
  } catch { return {} }
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

onMounted(loadSessions)

function toggleSidebar() {
  isCollapsed.value = !isCollapsed.value
}

function statusLabel(status) {
  return { active: '采集中', stale: '待编译', compiling: '编译中', compiled: '已完成' }[status] || status
}

function statusType(status) {
  return { active: 'success', stale: 'warning', compiling: 'primary', compiled: 'info' }[status] || 'info'
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
            <p class="page-description">查看采集到的 Agent 会话，以及知识编译进度。</p>
          </div>
          <el-button type="primary" :loading="checking" @click="checkApi"><el-icon><Monitor /></el-icon>检查后端连接</el-button>
        </div>

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
          <div class="event-marker" :class="`event-marker--${event.type}`">{{ eventIcon(event.type) }}</div>
          <div class="event-bubble" :class="`event-bubble--${event.type}`">
            <div class="event-meta"><strong>{{ event.label }}</strong><time>{{ event.time }}</time></div>
            <p>{{ event.content }}</p>
            <code v-if="event.tool">{{ event.tool }}</code>
          </div>
        </div>
      </div>
    </el-drawer>
  </el-container>
</template>
