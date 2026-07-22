<script setup lang="ts">
import { computed } from 'vue';
import type { MenuItem, ViewKey } from './views';

const props = defineProps<{
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

const groupedMenuItems = computed(() => {
  const groups: Array<{ name: string; items: MenuItem[] }> = [];
  for (const item of props.menuItems) {
    let group = groups.find((entry) => entry.name === item.group);
    if (!group) {
      group = { name: item.group, items: [] };
      groups.push(group);
    }
    group.items.push(item);
  }
  return groups;
});
</script>

<template>
  <div class="app-shell">
    <header class="system-header">
      <div class="brand">
        <div class="brand-mark">智</div>
        <div>
          <strong>智能药房</strong>
          <span>SaaS 管理平台</span>
        </div>
      </div>

      <div class="system-actions">
        <span>开发测试环境</span>
        <button class="legacy-top-btn" type="button" title="刷新当前页面" @click="$emit('refresh')">
          刷新
        </button>
      </div>
    </header>

    <aside class="sidebar">
      <nav class="nav">
        <section v-for="group in groupedMenuItems" :key="group.name" class="nav-group">
          <h2>{{ group.name }}</h2>
          <button
            v-for="item in group.items"
            :key="item.key"
            type="button"
            :class="{ active: activeView === item.key }"
            @click="$emit('switchView', item.key)"
          >
            <span>{{ item.label }}</span>
            <b v-if="item.showCount">{{ counts[item.key] ?? 0 }}</b>
          </button>
        </section>
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
        <button class="icon-button" type="button" title="刷新当前页面" @click="$emit('refresh')">
          ↻
        </button>
      </header>

      <div v-if="notice" class="notice" :class="notice.tone">
        {{ notice.text }}
      </div>

      <section class="page-frame">
        <slot />
      </section>
    </main>
  </div>
</template>
