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
          <p>保存时后端会再次测试候选配置；远程模型留空 API Key 会保留已加密保存的凭证。</p>
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
        <label v-if="form.deploymentType === 'REMOTE'">
          <span>API Key</span>
          <input v-model="form.apiKey" type="password" :placeholder="apiKeyPlaceholder" autocomplete="new-password" />
          <small>{{ apiKeyHint }}</small>
        </label>
        <p v-else class="local-api-key-hint">本地服务无需填写 API Key；保存时会自动清除原有远程凭证。</p>
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
          <h3>向量模型</h3>
          <p>先从后端测试候选模型；同名模型热切换，不同模型名会启动蓝绿重建。</p>
        </div>
        <span class="readonly-badge">受二次验证保护</span>
      </div>
      <dl class="summary-grid">
        <div><dt>运行时版本</dt><dd>{{ embeddingConfig.configVersion || '-' }}</dd></div>
        <div><dt>模型</dt><dd>{{ embeddingConfig.modelName || '-' }}</dd></div>
        <div><dt>维度</dt><dd>{{ embeddingConfig.dimension || '-' }}</dd></div>
        <div><dt>迁移状态</dt><dd>{{ embeddingConfig.migration?.status || '无' }}</dd></div>
      </dl>
      <p class="summary-note">文档集合：{{ embeddingConfig.documentCollection || '-' }}；记忆集合：{{ embeddingConfig.memoryCollection || '-' }}。{{ embeddingConfig.hasApiKey ? '已保存加密凭证，留空会保留它。' : '未配置 API Key。' }}</p>
      <form class="config-form" @submit.prevent="openEmbeddingConfirmation">
        <label><span>部署类型</span><select v-model="embeddingForm.deploymentType"><option value="REMOTE">远程 API</option><option value="LOCAL">本地服务</option></select></label>
        <label><span>兼容预设</span><select v-model="embeddingForm.compatibilityPreset"><option value="OPENAI_COMPATIBLE">OpenAI 兼容</option><option value="DASHSCOPE">DashScope</option><option value="OLLAMA">Ollama 兼容</option></select></label>
        <label><span>Base URL</span><input v-model.trim="embeddingForm.baseUrl" type="url" required /></label>
        <label><span>请求路径</span><input v-model.trim="embeddingForm.requestPath" type="text" placeholder="/v1/embeddings" /></label>
        <label class="form-wide"><span>最终请求地址预览</span><output class="url-preview">{{ embeddingFinalUrl || '请先填写 Base URL' }}</output></label>
        <label><span>模型名称</span><input v-model.trim="embeddingForm.modelName" type="text" required /></label>
        <label v-if="embeddingForm.deploymentType === 'REMOTE'"><span>API Key</span><input v-model="embeddingForm.apiKey" type="password" :placeholder="embeddingConfig.hasApiKey ? '已加密保存（留空保留）' : '远程服务必填'" autocomplete="new-password" /></label>
        <p v-else class="local-api-key-hint">本地服务无需填写 API Key；保存时会自动清除原有远程凭证。</p>
        <label><span>超时（毫秒）</span><input v-model.number="embeddingForm.timeoutMillis" type="number" min="1" step="1000" /></label>
        <div class="form-actions form-wide">
          <button class="button secondary" type="button" :disabled="embeddingTesting" @click="testEmbedding">{{ embeddingTesting ? '测试中…' : '测试连接' }}</button>
          <button class="button primary" type="submit" :disabled="!embeddingTest?.success">确认更换</button>
          <span v-if="embeddingTest" class="test-status" :class="embeddingTest.success ? 'success' : 'failure'">{{ embeddingTest.success ? `测试通过，${embeddingTest.changeMode === 'HOT_SWAP' ? '将热切换' : '将后台重建'}` : embeddingTest.message }}</span>
        </div>
      </form>
      <EmbeddingMigrationProgress :migration="embeddingConfig.migration" />
      <div v-if="embeddingConfig.migration?.status === 'FAILED'" class="form-actions migration-actions">
        <button class="button secondary" type="button" @click="openRetry">二次验证后重试</button>
        <button class="button secondary" type="button" @click="openRollback">回滚到版本 {{ embeddingConfig.migration.sourceConfigVersion }}</button>
      </div>
    </article>
    <EmbeddingModelChangeDialog :open="dialog.open" :candidate="embeddingForm" :active="embeddingConfig" :change-mode="dialog.mode" @close="dialog.open = false" @confirm="confirmEmbeddingChange" />
    <EmbeddingModelChangeDialog :open="retryDialog.open" :candidate="{}" :active="embeddingConfig" change-mode="HOT_SWAP" @close="retryDialog.open = false" @confirm="confirmRetry" />
    <EmbeddingModelChangeDialog :open="rollbackDialog.open" :candidate="{}" :active="embeddingConfig" change-mode="HOT_SWAP" @close="rollbackDialog.open = false" @confirm="confirmRollback" />
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { modelConfigApi } from '../../api/api'
import EmbeddingModelChangeDialog from '../../components/admin/EmbeddingModelChangeDialog.vue'
import EmbeddingMigrationProgress from '../../components/admin/EmbeddingMigrationProgress.vue'

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
})

const activeConfig = ref({})
const lastTest = ref(null)
const testing = ref(false)
const saving = ref(false)
const notice = reactive({ message: '', type: 'info' })
const embeddingConfig = ref({})
const embeddingForm = reactive({ deploymentType: 'REMOTE', compatibilityPreset: 'OPENAI_COMPATIBLE', baseUrl: '', requestPath: '/v1/embeddings', modelName: '', apiKey: '', timeoutMillis: 30000 })
const embeddingTest = ref(null)
const embeddingTesting = ref(false)
const dialog = reactive({ open: false, mode: 'HOT_SWAP' })
const retryDialog = reactive({ open: false })
const rollbackDialog = reactive({ open: false })
let migrationPoll = null

const finalUrl = computed(() => {
  const baseUrl = form.baseUrl.replace(/\/+$/, '')
  const requestPath = form.requestPath.trim().replace(/^\/+/, '')
  return baseUrl && requestPath ? `${baseUrl}/${requestPath}` : baseUrl
})
const embeddingFinalUrl = computed(() => {
  const baseUrl = embeddingForm.baseUrl.replace(/\/+$/, '')
  const requestPath = embeddingForm.requestPath.trim().replace(/^\/+/, '')
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
    toolCallingSupported: form.toolCallingSupported
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
}

async function loadConfig() {
  try {
    applyConfig(await modelConfigApi.query())
  } catch (error) {
    showNotice(error.message || '加载当前模型配置失败', 'danger')
  }
}

function applyEmbedding(config) {
  if (!config) return
  embeddingConfig.value = config
  for (const key of ['deploymentType', 'compatibilityPreset', 'baseUrl', 'requestPath', 'modelName', 'timeoutMillis']) {
    if (config[key] !== undefined && config[key] !== null) embeddingForm[key] = config[key]
  }
  embeddingForm.apiKey = ''
}
async function loadEmbedding() {
  try { applyEmbedding(await modelConfigApi.queryEmbedding()) } catch (error) { showNotice(error.message || '加载向量模型配置失败', 'danger') }
}
function embeddingPayload() { return { ...embeddingForm } }
async function testEmbedding() {
  embeddingTesting.value = true; embeddingTest.value = null
  try { embeddingTest.value = await modelConfigApi.testEmbedding(embeddingPayload()); showNotice(embeddingTest.value.success ? '向量模型连接与维度测试通过' : embeddingTest.value.message, embeddingTest.value.success ? 'success' : 'danger') }
  catch (error) { embeddingTest.value = { success: false, message: error.message || '向量模型测试失败' }; showNotice(embeddingTest.value.message, 'danger') }
  finally { embeddingTesting.value = false }
}
function openEmbeddingConfirmation() { if (!embeddingTest.value?.success) return; dialog.mode = embeddingTest.value.changeMode || 'BLUE_GREEN_REBUILD'; dialog.open = true }
async function confirmEmbeddingChange(secondFactor) {
  dialog.open = false
  try { const result = await modelConfigApi.changeEmbedding({ ...embeddingPayload(), ...secondFactor }); showNotice(result.message || '向量配置已提交', 'success'); await loadEmbedding(); ensureMigrationPolling() }
  catch (error) { showNotice(error.message || '向量模型更换失败，旧运行时保持不变', 'danger') }
}
function openRetry() { retryDialog.open = true }
async function confirmRetry(secondFactor) { retryDialog.open = false; try { await modelConfigApi.retryEmbeddingMigration({ migrationId: embeddingConfig.value.migration?.migrationId, ...secondFactor }); showNotice('迁移已重新排队，旧运行时保持不变', 'success'); await loadEmbedding(); ensureMigrationPolling() } catch (error) { showNotice(error.message || '重试失败', 'danger') } }
function openRollback() { rollbackDialog.open = true }
async function confirmRollback(secondFactor) { rollbackDialog.open = false; try { const result = await modelConfigApi.rollbackEmbedding({ configVersion: embeddingConfig.value.migration?.sourceConfigVersion, ...secondFactor }); showNotice(result.message || '已回滚', 'success'); await loadEmbedding() } catch (error) { showNotice(error.message || '回滚失败', 'danger') } }
function ensureMigrationPolling() {
  window.clearTimeout(migrationPoll)
  const migration = embeddingConfig.value.migration
  if (!migration || ['COMPLETED', 'FAILED'].includes(migration.status)) return
  migrationPoll = window.setTimeout(async () => { await loadEmbedding(); ensureMigrationPolling() }, 3000)
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

onMounted(async () => { await Promise.all([loadConfig(), loadEmbedding()]); ensureMigrationPolling() })
onBeforeUnmount(() => window.clearTimeout(migrationPoll))
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
.config-form label { min-width: 0; display: grid; gap: 6px; color: var(--color-muted-strong); font-size: 13px; }.config-form input, .config-form select { min-width: 0; box-sizing: border-box; width: 100%; padding: 9px 10px; border: 1px solid var(--color-border); border-radius: 8px; background: #fff; color: var(--color-text); font: inherit; }.config-form small { color: var(--color-muted); font-size: 12px; }.local-api-key-hint { align-self: end; margin: 0; padding: 9px 10px; border-radius: 8px; background: var(--color-surface-soft); color: var(--color-muted-strong); font-size: 13px; line-height: 1.5; }.form-wide { grid-column: 1 / -1; }.url-preview { display: block; min-height: 19px; overflow-wrap: anywhere; padding: 9px 10px; border-radius: 8px; background: var(--color-surface-soft); color: var(--color-primary-strong); }.checkbox-field { display: flex !important; align-items: center; gap: 8px; padding-top: 22px; }.checkbox-field input { width: auto; }.button { border: 1px solid var(--color-border); border-radius: 8px; padding: 9px 14px; cursor: pointer; font: inherit; }.button.primary { color: #fff; border-color: var(--color-primary); background: var(--color-primary); }.button.secondary { background: #fff; color: var(--color-text); }.button:disabled { cursor: wait; opacity: .65; }.form-actions { align-items: center; justify-content: flex-start; flex-wrap: wrap; }.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 18px 0 0; }.summary-grid div { padding: 12px; border-radius: 8px; background: var(--color-surface-soft); }.summary-grid dt { color: var(--color-muted); font-size: 12px; }.summary-grid dd { margin: 4px 0 0; color: var(--color-text); font-size: 14px; }
@media (max-width: 720px) { .page-header, .card-heading { display: grid; }.runtime-details { justify-content: flex-start; }.config-form, .summary-grid { grid-template-columns: 1fr; } }
</style>
