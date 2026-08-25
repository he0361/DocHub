<template>
  <div class="am-shell">
    <header class="am-header">
      <div>
        <h2 class="am-title">账号管理</h2>
        <p class="am-subtitle">新增/停用控制台账号，并为账号授予各模块的访问权限。仅管理员可见。</p>
      </div>
      <button class="am-btn primary" type="button" @click="openCreate">新增账号</button>
    </header>

    <div v-if="message" class="am-notice" :class="messageType">{{ message }}</div>

    <!-- 账号列表 -->
    <div class="am-table-wrap">
      <table class="am-table">
        <thead>
          <tr>
            <th>账号</th>
            <th>显示名</th>
            <th>角色</th>
            <th>权限</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>{{ user.username }}</td>
            <td>{{ user.displayName || '-' }}</td>
            <td>
              <span class="am-role" :class="{ admin: user.isAdmin }">{{ user.isAdmin ? '管理员' : '普通' }}</span>
            </td>
            <td>
              <div v-if="user.isAdmin" class="am-perms">全部权限</div>
              <div v-else class="am-perms">
                <span v-for="perm in user.permissions" :key="perm" class="am-perm-tag">{{ PERMISSION_LABELS[perm] || perm }}</span>
                <span v-if="!user.permissions?.length" class="am-perm-empty">无</span>
              </div>
            </td>
            <td>
              <span class="am-status" :class="hasCode(user.status, 1) ? 'ok' : 'off'">
                {{ hasCode(user.status, 1) ? '启用' : '停用' }}
              </span>
            </td>
            <td>{{ shortDate(user.createTime) }}</td>
            <td class="am-actions">
              <button class="am-link" type="button" @click="openEdit(user)">编辑</button>
              <button class="am-link" type="button" @click="toggleStatus(user)">
                {{ hasCode(user.status, 1) ? '停用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="!users.length" class="am-empty">暂无账号</div>
    </div>

    <!-- 新增/编辑表单 -->
    <div v-if="formVisible" class="am-form-overlay">
      <div class="am-form">
        <div class="am-form-head">
          <strong>{{ editingUser ? `编辑账号：${editingUser.username}` : '新增账号' }}</strong>
          <button class="am-form-close" type="button" @click="formVisible = false">×</button>
        </div>

        <label class="am-field">
          <span>账号</span>
          <input v-model="form.username" :disabled="Boolean(editingUser)" placeholder="登录账号" />
        </label>
        <label class="am-field">
          <span>密码 {{ editingUser ? '（留空则不修改）' : '' }}</span>
          <input v-model="form.password" type="password" placeholder="密码" />
        </label>
        <label class="am-field">
          <span>显示名</span>
          <input v-model="form.displayName" placeholder="显示名" />
        </label>

        <div class="am-field">
          <span>角色</span>
          <label class="am-check">
            <input v-model="form.isAdmin" type="checkbox" />
            <span>管理员（拥有全部权限）</span>
          </label>
        </div>

        <div v-if="!form.isAdmin" class="am-field">
          <span>权限</span>
          <div class="am-perm-grid">
            <label v-for="(label, code) in PERMISSION_LABELS" :key="code" class="am-check">
              <input v-model="form.permissionSet" type="checkbox" :value="code" />
              <span>{{ label }}</span>
            </label>
          </div>
        </div>

        <div class="am-form-actions">
          <button class="am-btn" type="button" @click="formVisible = false">取消</button>
          <button class="am-btn primary" :disabled="saving" type="button" @click="submitForm">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminUserApi } from '../../api/api'
import { hasCode } from '../../utils/manageFormat'

const PERMISSION_LABELS = {
  dashboard: '运营总览',
  knowledge_route: '知识路由',
  document_manage: '文档接入',
  observability: '对话观测',
  route_trace: '路由追踪',
  account_manage: '账号管理'
}

const users = ref([])
const loading = ref(false)
const saving = ref(false)
const formVisible = ref(false)
const editingUser = ref(null)
const message = ref('')
const messageType = ref('info')

const form = reactive({
  username: '',
  password: '',
  displayName: '',
  isAdmin: false,
  permissionSet: []
})

function shortDate(value) {
  if (!value) return '-'
  return String(value).slice(0, 16).replace('T', ' ')
}

function showMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
  setTimeout(() => {
    if (message.value === text) message.value = ''
  }, 4000)
}

function openCreate() {
  editingUser.value = null
  form.username = ''
  form.password = ''
  form.displayName = ''
  form.isAdmin = false
  form.permissionSet = []
  formVisible.value = true
}

function openEdit(user) {
  editingUser.value = user
  form.username = user.username
  form.password = ''
  form.displayName = user.displayName || ''
  form.isAdmin = Boolean(user.isAdmin)
  form.permissionSet = [...(user.permissions || [])]
  formVisible.value = true
}

async function submitForm() {
  saving.value = true
  try {
    const payload = {
      id: editingUser.value?.id || null,
      username: form.username.trim(),
      password: form.password || null,
      displayName: form.displayName.trim(),
      isAdmin: form.isAdmin,
      permissions: form.isAdmin ? '' : (form.permissionSet || []).join(',')
    }
    const saved = await adminUserApi.save(payload)
    showMessage(`账号「${saved.displayName || saved.username}」已保存`, 'success')
    formVisible.value = false
    await loadUsers()
  } catch (error) {
    showMessage(error.message || '保存失败', 'danger')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(user) {
  const targetStatus = hasCode(user.status, 1) ? 0 : 1
  try {
    await adminUserApi.status({ id: user.id, status: targetStatus })
    showMessage(targetStatus === 1 ? '已启用' : '已停用', 'success')
    await loadUsers()
  } catch (error) {
    showMessage(error.message || '操作失败', 'danger')
  }
}

async function loadUsers() {
  loading.value = true
  try {
    users.value = (await adminUserApi.list()) || []
  } catch (error) {
    showMessage(error.message || '加载账号失败', 'danger')
    users.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadUsers)
</script>

<style scoped>
.am-shell { padding: 24px; max-width: 1100px; }
.am-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.am-title { margin: 0 0 6px; font-size: 22px; color: var(--color-text-strong); }
.am-subtitle { margin: 0; color: var(--color-muted); font-size: 14px; }
.am-btn { border: 1px solid var(--color-border); background: var(--color-surface-soft); border-radius: 8px; padding: 7px 14px; cursor: pointer; color: var(--color-text); }
.am-btn.primary { background: var(--color-primary); border-color: var(--color-primary); color: #fff; }
.am-notice { padding: 10px 14px; border-radius: 8px; margin-bottom: 12px; font-size: 13px; }
.am-notice.info { background: rgba(37,87,214,.1); color: var(--color-primary-strong); }
.am-notice.success { background: rgba(22,163,74,.1); color: #15803d; }
.am-notice.danger { background: rgba(220,38,38,.1); color: #b91c1c; }
.am-table-wrap { border: 1px solid var(--color-border); border-radius: 10px; overflow-x: auto; }
.am-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.am-table th, .am-table td { padding: 10px 12px; text-align: left; border-bottom: 1px solid var(--color-border-soft); }
.am-table th { background: var(--color-surface-soft); color: var(--color-muted-strong); white-space: nowrap; }
.am-role { padding: 2px 8px; border-radius: 999px; font-size: 12px; background: rgba(37,87,214,.1); color: var(--color-primary-strong); }
.am-role.admin { background: rgba(220,38,38,.1); color: #b91c1c; }
.am-perms { display: flex; flex-wrap: wrap; gap: 4px; }
.am-perm-tag { padding: 1px 6px; border-radius: 4px; background: var(--color-surface-soft); border: 1px solid var(--color-border); font-size: 12px; }
.am-perm-empty { color: var(--color-muted); font-size: 12px; }
.am-status { font-size: 12px; }
.am-status.ok { color: #15803d; }
.am-status.off { color: var(--color-muted); }
.am-actions { white-space: nowrap; }
.am-link { border: none; background: none; color: var(--color-primary); cursor: pointer; margin-right: 8px; font-size: 13px; }
.am-empty { padding: 24px; text-align: center; color: var(--color-muted); }
.am-form-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.35); display: flex; align-items: center; justify-content: center; z-index: 100; }
.am-form { width: 420px; max-width: 92vw; background: var(--color-surface); border-radius: 12px; padding: 20px; }
.am-form-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.am-form-close { border: none; background: none; font-size: 20px; cursor: pointer; color: var(--color-muted); }
.am-field { display: block; margin-bottom: 12px; }
.am-field > span { display: block; font-size: 12px; color: var(--color-muted-strong); margin-bottom: 4px; }
.am-field input[type="text"], .am-field input[type="password"] { width: 100%; box-sizing: border-box; border: 1px solid var(--color-border); border-radius: 8px; padding: 8px 10px; font-size: 13px; }
.am-check { display: flex; align-items: center; gap: 6px; font-size: 13px; cursor: pointer; }
.am-perm-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 6px; }
.am-form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
</style>
