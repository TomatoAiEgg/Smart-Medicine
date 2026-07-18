<script setup lang="ts">
import type { MenuItem, ViewKey } from './views';

defineProps<{
  activeView: ViewKey;
  title: string;
  subtitle: string;
  menuItems: MenuItem[];
  counts: Partial<Record<ViewKey, number>>;
  notice: { tone: 'info' | 'success' | 'error'; text: string } | null;
}>();

defineEmits<{
  switchView: [view: ViewKey];
  refresh: [];
}>();
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">智</div>
        <div>
          <strong>智能药房</strong>
          <span>SaaS 管理台</span>
        </div>
      </div>

      <nav class="nav">
        <button
          v-for="item in menuItems"
          :key="item.key"
          type="button"
          :class="{ active: activeView === item.key }"
          @click="$emit('switchView', item.key)"
        >
          <span>{{ item.label }}</span>
          <b v-if="item.showCount">{{ counts[item.key] ?? 0 }}</b>
        </button>
      </nav>

      <div class="service-panel">
        <span>服务目标</span>
        <code>order-service :18082</code>
        <code>workflow-service :18085</code>
        <code>decoction-service :18087</code>
        <code>ops-service :18086</code>
        <code>logistics-service :18088</code>
        <code>callback-service :18089</code>
        <code>portal-service :18090</code>
        <code>report-service :18091</code>
        <code>integration-service :18092</code>
      </div>
    </aside>

    <main class="content">
      <header class="topbar">
        <div>
          <p>{{ subtitle }}</p>
          <h1>{{ title }}</h1>
        </div>
        <button class="icon-button" type="button" title="刷新当前任务" @click="$emit('refresh')">
          ↻
        </button>
      </header>

      <div v-if="notice" class="notice" :class="notice.tone">
        {{ notice.text }}
      </div>

      <slot />
    </main>
  </div>
</template>
