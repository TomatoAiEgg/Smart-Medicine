<script setup lang="ts">
import { computed, ref } from 'vue';
import type { MenuItem, ViewKey } from './views';

export interface LayoutTab {
  key: ViewKey;
  label: string;
  closable: boolean;
}

const props = defineProps<{
  activeView: ViewKey;
  title: string;
  subtitle: string;
  menuItems: MenuItem[];
  counts: Partial<Record<ViewKey, number>>;
  notice: { tone: 'info' | 'success' | 'error'; text: string } | null;
  tabs: LayoutTab[];
}>();

defineEmits<{
  switchView: [view: ViewKey];
  refresh: [];
  closeTab: [view: ViewKey];
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

const activeGroupNames = computed(() => {
  const names = new Set<string>();
  for (const item of props.menuItems) {
    if (item.key === props.activeView) names.add(item.group);
  }
  return names;
});
const expandedGroupNames = ref<Set<string>>(new Set(['订单管理']));

const groupIcons: Record<string, string> = {
  系统管理: '▣',
  参数管理: '▦',
  机构管理: '▤',
  物流管理: '▱',
  订单管理: '▥',
  维护管理: '▧',
  标签管理: '▨',
  短信管理: '▩',
  药品管理: '▰',
  报表管理: '▢',
  煎煮管理: '▮',
};

function groupIcon(groupName: string) {
  return groupIcons[groupName] ?? '▣';
}

function isGroupOpen(groupName: string) {
  return activeGroupNames.value.has(groupName) || expandedGroupNames.value.has(groupName);
}

function toggleGroup(groupName: string) {
  const nextGroupNames = new Set(expandedGroupNames.value);
  if (nextGroupNames.has(groupName)) {
    nextGroupNames.delete(groupName);
  } else {
    nextGroupNames.add(groupName);
  }
  expandedGroupNames.value = nextGroupNames;
}
</script>

<template>
  <div class="legacy-shell">
    <header class="legacy-north">
      <div class="legacy-logo">良益堂煎药管理系统</div>
    </header>

    <aside class="legacy-west">
      <button
        type="button"
        class="legacy-home-link"
        :class="{ active: activeView === 'dashboard' }"
        @click="$emit('switchView', 'dashboard')"
      >
        <span>首页</span>
        <b>«</b>
      </button>

      <nav class="legacy-accordion">
        <section
          v-for="group in groupedMenuItems"
          :key="group.name"
          class="legacy-menu-group"
          :class="{ open: isGroupOpen(group.name) }"
        >
          <button type="button" class="legacy-menu-title" @click="toggleGroup(group.name)">
            <span class="legacy-menu-icon">{{ groupIcon(group.name) }}</span>
            <span>{{ group.name }}</span>
            <b>⌄</b>
          </button>

          <div class="legacy-menu-items">
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
          </div>
        </section>
      </nav>

      <div class="legacy-service-targets">
        <span>服务目标</span>
        <code>order-service :18082</code>
        <code>workflow-service :18085</code>
        <code>decoction-service :18087</code>
        <code>ops-service :18086</code>
        <code>logistics-service :18088</code>
      </div>
    </aside>

    <main class="legacy-center">
      <div class="legacy-tabs">
        <div
          v-for="tab in tabs"
          :key="tab.key"
          class="legacy-tab"
          :class="{ active: activeView === tab.key }"
        >
          <button type="button" class="legacy-tab-main" @click="$emit('switchView', tab.key)">
            {{ tab.label }}
          </button>
          <button
            v-if="tab.closable"
            type="button"
            class="legacy-tab-close"
            :title="`关闭${tab.label}`"
            @click.stop="$emit('closeTab', tab.key)"
          >
            ×
          </button>
        </div>
      </div>

      <section class="legacy-content">
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
      </section>
    </main>
  </div>
</template>
