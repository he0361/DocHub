<template>
  <div class="workbench">
    <!-- 顶部欢迎 + 日期 -->
    <header class="wb-hero">
      <div class="wb-hero-inner">
        <div class="wb-eyebrow">文枢 DocHub</div>
        <h1 class="wb-title">{{ greeting }}，欢迎回到企业文档工作台</h1>
        <p class="wb-subtitle">围绕企业文档完成「问 → 写 → 管 → 落地」闭环，让每一次知识协作都清晰、可追溯。</p>
        <div class="wb-ctas">
          <RouterLink to="/chat" class="wb-cta primary">
            <span class="wb-cta-icon">☺</span>开始提问
          </RouterLink>
          <RouterLink to="/compose" class="wb-cta">
            <span class="wb-cta-icon">✎</span>开始仿写
          </RouterLink>
        </div>
      </div>
      <div class="wb-date">{{ todayText }}</div>
    </header>

    <!-- 快捷入口 -->
    <section class="wb-section">
      <div class="wb-section-head">
        <h2>快捷入口</h2>
      </div>
      <div class="wb-quick-grid">
        <RouterLink to="/chat" class="wb-quick">
          <span class="wb-quick-icon chat">☺</span>
          <div>
            <strong>智能问答</strong>
            <p>制度知识问答 · 证据驱动</p>
          </div>
        </RouterLink>
        <RouterLink to="/compose" class="wb-quick">
          <span class="wb-quick-icon compose">✎</span>
          <div>
            <strong>文档仿写</strong>
            <p>给参考文档与需求，仿照生成</p>
          </div>
        </RouterLink>
        <RouterLink to="/admin/documents" class="wb-quick">
          <span class="wb-quick-icon upload">⇪</span>
          <div>
            <strong>上传文档</strong>
            <p>接入知识库 · 自动解析索引</p>
          </div>
        </RouterLink>
        <RouterLink to="/skills" class="wb-quick">
          <span class="wb-quick-icon skill">✦</span>
          <div>
            <strong>技能列表</strong>
            <p>查看与启用已安装技能</p>
          </div>
        </RouterLink>
      </div>
    </section>

    <!-- 核心指标 -->
    <section class="wb-section">
      <div class="wb-section-head">
        <h2>核心数据</h2>
      </div>
      <div class="wb-kpi-grid">
        <RouterLink to="/admin/documents" class="wb-kpi">
          <span class="wb-kpi-label">知识库文档</span>
          <strong class="wb-kpi-value">{{ stats.documents }}</strong>
          <span class="wb-kpi-hint">点击查看文档列表</span>
        </RouterLink>
        <RouterLink to="/compose" class="wb-kpi">
          <span class="wb-kpi-label">生成记录</span>
          <strong class="wb-kpi-value">{{ stats.generated }}</strong>
          <span class="wb-kpi-hint">点击进入文档仿写</span>
        </RouterLink>
        <RouterLink to="/skills" class="wb-kpi">
          <span class="wb-kpi-label">已安装技能</span>
          <strong class="wb-kpi-value">{{ stats.skills }}</strong>
          <span class="wb-kpi-hint">点击查看技能列表</span>
        </RouterLink>
        <RouterLink to="/compose" class="wb-kpi">
          <span class="wb-kpi-label">文档模板</span>
          <strong class="wb-kpi-value">{{ stats.templates }}</strong>
          <span class="wb-kpi-hint">内置生成骨架</span>
        </RouterLink>
      </div>
    </section>

    <!-- 最近动态 -->
    <section class="wb-section">
      <div class="wb-section-head">
        <h2>最近动态</h2>
      </div>
      <div class="wb-recent-grid">
        <div class="wb-panel">
          <div class="wb-panel-head">
            <h3>最近文档</h3>
            <RouterLink to="/admin/documents" class="wb-more">全部 ›</RouterLink>
          </div>
          <div v-if="recentDocuments.length" class="wb-list">
            <RouterLink v-for="doc in recentDocuments" :key="doc.documentId" :to="`/admin/documents/${doc.documentId}`" class="wb-item">
              <span class="wb-item-dot" :class="statusClass(doc.parseStatus)"></span>
              <span class="wb-item-title">{{ doc.documentName }}</span>
              <span class="wb-item-time">{{ shortDate(doc.editTime) }}</span>
            </RouterLink>
          </div>
          <div v-else class="wb-empty">还没有文档，去<a href="/admin/documents" class="wb-empty-link">上传第一份文档</a></div>
        </div>

        <div class="wb-panel">
          <div class="wb-panel-head">
            <h3>最近生成记录</h3>
            <RouterLink to="/compose" class="wb-more">全部 ›</RouterLink>
          </div>
          <div v-if="recentRecords.length" class="wb-list">
            <RouterLink v-for="record in recentRecords" :key="record.recordCode" to="/compose" class="wb-item">
              <span class="wb-item-dot" :class="hasCode(record.generationStatus, 2) ? 'ok' : 'fail'"></span>
              <span class="wb-item-title">{{ record.templateName }}</span>
              <span class="wb-item-time">{{ shortDate(record.createTime) }}</span>
            </RouterLink>
          </div>
          <div v-else class="wb-empty">还没有生成记录，去<a href="/compose" class="wb-empty-link">文档仿写</a>试试</div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { manageApi, docgenApi, skillApi } from '../api/api'
import { hasCode } from '../utils/manageFormat'

const stats = ref({ documents: 0, generated: 0, skills: 0, templates: 0 })
const recentDocuments = ref([])
const recentRecords = ref([])

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() => {
  const now = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六']
  return `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 星期${week[now.getDay()]}`
})

function statusClass(value) {
  return String(value) === '2' ? 'ok' : 'pending'
}

function shortDate(value) {
  if (!value) return ''
  return String(value).slice(0, 10)
}

async function loadAll() {
  const [docPage, genPage, skillPage, templatePage] = await Promise.allSettled([
    manageApi.queryDocumentPage({ pageNo: '1', pageSize: '1' }),
    docgenApi.records({ pageNo: '1', pageSize: '5' }),
    skillApi.installedList({ pageNo: '1', pageSize: '1' }),
    docgenApi.templatePage({ pageNo: '1', pageSize: '1' })
  ])
  if (docPage.status === 'fulfilled') {
    stats.value.documents = Number(docPage.value?.total || 0)
    if (Number(stats.value.documents) > 0) {
      const docs = await manageApi.queryDocumentPage({ pageNo: '1', pageSize: '5' })
      recentDocuments.value = docs?.records || []
    }
  }
  if (genPage.status === 'fulfilled') {
    stats.value.generated = Number(genPage.value?.total || 0)
    recentRecords.value = genPage.value?.records || []
  }
  if (skillPage.status === 'fulfilled') {
    stats.value.skills = Number(skillPage.value?.total || 0)
  }
  if (templatePage.status === 'fulfilled') {
    stats.value.templates = Number(templatePage.value?.total || 0)
  }
}

onMounted(loadAll)
</script>

<style scoped>
.workbench {
  max-width: min(1240px, 96vw);
  margin: 0 auto;
}

.wb-hero {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 24px 22px;
  margin-bottom: 18px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background:
    radial-gradient(720px 240px at 10% -30%, rgba(217, 119, 87, 0.16), transparent 60%),
    radial-gradient(520px 200px at 92% -40%, rgba(192, 138, 62, 0.12), transparent 60%),
    var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}

.wb-eyebrow {
  font-size: 12px;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: var(--color-primary);
  font-weight: 700;
  margin-bottom: 8px;
}

.wb-title {
  margin: 0 0 8px;
  font-size: 24px;
  line-height: 1.25;
  color: var(--color-text-strong);
}

.wb-subtitle {
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
}

.wb-date {
  flex: none;
  font-size: 13px;
  color: var(--color-muted);
  padding: 6px 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 999px;
}

.wb-ctas {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.wb-cta {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 10px 20px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  transition: transform 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}

.wb-cta:hover {
  border-color: var(--color-primary);
  transform: translateY(-1px);
}

.wb-cta.primary {
  background: linear-gradient(135deg, #e08a5f, var(--color-accent));
  border-color: transparent;
  color: #fff;
  box-shadow: 0 6px 18px rgba(217, 119, 87, 0.28);
}

.wb-cta-icon {
  font-size: 15px;
}

.wb-section {
  margin-bottom: 18px;
}

.wb-section-head h2 {
  margin: 0 0 10px;
  font-size: 15px;
  color: var(--color-text-strong);
}

.wb-quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 10px;
}

.wb-quick {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 16px;
  background: var(--color-surface);
  backdrop-filter: blur(22px) saturate(140%);
  -webkit-backdrop-filter: blur(22px) saturate(140%);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1), var(--shadow-sm);
  transition: box-shadow 0.18s ease, transform 0.18s ease, border-color 0.18s ease;
}

.wb-quick:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.wb-quick-icon {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  font-size: 16px;
}

.wb-quick-icon.chat { background: var(--color-primary-soft); color: var(--color-primary-strong); }
.wb-quick-icon.compose { background: rgba(192, 138, 62, 0.14); color: #a36a1e; }
.wb-quick-icon.upload { background: rgba(90, 138, 106, 0.14); color: #4a6e55; }
.wb-quick-icon.skill { background: rgba(217, 119, 87, 0.12); color: #b8572c; }

.wb-quick strong {
  font-size: 14px;
  color: var(--color-text-strong);
}

.wb-quick p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-muted);
}

.wb-kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
}

.wb-kpi {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 18px 18px;
  background: var(--color-surface);
  backdrop-filter: blur(22px) saturate(140%);
  -webkit-backdrop-filter: blur(22px) saturate(140%);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1), var(--shadow-sm);
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.wb-kpi:hover {
  border-color: var(--color-primary);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1), 0 8px 28px rgba(94, 234, 212, 0.12);
  transform: translateY(-2px);
}

.wb-kpi-label {
  font-size: 13px;
  color: var(--color-muted-strong);
}

.wb-kpi-value {
  font-size: 42px;
  line-height: 1;
  font-weight: 800;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
  color: var(--color-primary-strong);
}

.wb-kpi-hint {
  font-size: 11px;
  color: var(--color-muted);
}

.wb-recent-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

@media (max-width: 820px) {
  .wb-recent-grid { grid-template-columns: 1fr; }
}

.wb-panel {
  background: var(--color-surface);
  backdrop-filter: blur(22px) saturate(140%);
  -webkit-backdrop-filter: blur(22px) saturate(140%);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 16px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1), var(--shadow-sm);
}

.wb-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.wb-panel-head h3 {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-strong);
}

.wb-more {
  font-size: 12px;
  color: var(--color-primary);
}

.wb-list {
  display: flex;
  flex-direction: column;
}

.wb-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 4px;
  border-bottom: 1px solid var(--color-border);
  font-size: 13px;
}

.wb-item:last-child {
  border-bottom: none;
}

.wb-item-dot {
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: 50%;
  background: var(--color-border-strong);
}

.wb-item-dot.ok { background: var(--color-success); }
.wb-item-dot.fail { background: var(--color-danger); }
.wb-item-dot.pending { background: var(--color-warning); }

.wb-item-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text);
}

.wb-item-time {
  flex: none;
  font-size: 12px;
  color: var(--color-muted);
}

.wb-empty {
  padding: 18px 4px;
  font-size: 13px;
  color: var(--color-muted);
}

.wb-empty-link {
  color: var(--color-primary);
}
</style>
