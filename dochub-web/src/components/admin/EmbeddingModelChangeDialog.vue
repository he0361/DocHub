<template>
  <div v-if="open" class="dialog-backdrop" role="presentation" @click.self="close">
    <section class="dialog" role="dialog" aria-modal="true" aria-labelledby="embedding-change-title">
      <h3 id="embedding-change-title">确认安全更换向量模型</h3>
      <p v-if="changeMode === 'BLUE_GREEN_REBUILD'">模型名称将改变：后台重建完成并校验通过后才会切换，当前用户仍在使用旧向量模型。</p>
      <p v-else>模型名称未改变：连接和维度校验通过后，会原子热切换凭证与请求地址。</p>
      <dl>
        <div><dt>当前模型</dt><dd>{{ active?.modelName || '-' }}</dd></div>
        <div><dt>候选模型</dt><dd>{{ candidate?.modelName || '-' }}</dd></div>
      </dl>
      <label>再次输入管理员密码
        <input v-model="password" aria-label="再次输入管理员密码" type="password" autocomplete="current-password" />
      </label>
      <label>确认文本
        <input v-model="phrase" aria-label="确认文本" type="text" placeholder="我确认更改向量模型" autocomplete="off" />
      </label>
      <p class="hint">请完整输入“我确认更改向量模型”。密码和确认文本不会保存。</p>
      <footer>
        <button type="button" @click="close">取消</button>
        <button type="button" :disabled="!canConfirm" @click="confirm">开始安全更换</button>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({ open: Boolean, candidate: Object, active: Object, changeMode: String })
const emit = defineEmits(['confirm', 'close'])
const password = ref('')
const phrase = ref('')
const canConfirm = computed(() => password.value.length > 0 && phrase.value === '我确认更改向量模型')
function clear() { password.value = ''; phrase.value = '' }
function close() { clear(); emit('close') }
function confirm() {
  if (!canConfirm.value) return
  const payload = { currentPassword: password.value, confirmationPhrase: phrase.value }
  clear()
  emit('confirm', payload)
}
watch(() => props.open, (visible) => { if (!visible) clear() })
</script>

<style scoped>
.dialog-backdrop { position: fixed; inset: 0; z-index: 30; display: grid; place-items: center; padding: 16px; background: rgba(15, 23, 42, .45); }.dialog { width: min(520px, 100%); display: grid; gap: 14px; padding: 22px; border-radius: 12px; background: #fff; color: #172033; box-shadow: 0 18px 50px rgba(15, 23, 42, .25); }.dialog h3, .dialog p { margin: 0; }.dialog p { line-height: 1.65; color: #536174; }.dialog dl { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin: 0; }.dialog dl div { padding: 10px; border-radius: 8px; background: #f5f7fb; }.dialog dt { font-size: 12px; color: #69758a; }.dialog dd { margin: 4px 0 0; overflow-wrap: anywhere; }.dialog label { display: grid; gap: 6px; font-size: 14px; }.dialog input { padding: 9px 10px; border: 1px solid #c9d2e1; border-radius: 8px; font: inherit; }.hint { font-size: 12px; }.dialog footer { display: flex; justify-content: flex-end; gap: 10px; }.dialog button { padding: 9px 13px; border: 1px solid #b8c2d4; border-radius: 8px; background: #fff; cursor: pointer; font: inherit; }.dialog button:last-child { border-color: #2563eb; background: #2563eb; color: #fff; }.dialog button:disabled { cursor: not-allowed; opacity: .5; }
</style>
