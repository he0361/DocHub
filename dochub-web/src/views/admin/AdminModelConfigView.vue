<template>
  <section class="model-config-page">
    <header class="page-header">
      <div>
        <h2>模型配置</h2>
        <p>配置在连接测试通过后才会保存并切换全局对话运行时。</p>
      </div>
      <span class="active-badge" :class="activeConfig.active ? 'active' : 'inactive'">
        {{ activeConfig.active ? '当前生效' : '尚未配置' }}
      </span>
    </header>

    <p class="global-warning">
      模型配置会影响所有用户和后台文档任务。请先测试连接再保存。Base URL 必须能从 DocHub 后端所在机器访问；本地模型不能填写仅对浏览器可见的地址。API Key 将加密保存且不会再次完整显示。更换向量模型会触发全量向量重建，重建期间请保持旧模型服务可用。
    </p>

    <ol class="configuration-guide" aria-label="五步配置引导">
      <li>选择远程 API 或本地服务及兼容预设。</li>
      <li>填写 Base URL、请求路径和模型名称，核对最终请求地址预览。</li>
      <li>远程服务填写 API Key；本地无鉴权服务可以留空。</li>
      <li>点击“测试连接”，通过后保存。</li>
      <li>向量配置需在确认弹窗中再次输入管理员密码和指定确认短语。</li>
    </ol>

    <p v-if="notice.message" class="notice" :class="notice.type">{{ notice.message }}</p>

    <article class="config-card">
      <div class="card-heading">
        <div>
          <h3>对话模型</h3>
          <p>保存时后端会再次测试候选配置；空白 API Key 会保留已加密保存的凭证。</p>
        </div>
        <div class="runtime-details">
          <span>版本 {{ activeConfig.configVersion || '-' }}</span>
          <span>更新人 {{ activeConfig.updatedBy || '-' }}</span>
          <span>更新时间 {{ formatTime(activeConfig.updateTime) }}</span>
        </div>
      </div>

      <form class="config-form" @submit.prevent="saveChat">
        <label>
          <span>部署类型</span>
          <select v-model="form.deploymentType">
            <option value="REMOTE">远程 API</option>
            <option value="LOCAL">本地服务</option>
          </select>
        </label>
        <label>
          <span>兼容预设</span>
          <select v-model="form.compatibilityPreset">
            <option value="OPENAI_COMPATIBLE">OpenAI 兼容</option>
            <option value="DASHSCOPE">DashScope</option>
            <option value="OLLAMA">Ollama 兼容</option>
          </select>
        </label>
        <label>
          <span>Base URL</span>
          <input v-model.trim="form.baseUrl" type="url" placeholder="https://api.example.com" />
        </label>
        <label>
          <span>请求路径</span>
          <input v-model.trim="form.requestPath" type="text" placeholder="/v1/chat/completions" />
        </label>
        <label class="form-wide">
          <span>最终请求地址预览</span>
          <output class="url-preview">{{ finalUrl || '请先填写 Base URL' }}</output>
        </label>
        <label>
          <span>模型名称</span>
          <input v-model.trim="form.modelName" type="text" placeholder="gpt-4o-mini" required />
        </label>
        <label>
          <span>API Key</span>
          <input v-model="form.apiKey" type="password" :placeholder="apiKeyPlaceholder" autocomplete="new-password" />
          <small>{{ apiKeyHint }}</small>
        </label>
        <label>
          <span>温度</span>
          <input v-model.number="form.temperature" type="number" min="0" max="2" step="0.1" />
        </label>
        <label>
          <span>最大输出 Token</span>
          <input v-model.number="form.maxTokens" type="number" min="1" step="1" />
        </label>
        <label>
          <span>超时（毫秒）</span>
          <input v-model.number="form.timeoutMillis" type="number" min="1" step="1000" />
        </label>
        <label class="checkbox-field">
          <input v-model="form.toolCallingSupported" type="checkbox" />
          <span>该模型支持工具调用</span>
        </label>
        <label v-if="form.deploymentType === 'LOCAL'" class="checkbox-field form-wide">
          <input v-model="form.clearApiKey" type="checkbox" />
          <span>清空本地服务 API Key</span>
        </label>
        <div class="form-actions form-wide">
          <button class="button secondary" type="button" :disabled="testing" @click="testChat">
            {{ testing ? '测试中…' : '测试连接' }}
          </button>
          <button class="button primary" type="submit" :disabled="saving">
            {{ saving ? '保存并激活中…' : '保存并激活' }}
          </button>
          <span v-if="lastTest" class="test-status" :class="lastTest.success ? 'success' : 'failure'">
            {{ lastTest.success ? '最近测试通过' : '最近测试失败' }}{{ lastTest.message ? `：${lastTest.message}` : '' }}
          </span>
        </div>
      </form>
    </article>

    <article class="config-card embedding-card">
      <div class="card-heading">
        <div>
          <h3>向量模型运行时摘要</h3>
          <p>向量配置修改必须进入受保护的蓝绿迁移流程；本页不提供直接修改入口。</p>
        </div>
        <span class="readonly-badge">只读</span>
      </div>
      <dl class="summary-grid">
        <div><dt>当前状态</dt><dd>{{ embeddingSummary.status }}</dd></div>
        <div><dt>运行时版本</dt><dd>{{ embeddingSummary.version }}</dd></div>
        <div><dt>模型</dt><dd>{{ embeddingSummary.modelName }}</dd></div>
        <div><dt>迁移状态</dt><dd>{{ embeddingSummary.migrationStatus }}</dd></div>
      </dl>
      <p class="summary-note">凭证热更新会在验证后原子切换；模型名称变化会先重建全部向量，再自动切换。</p>
    </article>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { modelConfigApi } from '../../api/api'

const form = reactive({
  deploymentType: 'REMOTE',
  compatibilityPreset: 'OPENAI_COMPATIBLE',
  baseUrl: '',
  requestPath: '/v1/chat/completions',
  modelName: '',
  apiKey: '',
  temperature: 0.7,
  maxTokens: 2048,
  timeoutMillis: 30000,
  toolCallingSupported: true,
  clearApiKey: false
})

const activeConfig = ref({})
const lastTest = ref(null)
const testing = ref(false)
const saving = ref(false)
const notice = reactive({ message: '', type: 'info' })
const embeddingSummary = {
  status: '由受保护迁移流程管理',
  version: '-',
  modelName: '-',
  migrationStatus: '未开始迁移'
}

const finalUrl = computed(() => {
  const baseUrl = form.baseUrl.replace(/\/+$/, '')
  const requestPath = form.requestPath.trim().replace(/^\/+/, '')
  return baseUrl && requestPath ? `${baseUrl}/${requestPath}` : baseUrl
})
const apiKeyHint = computed(() => activeConfig.value.hasApiKey
  ? '已保存加密凭证；留空将保留原值。'
  : '不会回显或记录 API Key。')
const apiKeyPlaceholder = computed(() => activeConfig.value.hasApiKey ? '已加密保存（留空保留）' : '远程服务必填')

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

function payload() {
  return {
    deploymentType: form.deploymentType,
    compatibilityPreset: form.compatibilityPreset,
    baseUrl: form.baseUrl,
    requestPath: form.requestPath,
    modelName: form.modelName,
    apiKey: form.apiKey,
    temperature: form.temperature,
    maxTokens: form.maxTokens,
    timeoutMillis: form.timeoutMillis,
    toolCallingSupported: form.toolCallingSupported,
    clearApiKey: form.deploymentType === 'LOCAL' && form.clearApiKey
  }
}

function showNotice(message, type = 'info') {
  notice.message = message
  notice.type = type
}

function applyConfig(config) {
  if (!config) return
  activeConfig.value = config
  for (const key of ['deploymentType', 'compatibilityPreset', 'baseUrl', 'requestPath', 'modelName', 'temperature', 'maxTokens', 'timeoutMillis']) {
    if (config[key] !== undefined && config[key] !== null) form[key] = config[key]
  }
  if (config.toolCallingSupported !== undefined) form.toolCallingSupported = Boolean(config.toolCallingSupported)
  form.apiKey = ''
  form.clearApiKey = false
}

async function loadConfig() {
  try {
    applyConfig(await modelConfigApi.query())
  } catch (error) {
    showNotice(error.message || '加载当前模型配置失败', 'danger')
  }
}

async function testChat() {
  testing.value = true
  lastTest.value = null
  try {
    const result = await modelConfigApi.testChat(payload())
    lastTest.value = result || { success: true, message: '连接测试成功' }
    showNotice(lastTest.value.success ? '连接测试通过' : (lastTest.value.message || '连接测试失败'), lastTest.value.success ? 'success' : 'danger')
  } catch (error) {
    lastTest.value = { success: false, message: error.message || '连接测试失败' }
    showNotice(lastTest.value.message, 'danger')
  } finally {
    testing.value = false
  }
}

async function saveChat() {
  saving.value = true
  try {
    applyConfig(await modelConfigApi.saveChat(payload()))
    showNotice('对话模型已保存并激活', 'success')
  } catch (error) {
    showNotice(error.message || '保存失败，当前运行时保持不变', 'danger')
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.model-config-page { max-width: 1120px; margin: 0 auto; display: grid; gap: 16px; }
.page-header, .card-heading, .form-actions { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-header h2, .card-heading h3 { margin: 0; color: var(--color-text-strong); }
.page-header p, .card-heading p, .summary-note { margin: 6px 0 0; color: var(--color-muted); font-size: 13px; line-height: 1.6; }
.global-warning { margin: 0; padding: 14px 16px; border: 1px solid #f2c66d; border-radius: 10px; background: #fff8e8; color: #754d00; line-height: 1.7; }
.configuration-guide { margin: 0; padding: 14px 16px 14px 34px; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-surface); color: var(--color-text); line-height: 1.85; font-size: 14px; }
.config-card { border: 1px solid var(--color-border); border-radius: 12px; padding: 20px; background: var(--color-surface); }
.runtime-details { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; color: var(--color-muted); font-size: 12px; }
.runtime-details span, .active-badge, .readonly-badge, .test-status { padding: 4px 8px; border-radius: 999px; background: var(--color-surface-soft); }
.active-badge.active, .test-status.success { color: #166534; background: #dcfce7; }
.active-badge.inactive, .readonly-badge, .test-status.failure { color: var(--color-muted-strong); }
.notice { margin: 0; padding: 10px 12px; border-radius: 8px; font-size: 13px; }
.notice.info { background: var(--color-primary-soft); color: var(--color-primary-strong); }.notice.success { background: #dcfce7; color: #166534; }.notice.danger { background: #fee2e2; color: #991b1b; }
.config-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 16px; margin-top: 18px; }
.config-form label { min-width: 0; display: grid; gap: 6px; color: var(--color-muted-strong); font-size: 13px; }.config-form input, .config-form select { min-width: 0; box-sizing: border-box; width: 100%; padding: 9px 10px; border: 1px solid var(--color-border); border-radius: 8px; background: #fff; color: var(--color-text); font: inherit; }.config-form small { color: var(--color-muted); font-size: 12px; }.form-wide { grid-column: 1 / -1; }.url-preview { display: block; min-height: 19px; overflow-wrap: anywhere; padding: 9px 10px; border-radius: 8px; background: var(--color-surface-soft); color: var(--color-primary-strong); }.checkbox-field { display: flex !important; align-items: center; gap: 8px; padding-top: 22px; }.checkbox-field input { width: auto; }.button { border: 1px solid var(--color-border); border-radius: 8px; padding: 9px 14px; cursor: pointer; font: inherit; }.button.primary { color: #fff; border-color: var(--color-primary); background: var(--color-primary); }.button.secondary { background: #fff; color: var(--color-text); }.button:disabled { cursor: wait; opacity: .65; }.form-actions { align-items: center; justify-content: flex-start; flex-wrap: wrap; }.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 18px 0 0; }.summary-grid div { padding: 12px; border-radius: 8px; background: var(--color-surface-soft); }.summary-grid dt { color: var(--color-muted); font-size: 12px; }.summary-grid dd { margin: 4px 0 0; color: var(--color-text); font-size: 14px; }
@media (max-width: 720px) { .page-header, .card-heading { display: grid; }.runtime-details { justify-content: flex-start; }.config-form, .summary-grid { grid-template-columns: 1fr; } }
</style>
