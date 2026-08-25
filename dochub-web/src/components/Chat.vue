<template>
  <article class="message-card" :class="{ 'message-user': isUser, 'message-assistant': !isUser }">
    <div class="avatar">
      <UserIcon v-if="isUser" class="icon" />
      <SparklesIcon v-else class="icon" />
    </div>

    <div class="bubble">
      <div class="bubble-header">
        <div>
          <p class="role-name">{{ isUser ? '你' : '智能助手' }}</p>
          <p class="message-time">{{ formatTime(message.updatedAt || message.createdAt) }}</p>
        </div>
        <button class="copy-button" type="button" :title="copyButtonTitle" @click="copyContent">
          <CheckIcon v-if="copied" class="icon" />
          <DocumentDuplicateIcon v-else class="icon" />
        </button>
      </div>

      <div v-if="isUser" class="plain-text">{{ message.content }}</div>
      <template v-else>
        <p v-if="showStatusNotice" class="message-notice message-status">{{ message.statusText }}</p>
        <p v-if="showErrorNotice" class="message-notice message-error">{{ message.errorMessage }}</p>
        <div v-if="assistantSkills.length" class="skill-chips">
          <span v-for="(item, index) in assistantSkills" :key="index" class="skill-chip">✦ {{ item }}</span>
        </div>
        <div v-if="hasAssistantContent" ref="contentRef" class="markdown-body" v-html="renderedContent"></div>
        <p v-else-if="showEmptyAssistantHint" class="message-placeholder">本次回答没有生成可展示的正文内容。</p>

        <section v-if="showRouteExplainCard" class="route-card" :class="`route-card-${routeExplain.statusTone}`">
          <div class="route-card-head">
            <div>
              <p class="route-kicker">{{ routeExplain.modeLabel }}</p>
              <h4 class="route-title">{{ routeExplain.confidenceBand.label }} · 置信度 {{ routeExplain.confidenceText }}</h4>
            </div>
            <span class="route-status-badge" :class="`route-status-badge-${routeExplain.statusTone}`">
              {{ routeExplain.statusLabel }}
            </span>
          </div>

          <p class="route-summary">{{ routeExplain.summary }}</p>

          <div v-if="routeExplain.notes?.length" class="route-note-list">
            <span
              v-for="(item, index) in routeExplain.notes"
              :key="`${message.id}-route-note-${index}`"
              class="route-note-chip"
            >
              {{ item }}
            </span>
          </div>

          <div v-if="routeExplain.topDocuments?.length" class="route-candidate-grid">
            <article
              v-for="(item, index) in routeExplain.topDocuments"
              :key="`${message.id}-route-doc-${item.documentId || index}`"
              class="route-candidate-card"
              :class="{ 'route-candidate-primary': index === 0 }"
            >
              <p class="route-candidate-rank">候选 {{ index + 1 }}</p>
              <strong>{{ item.documentName || item.documentId }}</strong>
              <span class="route-candidate-score">匹配分 {{ item.scoreText }}</span>
              <small>{{ item.reason || '基于文档画像与元数据综合召回' }}</small>
            </article>
          </div>

          <details v-if="routeExplain.scopePreview?.length || routeExplain.topicPreview?.length" class="route-detail-toggle">
            <summary>查看范围与主题候选</summary>
            <div class="route-detail-columns">
              <div v-if="routeExplain.scopePreview?.length" class="route-detail-block">
                <p class="route-detail-label">范围候选</p>
                <div class="route-detail-list">
                  <span
                    v-for="(item, index) in routeExplain.scopePreview"
                    :key="`${message.id}-route-scope-${item.scopeCode || index}`"
                    class="route-detail-chip"
                  >
                    {{ item.scopeName || item.scopeCode }} · {{ item.scoreText }}
                  </span>
                </div>
              </div>

              <div v-if="routeExplain.topicPreview?.length" class="route-detail-block">
                <p class="route-detail-label">主题候选</p>
                <div class="route-detail-list">
                  <span
                    v-for="(item, index) in routeExplain.topicPreview"
                    :key="`${message.id}-route-topic-${item.topicCode || index}`"
                    class="route-detail-chip"
                  >
                    {{ item.topicName || item.topicCode }} · {{ item.scoreText }}
                  </span>
                </div>
              </div>
            </div>
          </details>
        </section>
      </template>
      <div v-if="isStreaming" class="stream-cursor"></div>

      <section v-if="showRecommendationBar" class="recommend-bar">
        <p class="recommend-label">推荐追问</p>
        <div class="recommend-list">
          <button
            v-for="(item, index) in message.recommendations"
            :key="`${message.id}-recommend-${index}`"
            class="recommend-chip"
            type="button"
            @click="$emit('recommend', item)"
          >
            {{ item }}
          </button>
        </div>
      </section>
    </div>
  </article>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import sql from 'highlight.js/lib/languages/sql'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import { marked } from 'marked'
import {
  CheckIcon,
  DocumentDuplicateIcon,
  SparklesIcon,
  UserIcon
} from '@heroicons/vue/24/outline'

const props = defineProps({
  message: {
    type: Object,
    required: true
  },
  isStreaming: {
    type: Boolean,
    default: false
  },
  showRecommendations: {
    type: Boolean,
    default: false
  }
})
defineEmits(['recommend'])

const contentRef = ref(null)
const copied = ref(false)

hljs.registerLanguage('bash', bash)
hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('yaml', yaml)

marked.setOptions({
  breaks: true,
  gfm: true
})

const isUser = computed(() => props.message.role === 'user')
const assistantSkills = computed(() =>
  !isUser.value && Array.isArray(props.message.skills) ? props.message.skills : []
)
const copyButtonTitle = computed(() => (copied.value ? '已复制' : '复制内容'))
const hasAssistantContent = computed(() => !isUser.value && Boolean(props.message.content))
const showStatusNotice = computed(() => !isUser.value && Boolean(props.message.statusText))
const showErrorNotice = computed(() => !isUser.value && Boolean(props.message.errorMessage))
const showEmptyAssistantHint = computed(() => {
  return !isUser.value && !props.isStreaming && !props.message.content && (showStatusNotice.value || showErrorNotice.value)
})
const routeExplain = computed(() => (!isUser.value ? props.message.routeExplain || null : null))
const showRouteExplainCard = computed(() => Boolean(routeExplain.value))
const copyableText = computed(() => {
  if (props.message.content) {
    return props.message.content
  }

  return [props.message.statusText, props.message.errorMessage].filter(Boolean).join('\n')
})
const showRecommendationBar = computed(() => {
  return !isUser.value && props.showRecommendations && Array.isArray(props.message.recommendations) && props.message.recommendations.length > 0
})

const renderedContent = computed(() => {
  if (!props.message.content) {
    return ''
  }

  const rendered = marked.parse(props.message.content)
  return DOMPurify.sanitize(rendered, {
    ADD_ATTR: ['target', 'rel', 'class']
  })
})

async function highlightCodeBlocks() {
  await nextTick()

  if (!contentRef.value || isUser.value) {
    return
  }

  contentRef.value.querySelectorAll('pre code').forEach((block) => {
    hljs.highlightElement(block)
  })
}

async function copyContent() {
  try {
    await navigator.clipboard.writeText(copyableText.value || '')
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 1800)
  } catch (error) {
    console.error('复制消息失败', error)
  }
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
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

watch(
  () => props.message.content,
  () => {
    if (!isUser.value) {
      highlightCodeBlocks()
    }
  }
)

onMounted(() => {
  if (!isUser.value) {
    highlightCodeBlocks()
  }
})
</script>


<style scoped>
/* ===== 消息卡片 ===== */
.message-card {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.message-user {
  justify-content: flex-end;
}

.avatar {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  border: 1px solid var(--color-border);
}

.message-user .avatar {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

.bubble {
  min-width: 0;
  flex: 0 1 auto;
  max-width: min(760px, 78%);
  padding: 16px 18px;
  border-radius: 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}

.message-user .bubble {
  flex: 0 1 auto;
  max-width: min(760px, 78%);
  background: linear-gradient(135deg, #e08a5f, var(--color-accent));
  border-color: transparent;
}

.bubble-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.role-name {
  margin: 0;
  color: var(--color-text-strong);
  font-weight: 700;
  font-size: 14px;
}

.message-user .role-name {
  color: #fff;
}

.message-time {
  margin: 3px 0 0;
  color: var(--color-muted);
  font-size: 12px;
}

.message-user .message-time {
  color: rgba(255, 255, 255, 0.7);
}

.copy-button {
  width: 32px;
  height: 32px;
  flex: none;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  color: var(--color-muted-strong);
}

.message-user .copy-button {
  background: rgba(255, 255, 255, 0.14);
  border-color: transparent;
  color: #fff;
}

.plain-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
  font-size: 15px;
}

.message-user .plain-text {
  color: #fff;
}

.markdown-body {
  color: var(--color-text);
  line-height: 1.8;
  word-break: break-word;
  font-size: 15px;
}

.message-notice,
.message-placeholder {
  margin: 0 0 12px;
  padding: 12px 14px;
  border-radius: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.skill-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.skill-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(37, 87, 214, 0.1);
  border: 1px solid rgba(37, 87, 214, 0.25);
  color: var(--color-primary-strong);
  font-size: 12px;
}

.message-status {
  border: 1px solid var(--color-primary-soft);
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
}

.message-error {
  border: 1px solid rgba(179, 76, 47, 0.18);
  background: rgba(179, 76, 47, 0.08);
  color: var(--color-danger);
}

.message-placeholder {
  border: 1px dashed var(--color-border-strong);
  background: var(--color-surface-soft);
  color: var(--color-muted);
}

/* ===== 路由解释卡 ===== */
.route-card {
  margin-top: 14px;
  padding: 16px;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: var(--color-surface-soft);
}

.route-card-success {
  border-color: rgba(21, 115, 91, 0.2);
  background: rgba(21, 115, 91, 0.05);
}

.route-card-warning {
  border-color: rgba(168, 101, 32, 0.22);
  background: rgba(168, 101, 32, 0.06);
}

.route-card-danger {
  border-color: rgba(179, 76, 47, 0.2);
  background: rgba(179, 76, 47, 0.06);
}

.route-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.route-kicker,
.route-detail-label,
.route-candidate-rank {
  margin: 0;
  color: var(--color-muted);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.route-title {
  margin: 6px 0 0;
  color: var(--color-text-strong);
  font-size: 15px;
}

.route-status-badge {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.route-status-badge-success {
  background: rgba(21, 115, 91, 0.12);
  color: var(--color-success);
}

.route-status-badge-warning {
  background: rgba(168, 101, 32, 0.14);
  color: var(--color-warning);
}

.route-status-badge-danger {
  background: rgba(179, 76, 47, 0.12);
  color: var(--color-danger);
}

.route-summary {
  margin: 12px 0 0;
  color: var(--color-text);
  line-height: 1.75;
}

.route-note-list,
.route-detail-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.route-note-list {
  margin-top: 12px;
}

.route-note-chip,
.route-detail-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 6px 11px;
  font-size: 12px;
  line-height: 1.5;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text);
}

.route-candidate-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}

.route-candidate-card {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  display: grid;
  gap: 6px;
}

.route-candidate-primary {
  border-color: var(--color-primary);
  box-shadow: inset 0 0 0 1px var(--color-primary-soft);
}

.route-candidate-card strong {
  color: var(--color-text-strong);
}

.route-candidate-score,
.route-candidate-card small {
  color: var(--color-muted);
}

.route-detail-toggle {
  margin-top: 14px;
  border-top: 1px solid var(--color-border);
  padding-top: 10px;
}

.route-detail-toggle summary {
  cursor: pointer;
  color: var(--color-primary-strong);
  font-weight: 600;
}

.route-detail-columns {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.route-detail-block {
  display: grid;
  gap: 8px;
}

/* ===== Markdown 排版 ===== */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin-top: 1.2em;
  margin-bottom: 0.6em;
  color: var(--color-text-strong);
  letter-spacing: -0.02em;
}

.markdown-body :deep(p:first-child) {
  margin-top: 0;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(a) {
  color: var(--color-primary-strong);
  text-decoration: underline;
  text-decoration-color: var(--color-primary);
  text-underline-offset: 3px;
}

.markdown-body :deep(pre) {
  overflow-x: auto;
  margin: 14px 0;
  padding: 14px;
  border-radius: 12px;
  background: #0f1724;
}

.markdown-body :deep(code:not(pre code)) {
  padding: 2px 6px;
  border-radius: 6px;
  background: var(--color-surface-soft);
}

.stream-cursor {
  width: 10px;
  height: 20px;
  margin-top: 12px;
  border-radius: 999px;
  background: var(--color-primary);
  animation: pulse 1s infinite;
}

/* ===== 推荐追问 ===== */
.recommend-bar {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

.recommend-label {
  margin: 0 0 10px;
  color: var(--color-muted);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.recommend-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.recommend-chip {
  border: 1px solid var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  border-radius: 999px;
  padding: 9px 14px;
  font-size: 13px;
  font-weight: 600;
  transition: transform 0.2s ease, background 0.2s ease, border-color 0.2s ease;
}

.recommend-chip:hover {
  transform: translateY(-1px);
  border-color: var(--color-primary-strong);
}

.icon {
  width: 16px;
  height: 16px;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.3;
  }

  50% {
    opacity: 1;
  }
}

@media (max-width: 768px) {
  .message-card {
    gap: 10px;
  }

  .avatar {
    width: 32px;
    height: 32px;
  }

  .bubble {
    padding: 14px;
  }

  .route-card-head,
  .route-detail-columns {
    grid-template-columns: 1fr;
    display: grid;
  }

  .route-status-badge {
    justify-self: start;
  }
}
</style>
