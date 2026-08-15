import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

const proxyPrefixes = [
  '/order-api',
  '/workflow-api',
  '/message-api',
  '/decoction-api',
  '/ops-api',
  '/logistics-api',
  '/callback-api',
  '/portal-api',
  '/report-api',
  '/integration-api',
  '/auth-api',
] as const;

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = env.VITE_GATEWAY_URL || 'http://127.0.0.1:18080';

  return {
    plugins: [react()],
    build: {
      chunkSizeWarningLimit: 700,
      rollupOptions: {
        output: {
          manualChunks(id) {
            const normalizedId = id.replace(/\\/g, '/');

            if (!normalizedId.includes('/node_modules/')) return undefined;
            if (
              normalizedId.includes('/node_modules/@ant-design/pro-') ||
              normalizedId.includes('/node_modules/.pnpm/@ant-design+pro-')
            ) {
              return 'vendor-pro';
            }
            if (normalizedId.includes('/node_modules/@tanstack/react-query/')) return 'vendor-query';
            if (
              normalizedId.includes('/node_modules/react/') ||
              normalizedId.includes('/node_modules/react-dom/') ||
              normalizedId.includes('/node_modules/react-router-dom/') ||
              normalizedId.includes('/node_modules/scheduler/')
            ) {
              return 'vendor-react';
            }
            if (normalizedId.includes('/node_modules/@ant-design/icons/')) return 'vendor-icons';
            if (normalizedId.includes('/node_modules/dayjs/')) return 'vendor-dayjs';
            if (normalizedId.includes('/node_modules/antd/')) return 'vendor-antd';
            if (
              normalizedId.includes('/node_modules/@ant-design/') ||
              normalizedId.includes('/node_modules/@rc-component/') ||
              normalizedId.includes('/node_modules/rc-')
            ) {
              return 'vendor-antd-base';
            }
            return 'vendor';
          },
        },
      },
    },
    server: {
      proxy: Object.fromEntries(
        proxyPrefixes.map((prefix) => [
          prefix,
          {
            target,
            changeOrigin: true,
          },
        ]),
      ),
    },
  };
});
