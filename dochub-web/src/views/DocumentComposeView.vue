<template>
  <div class="compose">
    <header class="cp-header">
      <h2 class="cp-title">文档仿写</h2>
      <p class="cp-subtitle">提供一份参考文档，告诉 AI 你想要什么，它会照着这份文档的格式与写作风格生成新文档。</p>
    </header>

    <div class="cp-layout">
      <aside class="cp-form">
        <div class="cp-card">
          <div class="cp-card-title">参考文档</div>
          <div class="cp-source-tabs">
            <button type="button" class="cp-tab" :class="{ active: sourceType === 'upload' }" @click="sourceType = 'upload'">上传文件</button>
            <button type="button" class="cp-tab" :class="{ active: sourceType === 'kb' }" @click="sourceType = 'kb'">从知识库选择</button>
          </div>

          <div v-if="sourceType === 'upload'" class="cp-dropzone" @click="pickFile">
            <input ref="fileInputRef" type="file" accept=".pdf,.doc,.docx,.txt,.md,.html" hidden @change="onFileChange" />
            <div v-if="!selectedFile" class="cp-dropzone-empty">
              <span class="cp-dropzone-icon">⇪</span>
              <p>点击选择参考文档</p>
              <small>支持 PDF / Word / TXT / Markdown</small>
            </div>
            <div v-else class="cp-file-picked">
              <span class="cp-file-icon">▤</span>
              <div>
                <strong>{{ selectedFile.name }}</strong>
                <small>{{ (selectedFile.size / 1024).toFixed(1) }} KB</small>
              </div>
              <button type="button" class="cp-remove" @click.stop="clearFile">×</button>
            </div>
          </div>

          <div v-else class="cp-kb-select">
            <select v-model="selectedDocId" class="cp-select">
              <option value="">请选择知识库中的参考文档…</option>
              <option v-for="doc in kbDocuments" :key="doc.documentId" :value="doc.documentId">{{ doc.documentName }}</option>
            </select>
            <div v-if="!kbDocuments.length" class="cp-kb-empty">知识库暂无已解析文档，可先上传文件</div>
          </div>
        </div>

        <div class="cp-card">
          <div class="cp-card-title">需求描述</div>
          <textarea
            v-model="requirement"
            class="cp-requirement"
            placeholder="例如：仿照这份《差旅报销管理办法》的格式，写一份《费用报销管理办法》，适用于全体员工…"
            rows="3"
          />
        </div>

        <div v-if="result" class="cp-card">
          <div class="cp-card-title">修改建议（可选）</div>
          <textarea
            v-model="modification"
            class="cp-requirement"
            placeholder="对生成结果不满意的地方，例如：章节再精简一些、加上审批流程表格、语气更正式…"
            rows="2"
          />
        </div>

        <button class="cp-generate" :disabled="generating || !canGenerate" @click="generate">
          {{ generating ? '生成中…' : result ? '按建议重新生成' : '开始仿写' }}
        </button>

        <div v-if="generating" class="cp-progress">
          <div class="cp-progress-bar"><div class="cp-progress-fill" :style="{ width: progress + '%' }"></div></div>
          <div class="cp-progress-stage">{{ progressStage }}… {{ progress }}%</div>
        </div>

        <div v-if="errorMessage" class="cp-error">{{ errorMessage }}</div>
      </aside>

      <section v-if="result" class="cp-result">
        <div class="cp-result-head">
          <h3>{{ result.templateName }}</h3>
          <div class="cp-result-actions">
            <button class="cp-btn" :disabled="downloading" @click="download('md')">下载 .md</button>
            <button class="cp-btn" :disabled="downloading" @click="download('docx')">下载 .docx</button>
            <button class="cp-btn primary" :disabled="ingesting" @click="ingest">{{ ingesting ? '入库中…' : '一键入知识库' }}</button>
          </div>
        </div>

        <div v-if="result.outline?.length" class="cp-outline">
          <div class="cp-result-label">生成大纲</div>
          <ol>
            <li v-for="item in result.outline" :key="item">{{ item }}</li>
          </ol>
        </div>

        <div class="cp-result-label">正文预览</div>
        <article class="cp-preview" v-html="renderedMarkdown"></article>

        <div v-if="ingestResult" class="cp-ingest-tip">
          已入库：{{ ingestResult.documentName }}（文档ID {{ ingestResult.documentId }}）
        </div>
      </section>

      <section v-else class="cp-result cp-result-empty">
        <div class="cp-empty-icon">✎</div>
        <p>填写参考文档与需求后，点击「开始仿写」</p>
      </section>
    </div>

    <section class="cp-history">
      <h3 class="cp-history-title">生成历史</h3>
      <div v-if="records.length" class="cp-table-wrap">
        <table class="cp-table">
          <thead>
            <tr>
              <th>参考/模板</th>
              <th>状态</th>
              <th>耗时</th>
              <th>入库</th>
              <th>时间</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records" :key="record.recordCode">
              <td>{{ record.templateName }}</td>
              <td>{{ hasCode(record.generationStatus, 2) ? '成功' : '失败' }}</td>
              <td>{{ record.costMillis != null ? `${record.costMillis}ms` : '-' }}</td>
              <td>{{ record.sourceDocumentId || '-' }}</td>
              <td>{{ shortDate(record.createTime) }}</td>
              <td>
                <button class="cp-link" @click="download(record.recordCode, 'md')">下载</button>
                <button class="cp-link cp-link-danger" :disabled="deletingRecordCode === record.recordCode" @click="deleteRecord(record)">
                  {{ deletingRecordCode === record.recordCode ? '删除中…' : '删除' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="cp-empty">暂无生成记录</div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { docgenApi, manageApi } from '../api/api'
import { formatDateTime, hasCode } from '../utils/manageFormat'

const sourceType = ref('upload')
const fileInputRef = ref(null)
const selectedFile = ref(null)
const kbDocuments = ref([])
const selectedDocId = ref('')
const requirement = ref('')
const modification = ref('')
const generating = ref(false)
const downloading = ref(false)
const ingesting = ref(false)
const result = ref(null)
const errorMessage = ref('')
const ingestResult = ref(null)
const records = ref([])

// 生成进度（模拟阶段）
const progress = ref(0)
const progressStage = ref('准备中')
const STAGES = ['解析参考文档', '大纲规划', '正文生成']
let progressTimer = null

const canGenerate = computed(() => {
  if (generating.value) return false
  if (sourceType.value === 'upload') return !!selectedFile.value
  return !!selectedDocId.value
})

const renderedMarkdown = computed(() => {
  const content = result.value?.previewMarkdown || ''
  return DOMPurify.sanitize(marked.parse(content) || '')
})

function startProgress() {
  progress.value = 5
  let stageIndex = 0
  progressStage.value = STAGES[0]
  progressTimer = setInterval(() => {
    // 前 85% 按阶段推进，最后 15% 留给请求真正完成时置满
    if (progress.value < 85) {
      progress.value += 3
      const idx = Math.min(Math.floor(progress.value / 30), STAGES.length - 1)
      if (idx !== stageIndex) {
        stageIndex = idx
        progressStage.value = STAGES[idx]
      }
    }
  }, 400)
}

function stopProgress(success) {
  if (progressTimer) clearInterval(progressTimer)
  progressTimer = null
  if (success) {
    progress.value = 100
    progressStage.value = '完成'
    setTimeout(() => {
      progress.value = 0
    }, 600)
  }
}

function pickFile() {
  fileInputRef.value?.click()
}

function onFileChange(event) {
  selectedFile.value = event.target.files?.[0] || null
  selectedDocId.value = ''
}

function clearFile() {
  selectedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function shortDate(value) {
  if (!value) return '-'
  return String(value).slice(0, 19).replace('T', ' ')
}

function formatTime(value) {
  if (!value) return '-'
  try {
    return formatDateTime(value) || String(value).slice(0, 19).replace('T', ' ')
  } catch {
    return String(value).slice(0, 19).replace('T', ' ')
  }
}

async function loadKbDocuments() {
  try {
    const data = await manageApi.queryDocumentPage({ pageNo: '1', pageSize: '50' })
    kbDocuments.value = (data?.records || []).filter((doc) => doc.documentId)
  } catch {
    kbDocuments.value = []
  }
}

async function loadRecords() {
  try {
    const data = await docgenApi.records({ pageNo: '1', pageSize: '10' })
    records.value = data?.records || []
  } catch {
    records.value = []
  }
}

async function generate() {
  generating.value = true
  errorMessage.value = ''
  ingestResult.value = null
  result.value = { previewMarkdown: '' }
  progress.value = 5
  progressStage.value = '解析参考文档'
  try {
    // 修改建议合并进需求，让模型按建议修订
    const mergedRequirement = modification.value
      ? `${requirement.value}。\n[补充修改要求]${modification.value}`
      : requirement.value
    await docgenApi.generateReferenceStream({
      file: sourceType.value === 'upload' ? selectedFile.value : null,
      referenceDocumentId: sourceType.value === 'kb' ? selectedDocId.value : '',
      requirement: mergedRequirement,
      onEvent: handleGenerateEvent
    })
    stopProgress(true)
    loadRecords()
  } catch (error) {
    stopProgress(false)
    errorMessage.value = error.message || '文档仿写失败'
  } finally {
    generating.value = false
  }
}

function handleGenerateEvent(event) {
  if (!event) return
  if (event.type === 'status') {
    progressStage.value = event.content || progressStage.value
    progress.value = Math.min(progress.value + 5, 88)
  } else if (event.type === 'text') {
    const chunk = event.content || ''
    if (!chunk) return
    result.value = result.value || { previewMarkdown: '' }
    result.value.previewMarkdown = (result.value.previewMarkdown || '') + chunk
    progress.value = Math.min(progress.value + 1, 94)
  } else if (event.type === 'done') {
    const data = event.content || {}
    result.value = {
      recordCode: data.recordCode,
      templateName: data.templateName,
      fileName: data.fileName,
      outline: data.outline || [],
      previewMarkdown: data.previewMarkdown || result.value?.previewMarkdown || '',
      generationStatus: data.generationStatus
    }
    progress.value = 100
  } else if (event.type === 'error') {
    throw new Error(event.content || '文档仿写失败')
  }
}

async function download(recordCode, format) {
  downloading.value = true
  try {
    const code = format ? recordCode : result.value?.recordCode
    const fmt = format || recordCode
    await docgenApi.exportDocument(code, fmt)
  } catch (error) {
    errorMessage.value = error.message || '下载失败'
  } finally {
    downloading.value = false
  }
}

const deletingRecordCode = ref(null)

async function deleteRecord(record) {
  if (!record?.recordCode) return
  const confirmed = window.confirm(
    `确认删除生成记录《${record.templateName || record.recordCode}》吗？删除后不可恢复。`
  )
  if (!confirmed) return
  deletingRecordCode.value = record.recordCode
  try {
    await docgenApi.deleteRecord({ recordCode: record.recordCode })
    loadRecords()
  } catch (error) {
    errorMessage.value = error.message || '删除失败'
  } finally {
    deletingRecordCode.value = null
  }
}

async function ingest() {
  if (!result.value) return
  ingesting.value = true
  errorMessage.value = ''
  try {
    ingestResult.value = await docgenApi.ingest({
      recordCode: result.value.recordCode,
      knowledgeScopeCode: '',
      knowledgeScopeName: '',
      businessCategory: 'composed',
      documentTags: '仿写文档'
    })
    loadRecords()
  } catch (error) {
    errorMessage.value = error.message || '入库失败'
  } finally {
    ingesting.value = false
  }
}

onMounted(() => {
  loadKbDocuments()
  loadRecords()
})

onBeforeUnmount(() => {
  if (progressTimer) clearInterval(progressTimer)
})
</script>

<style scoped>
.compose {
  max-width: min(1280px, 96vw);
  margin: 0 auto;
}

.cp-header {
  margin-bottom: 10px;
}

.cp-title {
  margin: 0 0 6px;
  font-size: 22px;
  color: var(--color-text-strong);
}

.cp-subtitle {
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
}

.cp-layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 12px;
  align-items: start;
}

@media (max-width: 860px) {
  .cp-layout { grid-template-columns: 1fr; }
}

.cp-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cp-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px;
  box-shadow: var(--shadow-sm);
}

.cp-card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-muted-strong);
  margin-bottom: 10px;
}

.cp-source-tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
}

.cp-tab {
  flex: 1;
  padding: 7px 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-soft);
  font-size: 13px;
  color: var(--color-muted-strong);
}

.cp-tab.active {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
  color: var(--color-primary-strong);
  font-weight: 600;
}

.cp-dropzone {
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
  padding: 20px 14px;
  cursor: pointer;
  text-align: center;
  transition: border-color 0.15s ease;
}

.cp-dropzone:hover {
  border-color: var(--color-primary);
}

.cp-dropzone-empty p {
  margin: 8px 0 4px;
  font-size: 14px;
  color: var(--color-text);
}

.cp-dropzone-empty small {
  font-size: 12px;
  color: var(--color-muted);
}

.cp-dropzone-icon {
  font-size: 22px;
  color: var(--color-primary);
}

.cp-file-picked {
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
}

.cp-file-icon {
  font-size: 20px;
  color: var(--color-primary);
}

.cp-file-picked strong {
  font-size: 13px;
  color: var(--color-text-strong);
  display: block;
}

.cp-file-picked small {
  font-size: 11px;
  color: var(--color-muted);
}

.cp-remove {
  margin-left: auto;
  border: none;
  background: none;
  color: var(--color-muted);
  font-size: 18px;
}

.cp-kb-select select {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 14px;
}

.cp-kb-empty {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-muted);
}

.cp-requirement {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 14px;
  resize: vertical;
  font-family: inherit;
}

.cp-requirement:focus {
  outline: none;
  border-color: var(--color-primary);
}

.cp-generate {
  padding: 12px 0;
  border: none;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, #e08a5f, var(--color-accent));
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.cp-generate:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.cp-progress {
  padding: 4px 2px;
}

.cp-progress-bar {
  height: 8px;
  border-radius: 999px;
  background: var(--color-surface-soft);
  overflow: hidden;
  border: 1px solid var(--color-border);
}

.cp-progress-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--color-primary), var(--color-accent));
  transition: width 0.4s ease;
}

.cp-progress-stage {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-muted-strong);
}

.cp-error {
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: rgba(192, 69, 58, 0.1);
  color: var(--color-danger);
  font-size: 13px;
}

.cp-result {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 14px;
  min-height: 300px;
  box-shadow: var(--shadow-sm);
}

.cp-result-empty {
  display: grid;
  place-items: center;
  color: var(--color-muted);
  gap: 8px;
}

.cp-empty-icon {
  font-size: 30px;
  color: var(--color-primary);
}

.cp-result-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.cp-result-head h3 {
  margin: 0;
  font-size: 17px;
  color: var(--color-text-strong);
}

.cp-result-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.cp-btn {
  padding: 7px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: 13px;
  color: var(--color-text);
}

.cp-btn.primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

.cp-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.cp-result-label {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--color-muted);
  margin: 12px 0 8px;
}

.cp-outline ol {
  margin: 0 0 6px;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.8;
}

.cp-preview {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  background: var(--color-surface-soft);
  font-size: 13px;
  line-height: 1.7;
  /* 按视口高度百分比固定预览窗口，正文再长也只滚不撑高页面 */
  height: 55vh;
  min-height: 320px;
  max-height: 62vh;
  overflow-y: auto;
  overflow-x: hidden;
  /* 长行/长串强制换行，避免横向把窗口撑得很长 */
  overflow-wrap: break-word;
  word-break: break-word;
}

/* v-html 注入的 markdown 内容同样强制换行 */
.cp-preview :deep(p),
.cp-preview :deep(li),
.cp-preview :deep(td),
.cp-preview :deep(th),
.cp-preview :deep(blockquote),
.cp-preview :deep(h1),
.cp-preview :deep(h2),
.cp-preview :deep(h3),
.cp-preview :deep(h4) {
  overflow-wrap: break-word;
  word-break: break-word;
}

.cp-preview :deep(pre) {
  white-space: pre-wrap;
  word-break: break-word;
}

.cp-preview :deep(code) {
  white-space: pre-wrap;
  word-break: break-all;
}

.cp-preview :deep(img) {
  max-width: 100%;
  height: auto;
}

.cp-preview :deep(table) {
  width: 100%;
  max-width: 100%;
  table-layout: fixed;
  word-break: break-word;
}

.cp-ingest-tip {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-size: 13px;
}

.cp-history {
  margin-top: 18px;
}

.cp-history-title {
  font-size: 15px;
  color: var(--color-text-strong);
  margin: 0 0 12px;
}

.cp-table-wrap {
  overflow-x: auto;
}

.cp-table {
  width: 100%;
  border-collapse: collapse;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  font-size: 13px;
}

.cp-table th,
.cp-table td {
  text-align: left;
  padding: 9px 14px;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

.cp-table th {
  background: var(--color-surface-soft);
  color: var(--color-muted-strong);
  font-weight: 600;
}

.cp-link {
  border: none;
  background: none;
  color: var(--color-primary);
  font-size: 13px;
  padding: 0;
  margin-right: 8px;
}

.cp-link-danger {
  color: var(--color-danger);
}

.cp-link:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.cp-empty {
  color: var(--color-muted);
  font-size: 13px;
  padding: 16px;
  text-align: center;
}
</style>
