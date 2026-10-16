import { defineConfig, mergeConfig } from 'vitest/config';
import vite from './vite.config.ts';

export default mergeConfig(vite, defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    clearMocks: true,
    restoreMocks: true,
    css: true,
  },
}));
