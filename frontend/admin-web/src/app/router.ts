import { defineComponent } from 'vue';
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { routeItems } from './views';

const RouteMarker = defineComponent({
  name: 'RouteMarker',
  setup() {
    return () => null;
  },
});

const staticRoutes: RouteRecordRaw[] = routeItems.map((item) => ({
  path: item.path,
  name: item.key,
  component: RouteMarker,
  meta: {
    routeKey: item.key,
    title: item.label,
    legacyRoute: item.legacyRoute,
  },
}));

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    ...staticRoutes,
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
});
