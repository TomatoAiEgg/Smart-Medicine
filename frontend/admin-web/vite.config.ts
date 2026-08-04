import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  return {
    plugins: [vue()],
    server: {
      proxy: {
        '/order-api': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:18080',
          changeOrigin: true,
        },
        '/workflow-api': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:18080',
          changeOrigin: true,
        },
        '/message-api': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:18080',
          changeOrigin: true,
        },
        '/decoction-api': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:18080',
          changeOrigin: true,
        },
        '/ops-api': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:18080',
          changeOrigin: true,
        },
        '/logistics-api': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:18080',
          changeOrigin: true,
        },
        '/callback-api': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:18080',
          changeOrigin: true,
        },
        '/portal-api': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:18080',
          changeOrigin: true,
        },
        '/report-api': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:18080',
          changeOrigin: true,
        },
        '/integration-api': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:18080',
          changeOrigin: true,
        },
        '/auth-api': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:18080',
          changeOrigin: true,
        },
      },
    },
  };
});
