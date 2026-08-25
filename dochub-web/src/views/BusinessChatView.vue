<template>
  <section class="chat-shell">
    <!-- 会话侧边栏 -->
    <aside class="chat-sidebar" :class="{ open: sidebarOpen }">
      <div class="sidebar-head">
        <span class="sidebar-brand">会话记录</span>
        <button class="sidebar-close" type="button" @click="sidebarOpen = false">
          <XMarkIcon class="icon" />
        </button>
      </div>

      <button class="new-chat" type="button" :disabled="isStreaming" @click="startNewConversation">
        <PlusIcon class="icon" />
        新对话
      </button>

      <div class="session-list">
        <article
          v-for="session in sortedSessions"
          :key="session.conversationId"
          class="session-card"
          :class="{ active: session.conversationId === currentConversationId }"
        >
          <button
            class="session-select"
            type="button"
            :disabled="isStreaming"
            @click="loadConversation(session.conversationId)"
          >
            <div class="session-row">
              <span class="session-title">{{ sessionTitle(session) }}</span>
              <span v-if="session.running" class="running-dot">运行中</span>
            </div>
            <p class="session-preview">{{ sessionPreview(session) }}</p>
            <div class="session-meta">
              <span>{{ formatTime(session.updatedAt) }}</span>
              <span>{{ sessionMessageCount(session) }} 条</span>
            </div>
          </button>
          <button
            class="session-delete"
            type="button"
            title="删除会话"
            :disabled="isStreaming"
            @click.stop="deleteConversation(session.conversationId)"
          >
            <TrashIcon class="icon" />
          </button>
        </article>

        <div v-if="!loadingSessions && !sortedSessions.length" class="sidebar-empty">
          <p>还没有历史会话</p>
        </div>
      </div>
    </aside>

    <div v-if="sidebarOpen" class="sidebar-mask" @click="sidebarOpen = false"></div>

    <!-- 主聊天区 -->
    <main class="chat-main">
      <header class="chat-topbar">
        <div class="topbar-left">
          <button class="topbar-menu" type="button" @click="sidebarOpen = true">
            <Bars3Icon class="icon" />
          </button>
          <div class="topbar-session">
            <h2>{{ activeSessionTitle }}</h2>
            <span class="mode-pill" :class="{ auto: isAutoDocumentMode, open: !isDocumentMode && !isAutoDocumentMode }">
              {{ isDocumentMode ? '文档问答' : isAutoDocumentMode ? '自动知识问答' : '开放式提问' }}
            </span>
          </div>
        </div>
        <div class="topbar-actions">
          <a class="admin-entry" :href="adminConsoleHref" target="_blank" rel="noopener noreferrer">
            <BuildingOffice2Icon class="icon" />
            管理后台
          </a>
        </div>
      </header>

      <div class="messages-panel" ref="messagesPanelRef">
          <div v-if="pageError" class="notice error">{{ pageError }}</div>
          <div v-if="loadingConversation" class="notice">正在加载会话内容...</div>

          <div v-if="!displayMessages.length && !loadingConversation" class="empty-state">
            <div class="empty-mark">
              <SparklesIcon class="icon" />
            </div>
            <h3>让零散问题落成可执行方案</h3>
            <p>结合制度问答、文档理解与知识检索，把想法整理成清晰结论和下一步动作</p>
            <div class="prompt-grid">
              <button type="button" class="prompt-chip" @click="sendMessage('请先介绍一下你能帮我做哪些事情，并给出几个典型使用场景')">
                助手能做什么
              </button>
              <button type="button" class="prompt-chip" @click="sendMessage('请帮我把一个复杂问题拆成清晰的分析步骤，并给出执行建议')">
                拆解复杂问题
              </button>
            </div>
          </div>

          <Chat
            v-for="message in displayMessages"
            :key="message.id"
            :message="message"
            :is-streaming="isStreaming && message.id === currentAssistantMessageId"
            :show-recommendations="message.id === latestAssistantDisplayId"
            @recommend="sendMessage"
          />
        </div>

        <!-- 右侧上下文栏 -->
        <aside class="chat-rail">
          <div class="rail-block">
            <span class="rail-label">当前模式</span>
            <div class="rail-mode">{{ isDocumentMode ? '当前文档问答' : isAutoDocumentMode ? '自动知识问答' : '开放式提问' }}</div>
          </div>

          <div class="rail-block">
            <span class="rail-label">能力提示</span>
            <p v-if="isDocumentMode" class="rail-text">仅从选定的文档中检索证据作答，引用可追溯。</p>
            <p v-else-if="isAutoDocumentMode" class="rail-text">系统先自动预选候选文档，再走稳定检索链路。</p>
            <p v-else class="rail-text">
              <span class="rail-skill">✦</span>
              开放式提问会根据你的描述自动匹配并使用已安装技能。
            </p>
          </div>

          <div v-if="!isDocumentMode && latestAssistantRouteExplain?.topDocument" class="rail-block">
            <span class="rail-label">最近知识候选</span>
            <p class="rail-text">
              {{ latestAssistantRouteExplain.topDocument.documentName || latestAssistantRouteExplain.topDocument.documentId }}
            </p>
          </div>
      </aside>

      <!-- 输入区 -->
      <footer class="composer-wrap">
        <div class="composer-card">
          <div class="mode-switch" role="tablist" aria-label="聊天回答模式">
            <button
              class="mode-button"
              :class="{ active: isDocumentMode }"
              type="button"
              :disabled="isStreaming"
              @click="setChatMode(CHAT_MODES.DOCUMENT)"
            >
              当前文档问答
            </button>
            <button
              class="mode-button"
              :class="{ active: isAutoDocumentMode }"
              type="button"
              :disabled="isStreaming"
              @click="setChatMode(CHAT_MODES.AUTO_DOCUMENT)"
            >
              自动知识问答
            </button>
            <button
              class="mode-button"
              :class="{ active: !isDocumentMode && !isAutoDocumentMode }"
              type="button"
              :disabled="isStreaming"
              @click="setChatMode(CHAT_MODES.OPEN_CHAT)"
            >
              开放式提问
            </button>
          </div>

          <template v-if="!isDocumentMode && !isAutoDocumentMode">
            <div class="scope-row open-chat-mode-row">
              <span class="open-chat-mode-label">回答方式</span>
              <button
                class="mode-button mode-button-mini"
                :class="{ active: openChatMode === OPEN_CHAT_MODES.REACT_AGENT }"
                type="button"
                :disabled="isStreaming"
                @click="openChatMode = OPEN_CHAT_MODES.REACT_AGENT"
              >
                ReAct 自主执行
              </button>
              <button
                class="mode-button mode-button-mini"
                :class="{ active: openChatMode === OPEN_CHAT_MODES.PLAN_AND_EXECUTE }"
                type="button"
                :disabled="isStreaming"
                @click="openChatMode = OPEN_CHAT_MODES.PLAN_AND_EXECUTE"
              >
                计划执行
              </button>
            </div>
          </template>

          <template v-if="isDocumentMode">
            <div class="scope-row">
              <select
                v-model="selectedDocumentId"
                class="scope-select"
                :disabled="isStreaming || loadingDocumentOptions"
                @change="handleDocumentScopeChange"
              >
                <option value="">请选择文档…</option>
                <option v-for="item in documentOptions" :key="item.documentId" :value="item.documentId">
                  {{ item.documentName }}
                </option>
              </select>
              <span v-if="selectedDocumentName" class="scope-pill">已选：{{ selectedDocumentName }}</span>
              <span v-else-if="!loadingDocumentOptions" class="scope-pill warn">请先选择文档再提问</span>
            </div>
          </template>

          <!-- /skills 技能选择 -->
          <div v-if="showSkillPicker" class="skill-picker">
            <div class="skill-picker-head">
              <span>选择要使用的技能（将强制注入该技能指令）</span>
              <button class="skill-picker-close" type="button" @click="showSkillPicker = false">×</button>
            </div>
            <div v-if="availableSkills.length" class="skill-picker-list">
              <button
                v-for="skill in availableSkills"
                :key="skill.skillName"
                class="skill-picker-item"
                type="button"
                @click="pickSkill(skill)"
              >
                <span class="sp-name">{{ skill.displayName || skill.skillName }}</span>
                <span class="sp-desc">{{ skill.description || '' }}</span>
              </button>
            </div>
            <div v-else class="skill-picker-empty">没有已启用的技能，请先到技能列表安装并启用。</div>
          </div>

          <div v-if="forcedSkillName" class="forced-skill-bar">
            <span>✦ 已指定技能：{{ forcedSkillName }}</span>
            <button class="forced-skill-clear" type="button" @click="clearForcedSkill">清除</button>
          </div>

          <div class="composer-input-row">
            <textarea
              ref="composerRef"
              v-model="userInput"
              class="composer-input"
              rows="1"
              :placeholder="composerPlaceholder"
              :disabled="isStreaming"
              @input="resizeComposer"
              @keydown="handleComposerKeydown"
            ></textarea>
            <button
              v-if="isStreaming"
              class="stop-button"
              type="button"
              :disabled="isStopping"
              @click="stopStreaming"
            >
              <StopIcon class="icon" />
              {{ isStopping ? '停止中' : '停止' }}
            </button>
            <button
              class="send-button"
              type="button"
              :disabled="isStreaming || !canSend"
              @click="sendMessage()"
            >
              <PaperAirplaneIcon class="icon" />
              发送
            </button>
          </div>

          <div class="composer-hint">按 Enter 发送，Shift + Enter 换行<span v-if="isStreaming" class="streaming-badge">正在生成回答…</span></div>
        </div>
      </footer>
    </main>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Bars3Icon,
  BuildingOffice2Icon,
  PaperAirplaneIcon,
  PlusIcon,
  SparklesIcon,
  StopIcon,
  TrashIcon,
  XMarkIcon
} from '@heroicons/vue/24/outline'
import Chat from '../components/Chat.vue'
import { APIError, chatApi, createConversationId, manageApi, skillApi } from '../api/api'
import { hasCode } from '../utils/manageFormat'
import { buildChatRouteExplain, buildRouteTraceLookup } from '../utils/knowledgeRoute'

const router = useRouter()
const adminConsoleHref = router.resolve({
  name: 'AdminLogin',
  query: {
    redirect: '/admin/dashboard'
  }
}).href
const composerRef = ref(null)
const messagesPanelRef = ref(null)
const sidebarOpen = ref(false)
const sessions = ref([])
const currentConversationId = ref('')
const displayMessages = ref([])
const userInput = ref('')
const loadingSessions = ref(false)
const loadingConversation = ref(false)
const loadingDocumentOptions = ref(false)
const isStreaming = ref(false)
const isStopping = ref(false)
const pageError = ref('')
const currentStreamHandle = ref(null)
const currentAssistantMessageId = ref('')
const documentOptions = ref([])
const selectedDocumentId = ref('')
const selectedDocumentName = ref('')
const CHAT_MODES = Object.freeze({
  DOCUMENT: 'DOCUMENT',
  AUTO_DOCUMENT: 'AUTO_DOCUMENT',
  OPEN_CHAT: 'OPEN_CHAT'
})
// 开放式提问的回答方式：ReAct 自主执行 / 计划-执行
const OPEN_CHAT_MODES = Object.freeze({
  REACT_AGENT: 'REACT_AGENT',
  PLAN_AND_EXECUTE: 'PLAN_AND_EXECUTE'
})
const openChatMode = ref(OPEN_CHAT_MODES.REACT_AGENT)
const forcedSkillName = ref('')
const showSkillPicker = ref(false)
const availableSkills = ref([])
const chatMode = ref(CHAT_MODES.OPEN_CHAT)

const isDocumentMode = computed(() => chatMode.value === CHAT_MODES.DOCUMENT)
const isAutoDocumentMode = computed(() => chatMode.value === CHAT_MODES.AUTO_DOCUMENT)
const canSend = computed(() => {
  if (!userInput.value.trim()) {
    return false
  }
  // 文档问答模式的边界应该在界面层就明确暴露出来：
  // 没选文档时，发送按钮直接禁用，而不是让后端再去“猜”该怎么兜底。
  return !isDocumentMode.value || Boolean(selectedDocumentId.value)
})
const composerPlaceholder = computed(() => {
  if (isAutoDocumentMode.value) {
    return '请输入你的问题，系统会自动选择最相关的知识文档，例如：上线观察与值班规则中观察时长有哪些？'
  }
  return isDocumentMode.value
    ? '请输入关于当前文档的问题，例如：这份培训手册里的试用期规则是怎么规定的？'
    : '请输入你的问题，例如：帮我分析一下这个智能对话方案应该怎么拆分模块。'
})

const sortedSessions = computed(() => {
  return [...sessions.value].sort((left, right) => {
    const leftTime = left.updatedAt ? new Date(left.updatedAt).getTime() : 0
    const rightTime = right.updatedAt ? new Date(right.updatedAt).getTime() : 0
    return rightTime - leftTime
  })
})

const activeSessionTitle = computed(() => {
  const session = sessions.value.find((item) => item.conversationId === currentConversationId.value)
  return session ? sessionTitle(session) : '新的对话'
})
const latestAssistantDisplayId = computed(() => {
  const message = [...displayMessages.value].reverse().find((item) => item.role === 'assistant')
  return message?.id || ''
})
const latestAssistantRouteExplain = computed(() => {
  const message = [...displayMessages.value].reverse().find((item) => item.role === 'assistant' && item.routeExplain)
  return message?.routeExplain || null
})

function sessionTitle(session) {
  const latestUserMessage = session.latestUserMessage || latestExchangeQuestion(session)
  const latestAssistantMessage = session.latestAssistantMessage || latestExchangeAnswer(session)
  return truncate(latestUserMessage || latestAssistantMessage || '新的对话', 22)
}

function sessionPreview(session) {
  const latestAssistantMessage = session.latestAssistantMessage || latestExchangeAnswer(session)
  const latestUserMessage = session.latestUserMessage || latestExchangeQuestion(session)
  return truncate(latestAssistantMessage || latestUserMessage || '还没有消息内容', 48)
}

function sessionMessageCount(session) {
  if (session?.messageCount) {
    return session.messageCount
  }
  return mapExchangesToMessages(session?.exchanges || []).length
}

function latestExchangeQuestion(session) {
  const exchanges = session?.exchanges || []
  for (let index = exchanges.length - 1; index >= 0; index -= 1) {
    const question = exchanges[index]?.question
    if (question) {
      return question
    }
  }
  return ''
}

function latestExchangeAnswer(session) {
  const exchanges = session?.exchanges || []
  for (let index = exchanges.length - 1; index >= 0; index -= 1) {
    const answer = exchanges[index]?.answer
    if (answer) {
      return answer
    }
  }
  return ''
}

function truncate(value, maxLength) {
  if (!value) {
    return ''
  }
  return value.length > maxLength ? `${value.slice(0, maxLength)}...` : value
}

function formatTime(value) {
  if (!value) {
    return '刚刚'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '刚刚'
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

function createUserMessage(question) {
  return {
    id: `user-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    role: 'user',
    content: question,
    createdAt: new Date().toISOString()
  }
}

function createAssistantMessage() {
  return {
    id: `assistant-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    role: 'assistant',
    content: '',
    thinkingSteps: [],
    references: [],
    recommendations: [],
    skills: [],
    usedTools: [],
    status: 'RUNNING',
    statusText: '',
    errorMessage: '',
    firstResponseTimeMs: null,
    totalResponseTimeMs: null,
    debugTrace: null,
    routeExplain: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  }
}

// 后端把每一轮对话结构化成 exchange，这里把 exchange 展开成用户消息 + 助手消息，
// 这样前端展示层就不需要感知数据库 record 的结构细节。
function mapExchangesToMessages(exchanges = [], routeTraceLookup = {}) {
  return exchanges.flatMap((exchange) => {
    const createdAt = exchange.createdAt || exchange.createTime || null
    const updatedAt = exchange.updatedAt || exchange.editTime || createdAt
    const userMessage = {
      id: `exchange-${exchange.exchangeId}-user`,
      role: 'user',
      content: exchange.question || '',
      createdAt
    }

    const assistantMessage = {
      id: `exchange-${exchange.exchangeId}-assistant`,
      role: 'assistant',
      content: exchange.answer || '',
      thinkingSteps: exchange.thinkingSteps || [],
      references: exchange.references || [],
      recommendations: exchange.recommendations || [],
      skills: exchange.skills || [],
      usedTools: exchange.usedTools || [],
      status: exchange.status || '',
      statusText: '',
      errorMessage: exchange.errorMessage || '',
      firstResponseTimeMs: exchange.firstResponseTimeMs,
      totalResponseTimeMs: exchange.totalResponseTimeMs,
      debugTrace: exchange.debugTrace || null,
      routeExplain: buildChatRouteExplain(routeTraceLookup[String(exchange.exchangeId)]),
      createdAt,
      updatedAt
    }

    return [userMessage, assistantMessage]
  })
}

function upsertSession(session) {
  const index = sessions.value.findIndex((item) => item.conversationId === session.conversationId)
  if (index === -1) {
    sessions.value = [session, ...sessions.value]
    return
  }

  const nextSessions = [...sessions.value]
  nextSessions.splice(index, 1, session)
  sessions.value = nextSessions
}

// SSE 流里拿到的是增量事件，页面需要把它们持续合并进“当前这条助手消息”。
function updateCurrentAssistant(mutator) {
  const index = displayMessages.value.findIndex((message) => message.id === currentAssistantMessageId.value)
  if (index === -1) {
    return
  }

  const nextMessage = {
    ...displayMessages.value[index]
  }
  mutator(nextMessage)

  const nextMessages = [...displayMessages.value]
  nextMessages.splice(index, 1, nextMessage)
  displayMessages.value = nextMessages
}

async function scrollToBottom() {
  await nextTick()
  if (messagesPanelRef.value) {
    messagesPanelRef.value.scrollTop = messagesPanelRef.value.scrollHeight
  }
}

function resizeComposer() {
  nextTick(() => {
    if (!composerRef.value) {
      return
    }
    composerRef.value.style.height = 'auto'
    composerRef.value.style.height = `${Math.min(composerRef.value.scrollHeight, 220)}px`
  })
}

function focusComposer() {
  nextTick(() => {
    composerRef.value?.focus()
    resizeComposer()
  })
}

async function refreshSessions() {
  loadingSessions.value = true

  try {
    const data = await chatApi.listSessions()
    sessions.value = Array.isArray(data) ? data : []
  } catch (error) {
    pageError.value = normalizeError(error, '加载会话列表失败')
  } finally {
    loadingSessions.value = false
  }
}

async function refreshDocumentOptions() {
  loadingDocumentOptions.value = true
  try {
    const data = await chatApi.listKnowledgeDocumentOptions()
    documentOptions.value = Array.isArray(data) ? data : []
    syncSelectedDocumentName()
  } catch (error) {
    pageError.value = normalizeError(error, '加载可选知识文档失败')
  } finally {
    loadingDocumentOptions.value = false
  }
}

async function loadConversation(conversationId) {
  if (!conversationId || isStreaming.value) {
    return
  }

  loadingConversation.value = true
  pageError.value = ''

  try {
    const [sessionResult, routeTraceResult] = await Promise.allSettled([
      chatApi.getSession(conversationId),
      manageApi.queryKnowledgeRouteTracePage({
        conversationId,
        pageNo: '1',
        pageSize: '200'
      })
    ])

    if (sessionResult.status !== 'fulfilled') {
      throw sessionResult.reason
    }

    if (routeTraceResult.status === 'rejected') {
      console.warn('加载知识路由追踪失败', routeTraceResult.reason)
    }

    const session = sessionResult.value
    const routeTraceLookup = routeTraceResult.status === 'fulfilled'
      ? buildRouteTraceLookup(routeTraceResult.value?.records || [])
      : {}

    currentConversationId.value = conversationId
    displayMessages.value = mapExchangesToMessages(session.exchanges || [], routeTraceLookup)
    upsertSession(session)
    applySessionScope(session)
    sidebarOpen.value = false
    await scrollToBottom()
  } catch (error) {
    pageError.value = normalizeError(error, '加载会话详情失败')
  } finally {
    loadingConversation.value = false
  }
}

async function deleteConversation(conversationId) {
  if (!conversationId || isStreaming.value) {
    return
  }

  try {
    await chatApi.deleteSession(conversationId)
    sessions.value = sessions.value.filter((item) => item.conversationId !== conversationId)

    if (currentConversationId.value === conversationId) {
      const nextSession = sortedSessions.value[0]
      if (nextSession) {
        await loadConversation(nextSession.conversationId)
      } else {
        startNewConversation()
      }
    }
  } catch (error) {
    pageError.value = normalizeError(error, '删除会话失败')
  }
}

function startNewConversation() {
  if (isStreaming.value) {
    return
  }

  currentConversationId.value = createConversationId()
  displayMessages.value = []
  userInput.value = ''
  pageError.value = ''
  sidebarOpen.value = false
  syncSelectedDocumentName()
  focusComposer()
}

function applySessionScope(session) {
  // 会话详情回放时，前端要完整恢复“这条会话当时走的是哪一种产品能力”。
  // 这样学习者切回历史会话后，看到的模式开关、文档范围和后端执行结果才是一致的。
  chatMode.value = session?.chatMode || CHAT_MODES.OPEN_CHAT
  selectedDocumentId.value = session?.selectedDocumentId || ''
  selectedDocumentName.value = session?.selectedDocumentName || ''
  syncSelectedDocumentName()
}

function syncSelectedDocumentName() {
  if (!selectedDocumentId.value) {
    selectedDocumentName.value = ''
    return
  }
  const option = documentOptions.value.find((item) => item.documentId === selectedDocumentId.value)
  if (option) {
    selectedDocumentName.value = option.documentName
  }
}

function handleDocumentScopeChange() {
  syncSelectedDocumentName()
  if (isDocumentMode.value && displayMessages.value.length > 0 && !isStreaming.value) {
    startNewConversation()
  }
}

function setChatMode(nextMode) {
  if (isStreaming.value || chatMode.value === nextMode) {
    return
  }
  chatMode.value = nextMode
  pageError.value = ''

  // 模式切换代表“回答边界”已经改变。
  // 为了避免同一个 conversationId 混入两种完全不同的链路，
  // 这里直接起一个新会话，比在老会话里继续缝补更适合教学项目。
  if (displayMessages.value.length > 0) {
    startNewConversation()
  }
}

function handleComposerKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

// 历史会话是完整快照，流式回答是增量事件，这里统一负责把增量事件映射到展示态。
function applyStreamEvent(event) {
  updateCurrentAssistant((message) => {
    if (event.type === 'text') {
      message.content += event.content || ''
    }

    if (event.type === 'thinking' && event.content && !message.thinkingSteps.includes(event.content)) {
      message.thinkingSteps = [...message.thinkingSteps, event.content]
    }

    if (event.type === 'reference') {
      message.references = Array.isArray(event.content) ? event.content : []
    }

    if (event.type === 'recommend') {
      message.recommendations = Array.isArray(event.content) ? event.content : []
    }

    if (event.type === 'status') {
      message.statusText = event.content || ''
    }

    if (event.type === 'skill' && event.content) {
      message.skills = Array.isArray(message.skills) ? [...message.skills, event.content] : [event.content]
    }

    if (event.type === 'error') {
      message.errorMessage = event.content || '对话执行失败'
      message.status = 'FAILED'
    }

    message.updatedAt = event.timestamp || new Date().toISOString()
  })

  scrollToBottom()
}

async function sendMessage(presetQuestion) {
  const rawInput = presetQuestion || userInput.value
  const question = String(rawInput || '').trim()

  // /skills 命令：打开技能选择面板，不当作普通消息发送
  if (!presetQuestion && question === '/skills') {
    userInput.value = ''
    await toggleSkillPicker()
    return
  }

  if (!question || isStreaming.value) {
    return
  }
  if (isDocumentMode.value && !selectedDocumentId.value) {
    pageError.value = '当前文档问答模式下请先选择一个文档'
    return
  }

  const conversationId = currentConversationId.value || createConversationId()
  const assistantMessage = createAssistantMessage()
  currentConversationId.value = conversationId
  pageError.value = ''

  displayMessages.value = [
    ...displayMessages.value,
    createUserMessage(question),
    assistantMessage
  ]
  currentAssistantMessageId.value = assistantMessage.id
  isStreaming.value = true
  isStopping.value = false

  if (!presetQuestion) {
    userInput.value = ''
    resizeComposer()
  }

  await scrollToBottom()

  const streamHandle = chatApi.openStream(
    {
      question,
      conversationId,
      chatMode: chatMode.value,
      selectedDocumentId: isDocumentMode.value ? selectedDocumentId.value || null : null,
      openChatMode: openChatMode.value,
      forcedSkillName: forcedSkillName.value || null
    },
    {
      onEvent: applyStreamEvent
    }
  )

  currentStreamHandle.value = streamHandle

  try {
    await streamHandle.done
  } catch (error) {
    if (error.name !== 'AbortError') {
      updateCurrentAssistant((message) => {
        message.errorMessage = normalizeError(error, '流式对话失败')
        message.status = 'FAILED'
      })
      pageError.value = normalizeError(error, '流式对话失败')
    }
  } finally {
    currentStreamHandle.value = null
    currentAssistantMessageId.value = ''
    isStreaming.value = false
    isStopping.value = false

    try {
      await refreshSessions()
      const sessionExists = sessions.value.some((item) => item.conversationId === conversationId)
      if (sessionExists) {
        await loadConversation(conversationId)
      }
    } catch {
      // 这里的错误已经在各自方法里落到页面提示了，不需要再次抛出。
    }
  }
}

// ===== /skills 命令：手动指定技能 =====
async function openSkillPicker() {
  try {
    const data = await skillApi.installedList({ pageNo: '1', pageSize: '50', keyword: '' })
    availableSkills.value = (data?.records || []).filter((skill) => hasCode(skill.runState, 1))
  } catch (error) {
    availableSkills.value = []
    pageError.value = error.message || '加载技能列表失败'
  }
  showSkillPicker.value = true
}

async function toggleSkillPicker() {
  if (showSkillPicker.value) {
    showSkillPicker.value = false
    return
  }
  await openSkillPicker()
}

// 输入 /skills 时自动弹出技能选择面板，无需按回车
watch(userInput, (value) => {
  const trimmed = String(value || '').trim()
  if (trimmed === '/skills' && !isStreaming.value) {
    userInput.value = ''
    openSkillPicker()
  }
})

function pickSkill(skill) {
  forcedSkillName.value = skill.skillName
  showSkillPicker.value = false
  userInput.value = ''
  pageError.value = `已指定使用技能：${skill.displayName || skill.skillName}，输入你的问题后发送即可。`
}

function clearForcedSkill() {
  forcedSkillName.value = ''
  showSkillPicker.value = false
  pageError.value = ''
}

async function stopStreaming() {
  if (!isStreaming.value || !currentConversationId.value || !currentStreamHandle.value) {
    return
  }

  isStopping.value = true

  try {
    const result = await chatApi.stopSession(currentConversationId.value)
    updateCurrentAssistant((message) => {
      message.statusText = result?.message || '用户已停止生成'
    })
  } catch (error) {
    pageError.value = normalizeError(error, '停止会话失败')
    isStopping.value = false
    return
  }

  currentStreamHandle.value.controller.abort()
}

function normalizeError(error, fallback) {
  if (error instanceof APIError && error.message) {
    return error.message
  }

  if (error instanceof Error && error.message) {
    return error.message
  }

  return fallback
}

onMounted(async () => {
  await Promise.all([refreshDocumentOptions(), refreshSessions()])

  if (sortedSessions.value.length > 0) {
    await loadConversation(sortedSessions.value[0].conversationId)
  } else {
    startNewConversation()
  }
})
</script>

<style scoped>
.icon {
  width: 16px;
  height: 16px;
}

.chat-shell {
  height: 82vh;
  min-height: 46vh;
  display: grid;
  grid-template-columns: minmax(180px, 15vw) 1fr;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

/* ===== 侧边栏 ===== */
.chat-sidebar {
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  min-height: 0;
}

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 8px;
}

.sidebar-brand {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-strong);
}

.sidebar-close {
  border: none;
  background: none;
  color: var(--color-muted);
  padding: 4px;
}

.new-chat {
  margin: 4px 12px 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 9px 0;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.new-chat:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.session-card {
  position: relative;
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.session-card:hover {
  background: var(--color-surface-soft);
}

.session-card.active {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
}

.session-select {
  width: 100%;
  text-align: left;
  background: none;
  border: none;
  padding: 12px 14px;
  font: inherit;
  color: inherit;
}

.session-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.session-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.running-dot {
  flex: none;
  font-size: 10px;
  color: var(--color-primary);
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.running-dot::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  50% { opacity: 0.3; }
}

.session-preview {
  margin: 4px 0;
  font-size: 12px;
  color: var(--color-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: var(--color-muted);
}

.session-delete {
  position: absolute;
  top: 8px;
  right: 8px;
  border: none;
  background: none;
  color: var(--color-muted);
  padding: 4px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.session-card:hover .session-delete {
  opacity: 1;
}

.sidebar-empty {
  padding: 20px;
  text-align: center;
  font-size: 13px;
  color: var(--color-muted);
}

.sidebar-mask {
  display: none;
}

/* ===== 主区（网格：左列消息+输入框，右列上下文栏） ===== */
.chat-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(150px, 13vw);
  grid-template-rows: auto 1fr auto;
  min-width: 0;
  min-height: 0;
  background: var(--color-bg);
}

.chat-topbar {
  grid-column: 1 / -1;
  grid-row: 1;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.topbar-menu {
  border: none;
  background: none;
  color: var(--color-muted);
  padding: 4px;
}

.topbar-session {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.topbar-session h2 {
  margin: 0;
  font-size: 15px;
  color: var(--color-text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mode-pill {
  flex: none;
  font-size: 11px;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
}

.mode-pill.open {
  background: rgba(217, 119, 87, 0.14);
  color: var(--color-accent);
}

.admin-entry {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--color-primary);
  font-weight: 600;
}

/* ===== 消息区（左列） ===== */
.messages-panel {
  grid-column: 1;
  grid-row: 2;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

@media (max-width: 1100px) {
  .chat-rail { display: none; }
}

/* ===== 输入区（左列，与消息区同宽对齐） ===== */
.composer-wrap {
  grid-column: 1;
  grid-row: 3;
  padding: 8px 18px 10px;
  background: var(--color-bg);
}

.composer-card {
  width: 100%;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 14px 16px;
  box-shadow: var(--shadow-sm);
}

/* 消息卡铺满消息区：助手消息贴左，用户消息贴右 */
.messages-panel :deep(.message-card) {
  width: 100%;
}

.notice {
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-muted);
  font-size: 13px;
}

.notice.error {
  background: rgba(179, 76, 47, 0.1);
  color: var(--color-danger);
}

.empty-state {
  margin: auto;
  max-width: 440px;
  text-align: center;
  padding: 30px 0;
}

.empty-mark {
  width: 52px;
  height: 52px;
  margin: 0 auto 14px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.empty-mark .icon {
  width: 24px;
  height: 24px;
}

.empty-state h3 {
  margin: 0 0 8px;
  font-size: 18px;
  color: var(--color-text-strong);
}

.empty-state p {
  margin: 0 0 18px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-muted);
}

.prompt-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.prompt-chip {
  padding: 8px 14px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  font-size: 13px;
  color: var(--color-text);
  transition: border-color 0.15s ease, color 0.15s ease;
}

.prompt-chip:hover {
  border-color: var(--color-primary);
  color: var(--color-primary-strong);
}

/* 右栏：占右列，跨消息区与输入区高度 */
.chat-rail {
  grid-column: 2;
  grid-row: 2 / 4;
  border-left: 1px solid var(--color-border);
  background: var(--color-surface);
  padding: 12px 10px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rail-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rail-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--color-muted);
}

.rail-mode {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-strong);
}

.rail-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--color-muted-strong);
}

.rail-skill {
  color: var(--color-warning);
}

.mode-switch {
  display: flex;
  gap: 4px;
  margin-bottom: 10px;
}

.mode-button {
  flex: 1;
  padding: 7px 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-soft);
  font-size: 13px;
  color: var(--color-muted-strong);
  transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.mode-button.active {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
  color: var(--color-primary-strong);
  font-weight: 600;
}

.mode-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.open-chat-mode-row {
  margin-top: 8px;
}

.open-chat-mode-label {
  font-size: 12px;
  color: var(--color-muted);
  flex-shrink: 0;
}

.mode-button-mini {
  flex: 0 0 auto;
  padding: 5px 12px;
  font-size: 12px;
  border-radius: 999px;
}

.scope-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.scope-select {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  background: var(--color-surface);
}

.scope-pill {
  font-size: 12px;
  color: var(--color-muted-strong);
  white-space: nowrap;
}

.scope-pill.warn {
  color: var(--color-warning);
}

.composer-input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.skill-picker {
  margin-bottom: 8px;
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface-soft);
  max-height: 220px;
  overflow-y: auto;
}

.skill-picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--color-muted-strong);
  margin-bottom: 8px;
}

.skill-picker-close {
  border: none;
  background: none;
  color: var(--color-muted);
  font-size: 18px;
  cursor: pointer;
  line-height: 1;
}

.skill-picker-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.skill-picker-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}

.skill-picker-item:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.sp-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.sp-desc {
  font-size: 12px;
  color: var(--color-muted);
}

.skill-picker-empty {
  font-size: 12px;
  color: var(--color-muted);
  padding: 8px 0;
}

.forced-skill-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(37, 87, 214, 0.1);
  border: 1px solid rgba(37, 87, 214, 0.25);
  color: var(--color-primary-strong);
  font-size: 13px;
}

.forced-skill-clear {
  border: none;
  background: none;
  color: var(--color-muted);
  font-size: 12px;
  cursor: pointer;
}

.composer-input {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  padding: 10px 4px;
  font-size: 15px;
  font-family: inherit;
  line-height: 1.6;
  max-height: 180px;
  color: var(--color-text);
  background: transparent;
}

.stop-button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: 13px;
  color: var(--color-text);
}

.send-button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 9px 18px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.send-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.composer-hint {
  margin-top: 8px;
  font-size: 11px;
  color: var(--color-muted);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.streaming-badge {
  color: var(--color-primary);
  font-weight: 600;
}

@media (max-width: 900px) {
  .chat-shell {
    grid-template-columns: 1fr;
  }

  .chat-sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    width: 260px;
    z-index: 40;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
  }

  .chat-sidebar.open {
    transform: translateX(0);
  }

  .sidebar-mask {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.3);
    z-index: 39;
  }
}
</style>
