<template>
  <router-view v-if="isFullscreenLayout" />

  <div v-else class="ws-shell">
    <aside class="ws-sidebar">
      <div class="ws-brand">
        <img class="ws-brand-mark" src="/logo.png" alt="文枢 DocHub" />
        <div class="ws-brand-text">
          <strong>文枢</strong>
          <span>DocHub</span>
        </div>
      </div>

      <div class="ws-section">工作台</div>
      <nav class="ws-nav">
        <router-link to="/workbench" class="ws-nav-item" active-class="is-active">
          <span class="ws-nav-icon">⌂</span><span>工作台</span>
        </router-link>
        <router-link to="/chat" class="ws-nav-item" active-class="is-active">
          <span class="ws-nav-icon">☺</span><span>智能问答</span>
        </router-link>
        <router-link to="/compose" class="ws-nav-item" active-class="is-active">
          <span class="ws-nav-icon">✎</span><span>文档仿写</span>
        </router-link>
        <router-link to="/skills" class="ws-nav-item" active-class="is-active">
          <span class="ws-nav-icon">✦</span><span>技能列表</span>
        </router-link>
      </nav>

      <div class="ws-nav-spacer"></div>

      <div class="ws-section">系统</div>
      <nav class="ws-nav">
        <router-link to="/admin/dashboard" class="ws-nav-item" active-class="is-active">
          <span class="ws-nav-icon">▤</span><span>管理控制台</span>
        </router-link>
      </nav>
    </aside>

    <div class="ws-body">
      <header class="ws-topbar">
        <div class="ws-topbar-title">文枢 DocHub · 智能文档工作台</div>
        <div class="ws-topbar-actions">
          <router-link to="/admin/dashboard" class="ws-topbar-link">管理控制台</router-link>
        </div>
      </header>

      <main class="ws-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const isFullscreenLayout = computed(() => route.meta?.layout === 'fullscreen')
</script>

<style scoped>
.ws-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(180px, 15vw) 1fr;
  background: var(--color-bg);
}

.ws-sidebar {
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  padding: 18px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}

.ws-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 8px 14px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 6px;
}

.ws-brand-mark {
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 10px;
  object-fit: cover;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
}

.ws-brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.ws-brand-text strong {
  font-size: 16px;
  color: var(--color-text-strong);
  letter-spacing: 1px;
  font-family: var(--font-display);
}

.ws-brand-text span {
  font-size: 11px;
  color: var(--color-muted);
  letter-spacing: 0.5px;
}

.ws-section {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.6px;
  color: var(--color-muted);
  padding: 8px 10px 4px;
}

.ws-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ws-nav-spacer {
  flex: 1;
}

.ws-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: var(--radius-sm);
  color: var(--color-text);
  font-size: 14px;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.ws-nav-item:hover {
  background: var(--color-surface-soft);
}

.ws-nav-item.is-active {
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-weight: 600;
}

.ws-nav-icon {
  width: 18px;
  text-align: center;
  font-size: 15px;
  opacity: 0.8;
}

.ws-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.ws-topbar {
  height: 48px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.ws-topbar-title {
  font-size: 13px;
  color: var(--color-muted-strong);
}

.ws-topbar-link {
  font-size: 13px;
  color: var(--color-primary-strong);
  font-weight: 600;
}

.ws-main {
  flex: 1;
  min-width: 0;
  padding: 14px 16px 18px;
}

@media (max-width: 700px) {
  .ws-shell {
    grid-template-columns: 1fr;
  }

  .ws-sidebar {
    position: static;
    height: auto;
    border-right: none;
    border-bottom: 1px solid var(--color-border);
  }

  .ws-brand {
    justify-content: center;
  }

  .ws-nav {
    flex-direction: row;
    justify-content: space-around;
  }
}
</style>
