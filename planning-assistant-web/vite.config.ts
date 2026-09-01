import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import { resolveApiProxyTarget } from './src/config/apiProxy';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');

  return {
    plugins: [vue()],
    server: { proxy: { '/api': resolveApiProxyTarget(env) } }
  };
});
