import { defineConfig } from '@playwright/test';
import { randomBytes } from 'node:crypto';

const port = Number(process.env.WEBOX_E2E_PORT ?? '15173');
if (!Number.isInteger(port) || port < 1024 || port > 65535 || [5173, 8080, 3306].includes(port)) {
  throw new Error('WEBOX_E2E_PORT must be a dedicated test port (1024–65535).');
}
const apiOrigin = new URL(process.env.WEBOX_E2E_API_ORIGIN ?? 'http://127.0.0.1:18081');
if (apiOrigin.protocol !== 'http:' || !['127.0.0.1', 'localhost'].includes(apiOrigin.hostname)
  || !apiOrigin.port || [port, 8080, 3306, 5173].includes(Number(apiOrigin.port))
  || apiOrigin.pathname !== '/' || apiOrigin.search || apiOrigin.hash || apiOrigin.username || apiOrigin.password) {
  throw new Error('WEBOX_E2E_API_ORIGIN must identify a separate local test backend, never the development server.');
}
process.env.WEBOX_E2E_FIXTURE_KEY ??= randomBytes(24).toString('hex');
const baseURL = `http://127.0.0.1:${port}`;
const browsers = ['chromium', 'firefox', 'webkit'] as const;

export default defineConfig({
  testDir: './tests/e2e',
  testIgnore: ['**/*-live.spec.ts', '**/persistence.spec.ts'],
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  workers: 2,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: browsers.flatMap(browserName => [375, 768, 1440].map(width => ({
    name: `${browserName}-${width}`,
    use: {
      browserName,
      viewport: { width, height: 900 },
      // Tests use loopback only. Firefox's macOS system proxy can stall these requests.
      launchOptions: browserName === 'firefox' ? { firefoxUserPrefs: { 'network.proxy.type': 0 } } : {},
    },
  }))),
  webServer: [{
    command: '../scripts/e2e-backend.sh',
    url: `${apiOrigin.origin}/api/v1/health`,
    env: { WEBOX_E2E_API_ORIGIN: apiOrigin.origin, WEBOX_E2E_FIXTURE_KEY: process.env.WEBOX_E2E_FIXTURE_KEY },
    reuseExistingServer: false,
    timeout: 120_000,
    gracefulShutdown: { signal: 'SIGTERM', timeout: 10_000 },
  }, {
    command: `npm run dev -- --port ${port}`,
    url: baseURL,
    env: { WEBOX_API_ORIGIN: apiOrigin.origin },
    reuseExistingServer: process.env.WEBOX_E2E_REUSE_SERVER === 'true',
    timeout: 30_000,
    gracefulShutdown: { signal: 'SIGTERM', timeout: 5_000 },
  }],
});
