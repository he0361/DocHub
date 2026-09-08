<template>
  <div v-if="open" class="merge-backdrop" @click.self="$emit('close')">
    <section class="merge-dialog" role="dialog" aria-modal="true" aria-label="合并重复知识域">
      <header>
        <div><span>Duplicate Scope Repair</span><h3>合并重复知识域</h3></div>
        <button type="button" aria-label="关闭合并窗口" @click="$emit('close')">×</button>
      </header>
      <p class="merge-warning">合并会迁移文档和主题关系、停用源知识域并刷新路由索引。审计记录会永久保留。</p>
      <label>源知识域
        <select v-model="sourceCode" aria-label="源知识域">
          <option value="">请选择</option>
          <option v-for="scope in scopes" :key="scope.scopeCode" :value="scope.scopeCode">{{ scope.scopeName }}（{{ scope.scopeCode }}）</option>
        </select>
      </label>
      <label>目标知识域
        <select v-model="targetCode" aria-label="目标知识域">
          <option value="">请选择保留的知识域</option>
          <option v-for="scope in targetScopes" :key="scope.scopeCode" :value="scope.scopeCode">{{ scope.scopeName }}（{{ scope.scopeCode }}）</option>
        </select>
      </label>
      <div class="merge-counts">
        <span>文档 <strong>{{ preview.documentCount }}</strong></span>
        <span>主题 <strong>{{ preview.topicCount }}</strong></span>
        <span>关系 <strong>{{ preview.relationCount }}</strong></span>
      </div>
      <footer>
        <button type="button" @click="$emit('close')">取消</button>
        <button class="merge-submit" type="button" :disabled="!sourceCode || !targetCode || loading" @click="submit">
          {{ loading ? '正在合并...' : '确认迁移并停用源知识域' }}
        </button>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  open: Boolean,
  sourceScopeCode: { type: String, default: '' },
  scopes: { type: Array, default: () => [] },
  topics: { type: Array, default: () => [] },
  documents: { type: Array, default: () => [] },
  relations: { type: Array, default: () => [] },
  loading: Boolean
})
const emit = defineEmits(['close', 'merge'])
const sourceCode = ref('')
const targetCode = ref('')

watch(() => [props.open, props.sourceScopeCode], () => {
  if (props.open) {
    sourceCode.value = props.sourceScopeCode || ''
    targetCode.value = ''
  }
}, { immediate: true })

const targetScopes = computed(() => props.scopes.filter((scope) => scope.scopeCode !== sourceCode.value))
const preview = computed(() => {
  const sourceTopics = props.topics.filter((topic) => topic.scopeCode === sourceCode.value)
  const topicCodes = new Set(sourceTopics.map((topic) => topic.topicCode))
  return {
    documentCount: props.documents.filter((document) => document.knowledgeScopeCode === sourceCode.value).length,
    topicCount: sourceTopics.length,
    relationCount: props.relations.filter((relation) => topicCodes.has(relation.topicCode)).length
  }
})

function submit() {
  emit('merge', { sourceScopeCode: sourceCode.value, targetScopeCode: targetCode.value })
}
</script>

<style scoped>
.merge-backdrop { position: fixed; inset: 0; z-index: 1100; display: grid; place-items: center; padding: 24px; background: rgba(32, 28, 22, .42); }
.merge-dialog { width: min(560px, 100%); padding: 22px; border-radius: 16px; background: #fffdf8; box-shadow: 0 24px 70px rgba(28, 24, 18, .24); }
.merge-dialog header { display: flex; justify-content: space-between; gap: 16px; }
.merge-dialog header span { color: #a26435; font-size: 12px; text-transform: uppercase; letter-spacing: .08em; }
.merge-dialog h3 { margin: 4px 0 0; }
.merge-dialog header button { border: 0; background: transparent; font-size: 24px; }
.merge-warning { padding: 10px 12px; border-radius: 10px; background: #fff0d8; color: #744a20; }
.merge-dialog label { display: grid; gap: 6px; margin: 12px 0; color: #62594d; font-size: 13px; }
.merge-dialog select { padding: 10px; border: 1px solid #d8cdbb; border-radius: 9px; background: white; }
.merge-counts { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin: 14px 0; }
.merge-counts span { padding: 10px; border-radius: 9px; background: #f4efe5; text-align: center; }
.merge-dialog footer { display: flex; justify-content: flex-end; gap: 10px; }
.merge-dialog footer button { padding: 9px 13px; border: 1px solid #d8cdbb; border-radius: 9px; background: white; }
.merge-dialog footer .merge-submit { border-color: #a95042; background: #a95042; color: white; }
.merge-dialog footer button:disabled { cursor: not-allowed; opacity: .45; }
</style>
