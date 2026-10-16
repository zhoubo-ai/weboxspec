import { defineConfig } from '@playwright/test';
import { readFileSync } from 'node:fs';
if (!process.env.WEBOX_PERSISTENCE_STATE) throw new Error('Persistence tests must run through backend -Ppersistence verify.');
const state = JSON.parse(readFileSync(process.env.WEBOX_PERSISTENCE_STATE, 'utf8'));
const backend = new URL(state.apiOrigin);
if (backend.hostname !== '127.0.0.1' || backend.protocol !== 'http:' || [8080, 18081, 3306, 15174].includes(Number(backend.port))) throw new Error('An isolated persistence backend is required.');
export default defineConfig({
  testDir: './tests/e2e', testMatch: 'persistence.spec.ts', fullyParallel: false, workers: 1, retries: 0,
  outputDir: state.browserOutput, reporter: [['list']],
  use: { baseURL: 'http://127.0.0.1:15174', viewport: { width: 1440, height: 900 }, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: ['chromium', 'firefox', 'webkit'].map(browserName => ({ name: browserName, use: { browserName: browserName as 'chromium' | 'firefox' | 'webkit', launchOptions: browserName === 'firefox' ? { firefoxUserPrefs: { 'network.proxy.type': 0 } } : {} } })),
  webServer: { command: 'npm run dev -- --port 15174', url: 'http://127.0.0.1:15174', env: { WEBOX_API_ORIGIN: backend.origin }, reuseExistingServer: false, timeout: 30000, gracefulShutdown: { signal: 'SIGTERM', timeout: 5000 } },
});
