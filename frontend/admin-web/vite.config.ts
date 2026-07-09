import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  return {
    plugins: [vue()],
    server: {
      proxy: {
        '/order-api': {
          target: env.VITE_ORDER_SERVICE_URL || 'http://127.0.0.1:18082',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/order-api/, ''),
        },
        '/workflow-api': {
          target: env.VITE_WORKFLOW_SERVICE_URL || 'http://127.0.0.1:18085',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/workflow-api/, ''),
        },
        '/decoction-api': {
          target: env.VITE_DECOCTION_SERVICE_URL || 'http://127.0.0.1:18087',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/decoction-api/, ''),
        },
        '/ops-api': {
          target: env.VITE_OPS_SERVICE_URL || 'http://127.0.0.1:18086',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/ops-api/, ''),
        },
        '/logistics-api': {
          target: env.VITE_LOGISTICS_SERVICE_URL || 'http://127.0.0.1:18088',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/logistics-api/, ''),
        },
        '/callback-api': {
          target: env.VITE_CALLBACK_SERVICE_URL || 'http://127.0.0.1:18089',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/callback-api/, ''),
        },
        '/portal-api': {
          target: env.VITE_PORTAL_SERVICE_URL || 'http://127.0.0.1:18090',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/portal-api/, ''),
        },
      },
    },
  };
});
