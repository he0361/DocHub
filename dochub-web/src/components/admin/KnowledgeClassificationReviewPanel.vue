<template>
  <section class="classification-review" aria-label="知识分类待确认">
    <header>
      <div>
        <span>Knowledge Classification Review</span>
        <h3>系统未能高置信度确认知识域</h3>
      </div>
      <strong>待管理员确认</strong>
    </header>

    <p class="review-reason">{{ review?.reason || '当前证据不足以安全地自动创建知识域，请选择已有路由或确认保存的 LLM 提案。' }}</p>

    <div v-if="candidates.length" class="review-candidates">
      <article v-for="candidate in candidates" :key="candidate.routeCode">
        <div>
          <strong>{{ candidate.routeName || candidate.routeCode }}</strong>
          <small>{{ candidate.routeCode }}</small>
        </div>
        <span>综合分 {{ formatScore(candidate.combinedScore) }}</span>
        <p>{{ candidate.reason || '由词面、语义与项目标识证据共同产生。' }}</p>
      </article>
    </div>

    <div class="review-grid">
      <section class="review-option">
        <h4>使用系统已有路由</h4>
        <label>
          已有知识域
          <select v-model="selectedScopeCode" aria-label="已有知识域">
            <option value="">请选择知识域</option>
            <option v-for="scope in scopes" :key="scope.scopeCode" :value="scope.scopeCode">
              {{ scope.scopeName }}（{{ scope.scopeCode }}）
            </option>
          </select>
        </label>
        <label>
          已有主题
          <select v-model="selectedTopicCode" aria-label="已有主题" :disabled="!selectedScopeCode">
            <option value="">无主题</option>
            <option v-for="topic in availableTopics" :key="topic.topicCode" :value="topic.topicCode">
              {{ topic.topicName }}（{{ topic.topicCode }}）
            </option>
          </select>
        </label>
        <button type="button" :disabled="!selectedScopeCode || loading" @click="resolveExisting">
          确认使用已有知识域
        </button>
      </section>

      <section class="review-option review-option-proposal">
        <h4>LLM 保存提案</h4>
        <dl>
          <div><dt>知识域</dt><dd>{{ proposal.scopeName || '-' }}（{{ proposal.scopeCode || '-' }}）</dd></div>
          <div><dt>主题</dt><dd>{{ proposal.topicName || '无主题' }}<template v-if="proposal.topicCode">（{{ proposal.topicCode }}）</template></dd></div>
        </dl>
        <p>此操作严格应用当前审核记录里的保存提案，不会重新调用模型生成另一套结果。</p>
        <button class="trust-button" type="button" :disabled="loading || !proposal.scopeName" @click="trustProposal">
          相信 LLM 创建
        </button>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  review: { type: Object, required: true },
  scopes: { type: Array, default: () => [] },
  topics: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})
const emit = defineEmits(['resolve'])
const selectedScopeCode = ref('')
const selectedTopicCode = ref('')

watch(() => props.review?.reviewId, () => {
  selectedScopeCode.value = ''
  selectedTopicCode.value = ''
})
watch(selectedScopeCode, () => {
  selectedTopicCode.value = ''
})

const proposal = computed(() => parseJson(props.review?.proposedScopeJson, {}))
const candidates = computed(() => parseJson(props.review?.candidateJson, []).map((item) => ({
  routeCode: item?.route?.routeCode || item?.routeCode || '',
  routeName: item?.route?.routeName || item?.routeName || '',
  combinedScore: item?.combinedScore,
  reason: item?.reason || ''
})))
const availableTopics = computed(() => props.topics.filter((topic) => topic.scopeCode === selectedScopeCode.value))

function parseJson(value, fallback) {
  if (!value) return fallback
  if (typeof value !== 'string') return value
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

function formatScore(value) {
  const score = Number(value)
  return Number.isFinite(score) ? score.toFixed(2) : '-'
}

function resolveExisting() {
  emit('resolve', {
    reviewId: props.review.reviewId,
    version: props.review.version,
    mode: 'USE_EXISTING',
    scopeCode: selectedScopeCode.value,
    topicCode: selectedTopicCode.value
  })
}

function trustProposal() {
  emit('resolve', {
    reviewId: props.review.reviewId,
    version: props.review.version,
    mode: 'TRUST_LLM_PROPOSAL'
  })
}
</script>

<style scoped>
.classification-review { margin-bottom: 18px; padding: 18px; border: 1px solid #e4b866; border-radius: 16px; background: #fff9ec; }
.classification-review header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.classification-review header span { color: #9a6a22; font-size: 12px; letter-spacing: .08em; text-transform: uppercase; }
.classification-review h3 { margin: 4px 0 0; }
.classification-review header > strong { padding: 5px 10px; border-radius: 999px; background: #f5dfad; color: #765018; font-size: 12px; white-space: nowrap; }
.review-reason { color: #66543b; }
.review-candidates { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 10px; margin: 14px 0; }
.review-candidates article { padding: 12px; border: 1px solid #ead8b5; border-radius: 12px; background: #fffdf8; }
.review-candidates article > div { display: flex; justify-content: space-between; gap: 8px; }
.review-candidates small, .review-candidates span, .review-candidates p { color: #796c59; font-size: 12px; }
.review-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.review-option { display: flex; flex-direction: column; gap: 10px; padding: 14px; border: 1px solid #e7dcc8; border-radius: 12px; background: #fff; }
.review-option h4, .review-option p, .review-option dl { margin: 0; }
.review-option label { display: grid; gap: 5px; color: #675e50; font-size: 13px; }
.review-option select { width: 100%; padding: 9px; border: 1px solid #d8cdbb; border-radius: 9px; background: white; }
.review-option button { padding: 10px 14px; border: 0; border-radius: 9px; background: #3f7651; color: white; }
.review-option button:disabled { cursor: not-allowed; opacity: .45; }
.review-option-proposal { border-color: #e0c999; }
.review-option-proposal dl { display: grid; gap: 7px; }
.review-option-proposal dl div { display: grid; grid-template-columns: 60px 1fr; gap: 8px; }
.review-option-proposal dt { color: #807461; }
.review-option-proposal dd { margin: 0; font-weight: 600; }
.review-option .trust-button { background: #b86f35; }
@media (max-width: 800px) { .review-grid { grid-template-columns: 1fr; } }
</style>
