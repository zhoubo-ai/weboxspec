import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const apiOrigin = process.env.WEBOX_API_ORIGIN ?? 'http://127.0.0.1:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: apiOrigin,
        changeOrigin: false,
        timeout: 0,
        proxyTimeout: 0,
      },
      '/media': {
        target: apiOrigin,
        changeOrigin: false,
      },
    },
  },
});
