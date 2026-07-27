<script setup lang="ts">
import type { AppRouteItem } from '../../app/views';

defineProps<{
  item: AppRouteItem;
}>();
</script>

<template>
  <article class="pending-menu-page">
    <section class="pending-menu-summary">
      <div>
        <strong>页面待实现</strong>
        <span>接口待接入</span>
      </div>
      <p>
        此路由已接入菜单和浏览器地址，等待后端 API、权限和页面组件实现后再承载业务数据。
      </p>
    </section>

    <section class="pending-menu-grid">
      <div>
        <span>菜单名称</span>
        <strong>{{ item.label }}</strong>
      </div>
      <div>
        <span>所属一级菜单</span>
        <strong>{{ item.group }}</strong>
      </div>
      <div>
        <span>旧路由</span>
        <strong>{{ item.legacyRoute ?? '无旧后台路由' }}</strong>
      </div>
      <div>
        <span>新路由</span>
        <strong>{{ item.path }}</strong>
      </div>
      <div>
        <span>优先级</span>
        <strong>{{ item.priority }}</strong>
      </div>
      <div>
        <span>计划组件</span>
        <strong>{{ item.plannedComponent }}</strong>
      </div>
    </section>

    <section class="pending-menu-section">
      <h2>待接入 API</h2>
      <ul>
        <li v-for="dependency in item.apiDependencies" :key="dependency">{{ dependency }}</li>
      </ul>
    </section>

    <section class="pending-menu-section">
      <h2>核心操作</h2>
      <ul>
        <li v-for="action in item.coreActions" :key="action">{{ action }}</li>
      </ul>
    </section>
  </article>
</template>
