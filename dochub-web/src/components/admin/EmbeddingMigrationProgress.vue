<template>
  <section v-if="migration" class="progress" aria-live="polite">
    <div class="heading"><strong>向量迁移：{{ migration.status }}</strong><span>旧版本 {{ migration.sourceConfigVersion }} → 目标版本 {{ migration.targetConfigVersion }}</span></div>
    <p v-if="isWorking">后台重建完成并校验通过后才会切换；当前用户仍在使用旧向量模型。</p>
    <p v-else-if="migration.status === 'COMPLETED'">重建与校验已完成，系统已切换至目标向量模型。</p>
    <p v-else-if="migration.status === 'FAILED'">迁移失败，当前运行时未改变。修复候选服务后可使用“重试”，或回滚到保留版本。</p>
    <div class="meters"><label>文档 {{ migration.documentProcessed || 0 }} / {{ migration.documentTotal || 0 }}<progress :value="migration.documentProcessed || 0" :max="Math.max(migration.documentTotal || 0, 1)" /></label><label>记忆 {{ migration.memoryProcessed || 0 }} / {{ migration.memoryTotal || 0 }}<progress :value="migration.memoryProcessed || 0" :max="Math.max(migration.memoryTotal || 0, 1)" /></label></div>
    <p v-if="migration.errorSummary" class="error">失败信息：{{ migration.errorSummary }}</p>
  </section>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({ migration: Object })
const isWorking = computed(() => props.migration && !['COMPLETED', 'FAILED'].includes(props.migration.status))
</script>

<style scoped>
.progress { display: grid; gap: 10px; margin-top: 16px; padding: 14px; border: 1px solid #b9d1ff; border-radius: 10px; background: #f3f7ff; }.heading { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; }.progress p { margin: 0; line-height: 1.6; color: #42526a; }.meters { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }.meters label { display: grid; gap: 5px; color: #536174; font-size: 13px; }.meters progress { width: 100%; }.error { color: #991b1b !important; } @media (max-width: 640px) { .meters { grid-template-columns: 1fr; } }
</style>
