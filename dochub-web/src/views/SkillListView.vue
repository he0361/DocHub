<template>
  <div class="skill-list">
    <header class="sl-header">
      <div>
        <h2 class="sl-title">技能列表</h2>
        <p class="sl-subtitle">你在系统上安装的技能都会在这里展示。已启用的技能会在智能问答（开放式提问）与文档仿写时，根据你的需求描述自动使用。</p>
      </div>
      <div class="sl-header-actions">
        <input ref="skillFileInput" type="file" accept=".md,.zip" class="sl-file-input" @change="handleUpload" />
        <button class="sl-btn sl-btn-ghost" type="button" @click="showFormatTip = !showFormatTip">
          格式说明
        </button>
        <button class="sl-btn primary" :disabled="uploading" @click="skillFileInput.click()">
          {{ uploading ? '上传中…' : '上传技能' }}
        </button>
      </div>
    </header>

    <div v-if="showFormatTip" class="sl-format-tip">
      <p class="sl-format-tip-title">📌 上传技能格式说明</p>
      <ul class="sl-format-tip-list">
        <li><b>支持格式</b>：单个 <code>SKILL.md</code> 文件，或技能文件夹的 <code>.zip</code> 压缩包（zip 内需包含 <code>SKILL.md</code>）。</li>
        <li><b>frontmatter</b>：文件顶部用 <code>---</code> 包裹的 YAML，<code>name</code>（技能标识）和 <code>description</code>（用途描述，用于自动匹配）<b>必须有</b>。</li>
        <li><b>建议补充</b>：<code>display_name</code>（显示名）、<code>when_to_use</code>（触发时机）、<code>category</code>（分类）、<code>tags</code>（标签，逗号分隔）。缺失会自动补默认值，但默认值匹配能力较弱。</li>
        <li><b>正文</b>：Markdown 指令，会注入到回答提示词中，请写明具体执行步骤。</li>
        <li><b>重要</b>：正文需<b>自包含</b>，不要依赖 <code>references/</code> 等外部文件——外部文件不会被加载，依赖外部文件的步骤会失效。</li>
        <li><b>示例</b>：<pre>---
name: my-skill
display_name: 我的技能
description: 用于处理某类文档任务
when_to_use: 用户需要某类能力时
tags: 标签1,标签2
---
正文指令…</pre></li>
      </ul>
    </div>

    <!-- 管理员密码对话框 -->
    <div v-if="passwordDialogVisible" class="sl-dialog-overlay" @click.self="closePasswordDialog">
      <div class="sl-dialog">
        <div class="sl-dialog-head">
          <strong>查看技能存放位置</strong>
          <button class="sl-dialog-close" type="button" @click="closePasswordDialog">×</button>
        </div>
        <p class="sl-dialog-desc">请输入任意管理员的账号密码，以查看「{{ passwordTarget?.displayName || passwordTarget?.skillName }}」的存放位置。</p>
        <input
          v-model="passwordInput"
          class="sl-dialog-input"
          type="password"
          placeholder="管理员密码"
          autofocus
          @keyup.enter="submitPassword"
        />
        <p v-if="passwordError" class="sl-dialog-error">{{ passwordError }}</p>
        <div class="sl-dialog-actions">
          <button class="sl-btn" type="button" @click="closePasswordDialog">取消</button>
          <button class="sl-btn primary" :disabled="passwordVerifying" type="button" @click="submitPassword">
            {{ passwordVerifying ? '校验中…' : '确认' }}
          </button>
        </div>
      </div>
    </div>

    <div class="sl-section">
      <div class="sl-section-head">
        <h3>已安装技能</h3>
      </div>
      <div v-if="loading" class="sl-empty">加载中…</div>
      <div v-else-if="installed.length" class="sl-grid">
        <article v-for="skill in installed" :key="skill.skillName" class="sl-card" :class="{ disabled: !hasCode(skill.runState, 1) }">
          <div class="sl-card-top">
            <span class="sl-badge" :class="stateClass(skill.runState)">{{ stateLabel(skill.runState) }}</span>
            <span class="sl-type">{{ skill.skillType }}</span>
          </div>
          <h4 class="sl-name">{{ skill.displayName || skill.skillName }}</h4>
          <p class="sl-desc">{{ skill.description || '暂无说明' }}</p>
          <div class="sl-meta">
            <span v-if="skill.category">分类：{{ skill.category }}</span>
            <span v-if="skill.tags">标签：{{ skill.tags }}</span>
          </div>
          <div class="sl-storage">
            <span class="sl-storage-label">存放位置</span>
            <code class="sl-storage-path">{{ revealedLocations[skill.skillName] ? skillPath(skill) : '******' }}</code>
            <button v-if="!revealedLocations[skill.skillName]" class="sl-reveal-link" type="button" @click="revealLocation(skill)">查看</button>
          </div>
          <div class="sl-actions">
            <button
              v-if="!hasCode(skill.runState, 1)"
              class="sl-btn primary"
              @click="setEnabled(skill, true)"
            >启用</button>
            <button
              v-if="hasCode(skill.runState, 1)"
              class="sl-btn"
              @click="setEnabled(skill, false)"
            >停用</button>
            <button class="sl-btn danger" @click="remove(skill)">删除</button>
          </div>
        </article>
      </div>
      <div v-else class="sl-empty">还没有安装技能</div>
    </div>

    <div class="sl-section">
      <div class="sl-section-head">
        <h3>可安装</h3>
        <span class="sl-hint">未安装的内置技能</span>
      </div>
      <div v-if="available.length" class="sl-grid">
        <article v-for="skill in available" :key="skill.skillName" class="sl-card">
          <div class="sl-card-top">
            <span class="sl-badge">未安装</span>
          </div>
          <h4 class="sl-name">{{ skill.displayName || skill.skillName }}</h4>
          <p class="sl-desc">{{ skill.description || '暂无说明' }}</p>
          <div class="sl-actions">
            <button class="sl-btn primary" :disabled="installing === skill.skillName" @click="install(skill)">
              {{ installing === skill.skillName ? '安装中…' : '安装' }}
            </button>
          </div>
        </article>
      </div>
      <div v-else class="sl-empty">没有更多可安装的技能</div>
    </div>

    <div v-if="message" class="sl-message">{{ message }}</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { skillApi } from '../api/api'
import { hasCode } from '../utils/manageFormat'

const installed = ref([])
const market = ref([])
const loading = ref(false)
const uploading = ref(false)
const skillFileInput = ref(null)
const showFormatTip = ref(false)
const revealedLocations = ref({})
const passwordDialogVisible = ref(false)
const passwordInput = ref('')
const passwordError = ref('')
const passwordTarget = ref(null)
const passwordVerifying = ref(false)
const installing = ref('')
const message = ref('')

const available = computed(() => {
  const installedNames = new Set(installed.value.map((skill) => skill.skillName))
  return market.value.filter((skill) => !installedNames.has(skill.skillName))
})

function stateClass(runState) {
  if (hasCode(runState, 1)) return 'ok'
  if (hasCode(runState, 3)) return 'pending'
  return 'off'
}

function stateLabel(runState) {
  if (hasCode(runState, 1)) return '已启用'
  if (hasCode(runState, 3)) return '待审核'
  return '已停用'
}

function showMessage(text) {
  message.value = text
  setTimeout(() => (message.value = ''), 3000)
}

async function loadInstalled() {
  loading.value = true
  try {
    const data = await skillApi.installedList({ pageNo: '1', pageSize: '50', keyword: '' })
    installed.value = data?.records || []
  } catch (error) {
    message.value = error.message || '加载技能失败'
  } finally {
    loading.value = false
  }
}

async function loadMarket() {
  try {
    const data = await skillApi.marketList({ pageNo: '1', pageSize: '50', keyword: '', category: '' })
    market.value = data?.records || []
  } catch {
    market.value = []
  }
}

async function install(skill) {
  installing.value = skill.skillName
  try {
    await skillApi.install({ skillName: skill.skillName })
    showMessage(`技能「${skill.displayName || skill.skillName}」安装成功`)
    await Promise.all([loadInstalled(), loadMarket()])
  } catch (error) {
    message.value = error.message || '安装失败'
  } finally {
    installing.value = ''
  }
}

async function handleUpload(event) {
  const file = event.target.files?.[0]
  if (!file) return
  uploading.value = true
  message.value = ''
  try {
    const result = await skillApi.uploadSkill(file)
    showMessage(`技能「${result.displayName || result.skillName}」上传成功并已启用`)
    await Promise.all([loadInstalled(), loadMarket()])
  } catch (error) {
    message.value = error.message || '上传失败'
  } finally {
    uploading.value = false
    if (skillFileInput.value) {
      skillFileInput.value.value = ''
    }
  }
}

function skillPath(skill) {
  return `${skill.sourceType || 'classpath'}://${skill.objectPrefix || skill.skillName + '/'}`
}

function revealLocation(skill) {
  passwordTarget.value = skill
  passwordInput.value = ''
  passwordError.value = ''
  passwordDialogVisible.value = true
}

async function submitPassword() {
  const skill = passwordTarget.value
  if (!skill) return
  passwordVerifying.value = true
  passwordError.value = ''
  try {
    const ok = await skillApi.verifyAdmin({ password: passwordInput.value })
    if (ok) {
      revealedLocations.value = { ...revealedLocations.value, [skill.skillName]: true }
      passwordDialogVisible.value = false
    } else {
      passwordError.value = '管理员密码错误，无法查看'
    }
  } catch (error) {
    passwordError.value = error.message || '校验失败'
  } finally {
    passwordVerifying.value = false
  }
}

function closePasswordDialog() {
  passwordDialogVisible.value = false
  passwordInput.value = ''
  passwordError.value = ''
  passwordTarget.value = null
}

async function setEnabled(skill, enabled) {
  try {
    await skillApi[enabled ? 'enable' : 'disable']({ skillName: skill.skillName })
    showMessage(`已${enabled ? '启用' : '停用'}「${skill.displayName || skill.skillName}」`)
    await loadInstalled()
  } catch (error) {
    message.value = error.message || '操作失败'
  }
}

async function remove(skill) {
  if (!window.confirm(`确定删除技能「${skill.displayName || skill.skillName}」吗？`)) return
  try {
    await skillApi.deleteSkill({ skillName: skill.skillName })
    showMessage('技能已删除')
    await Promise.all([loadInstalled(), loadMarket()])
  } catch (error) {
    message.value = error.message || '删除失败'
  }
}

onMounted(() => {
  loadInstalled()
  loadMarket()
})
</script>

<style scoped>
.skill-list {
  max-width: min(1180px, 96vw);
  margin: 0 auto;
}

.sl-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.sl-header-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.sl-btn-ghost {
  background: transparent;
  border-color: var(--color-border);
  color: var(--color-muted-strong);
}

.sl-file-input {
  display: none;
}

.sl-format-tip {
  margin: 0 0 16px;
  padding: 12px 16px;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-primary);
  border-radius: 8px;
  background: var(--color-surface-soft);
  font-size: 13px;
  color: var(--color-muted-strong);
  line-height: 1.8;
}

.sl-format-tip-title {
  margin: 0 0 6px;
  font-weight: 600;
  color: var(--color-text-strong);
}

.sl-format-tip-list {
  margin: 0;
  padding-left: 18px;
}

.sl-format-tip-list code {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  padding: 0 4px;
  font-size: 12px;
}

.sl-format-tip-list pre {
  margin: 6px 0 0;
  padding: 8px 10px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre;
}

.sl-dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.sl-dialog {
  width: 380px;
  max-width: 92vw;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 16px 48px rgba(15, 23, 42, 0.18);
}

.sl-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 15px;
  color: var(--color-text-strong);
}

.sl-dialog-close {
  border: none;
  background: none;
  color: var(--color-muted);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}

.sl-dialog-desc {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--color-muted);
  line-height: 1.7;
}

.sl-dialog-input {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.sl-dialog-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.sl-dialog-error {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--color-danger);
}

.sl-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.sl-title {
  margin: 0 0 6px;
  font-size: 22px;
  color: var(--color-text-strong);
}

.sl-subtitle {
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.7;
}

.sl-section {
  margin-bottom: 14px;
}

.sl-section-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}

.sl-section-head h3 {
  margin: 0;
  font-size: 15px;
  color: var(--color-text-strong);
}

.sl-hint {
  font-size: 12px;
  color: var(--color-muted);
}

.sl-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 8px;
}

.sl-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  box-shadow: var(--shadow-sm);
}

.sl-card.disabled {
  opacity: 0.72;
}

.sl-card-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sl-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  color: var(--color-muted-strong);
}

.sl-badge.ok {
  background: rgba(21, 115, 91, 0.12);
  border-color: transparent;
  color: var(--color-success);
}

.sl-badge.off {
  background: rgba(104, 117, 140, 0.14);
  border-color: transparent;
  color: var(--color-muted);
}

.sl-badge.pending {
  background: rgba(168, 101, 32, 0.12);
  border-color: transparent;
  color: var(--color-warning);
}

.sl-type {
  font-size: 11px;
  color: var(--color-muted);
}

.sl-name {
  margin: 0;
  font-size: 15px;
  color: var(--color-text-strong);
}

.sl-desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--color-muted);
}

.sl-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: var(--color-muted-strong);
}

.sl-storage {
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
}

.sl-storage-label {
  display: block;
  font-size: 11px;
  color: var(--color-muted);
  margin-bottom: 4px;
}

.sl-reveal-link {
  border: none;
  background: none;
  color: var(--color-primary);
  font-size: 12px;
  padding: 0 0 0 4px;
  cursor: pointer;
}

.sl-storage-path {
  font-size: 12px;
  color: var(--color-primary-strong);
  word-break: break-all;
}

.sl-actions {
  display: flex;
  gap: 8px;
  margin-top: auto;
}

.sl-btn {
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: 13px;
  color: var(--color-text);
}

.sl-btn.primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

.sl-btn.danger {
  color: var(--color-danger);
}

.sl-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.sl-empty {
  padding: 20px;
  text-align: center;
  color: var(--color-muted);
  font-size: 13px;
  background: var(--color-surface);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-lg);
}

.sl-message {
  margin-top: 16px;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-size: 13px;
}
</style>
