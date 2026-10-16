import { defineConfig } from '@playwright/test';
import { readFileSync } from 'node:fs';
if (!process.env.WEBOX_LOAD_STATE) throw new Error('Run performance tests through -Pload gatling:test.');
const state = JSON.parse(readFileSync(process.env.WEBOX_LOAD_STATE, 'utf8'));
const origin = new URL(state.apiOrigin);
if (origin.hostname !== '127.0.0.1' || origin.protocol !== 'http:' || [8080, 18081, 3306, 15175].includes(Number(origin.port))) throw new Error('An isolated load backend is required.');
export default defineConfig({
  testDir: './tests/performance', fullyParallel: false, workers: 1, retries: 0, timeout: 450000,
  outputDir: state.output, reporter: [['list']],
  use: { baseURL: 'http://127.0.0.1:15175', browserName: 'chromium', viewport: { width: 1440, height: 900 }, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  webServer: { command: 'npm run preview -- --host 127.0.0.1 --port 15175 --strictPort', url: 'http://127.0.0.1:15175', env: { WEBOX_API_ORIGIN: origin.origin }, reuseExistingServer: false, timeout: 30000, gracefulShutdown: { signal: 'SIGTERM', timeout: 5000 } },
});
