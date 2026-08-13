import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import AdminRouteHost from './AdminRouteHost.vue';
import { routeItems } from './views';

const staticRoutes: RouteRecordRaw[] = routeItems.map((item) => ({
  path: item.path,
  name: item.key,
  component: AdminRouteHost,
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

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : routeItems[0].label;
  document.title = `${title} - 智能药房 SaaS`;
});
