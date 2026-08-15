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
