import { expect, test, type BrowserContext } from '@playwright/test';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
const state = JSON.parse(readFileSync(process.env.WEBOX_LOAD_STATE!, 'utf8'));
const count = state.smoke ? 2 : 20;
const limits = { menu: 2000, filter: 2000, search: 2000, cart: 300, order: 3000, inventory: 2000, dashboard: 3000 };
type Metric = keyof typeof limits;
type Attempt = { attempt: number; elapsedMs: number | null; failure: string | null; activeStreams: number | null };
const results = Object.fromEntries(Object.keys(limits).map(key => [key, Array.from({ length: count }, (_, i) => ({ attempt: i + 1, elapsedMs: null, failure: 'Not executed', activeStreams: null }))])) as Record<Metric, Attempt[]>;
async function cookie(context: BrowserContext, value: string) { await context.addCookies([{ name: 'WEBOXSESSION', value, domain: '127.0.0.1', path: '/', httpOnly: true, secure: false, sameSite: 'Lax' }]); }
test('actual DOM availability during the same sustained employee load', async ({ page, context, browser }) => {
  const admin = await browser.newContext({ baseURL: 'http://127.0.0.1:15175' }), writer = await browser.newContext({ baseURL: 'http://127.0.0.1:15175' });
  await cookie(context, state.browserCookie);await cookie(admin, state.adminCookie);await cookie(writer, state.writerCookie);
  const dashboard = await admin.newPage();
  const active = async () => {
    const response = await admin.request.get(`${state.apiOrigin}/actuator/metrics/webox.inventory.active`);
    expect(response.status()).toBe(200);return Number((await response.json()).measurements[0].value);
  };
  async function sample(metric: Metric, index: number, action: () => Promise<number | void>) {
    const row = results[metric][index];
    try { row.activeStreams = await active();expect(row.activeStreams).toBeGreaterThanOrEqual(state.expectedActive);const start = performance.now();const measured = await action();row.elapsedMs = measured ?? performance.now() - start;row.failure = null; }
    catch (failure) { row.elapsedMs = null;row.failure = failure instanceof Error ? failure.name : 'UnknownError';throw failure; }
  }
  async function menu() {
    await page.goto('/menu');await page.getByLabel('Delivery date').fill(state.date);await expect(page.getByText('Live inventory', { exact: true })).toBeVisible();await page.getByLabel('Search dishes').fill(state.browserDishName);await expect(page.getByRole('article')).toHaveCount(1);await expect(page.getByRole('button', { name: `View ${state.browserDishName}`, exact: true })).toBeEnabled();
  }
  async function add() { await page.getByRole('button', { name: `View ${state.browserDishName}`, exact: true }).click();const dialog = page.getByRole('dialog');await dialog.getByRole('button', { name: 'Add to cart', exact: true }).click();await dialog.getByRole('button', { name: 'Close dish details' }).click(); }
  const employeeCsrf = (await (await context.request.get('/api/v1/auth/csrf')).json()).token;
  const writerCsrf = (await (await writer.request.get('/api/v1/auth/csrf')).json()).token;
  try {
    // One unmeasured complete menu/search warm-up, without mocked responses or clock manipulation.
    await menu();await page.getByLabel('Search dishes').fill('');await expect(page.getByRole('article')).toHaveCount(24);
    for (let i = 0; i < count; i++) {
      await page.getByRole('link', { name: 'Orders', exact: true }).click();await expect(page.getByRole('heading', { name: 'Your orders', exact: true })).toBeVisible();
      await sample('menu', i, async () => { const response = page.waitForResponse(r => r.url().includes(`/api/v1/menus/${state.date}?`) && new URL(r.url()).searchParams.get('search') === '');await page.getByRole('link', { name: 'Menu', exact: true }).click();expect((await response).status()).toBe(200);await expect(page.getByRole('article')).toHaveCount(24);await expect(page.getByRole('article').first().getByRole('button')).toBeEnabled(); });
    }
    for (let i = 0; i < count; i++) {
      const selected = i % 2 === 0;
      await sample('filter', i, async () => {
        const response = page.waitForResponse(r => r.url().includes(`/api/v1/menus/${state.date}?`) && new URL(r.url()).searchParams.getAll('categories').includes('Light Meal') === selected);
        await page.getByRole('checkbox', { name: 'Light Meal', exact: true }).setChecked(selected);const reply = await response;expect(reply.status()).toBe(200);const data = await reply.json();await expect(page.locator('.results-heading')).toContainText(`${data.totalElements} dishes`);await expect(page.getByRole('article')).toHaveCount(24);
      });
    }
    for (let i = 0; i < count; i++) { const name = `Load Dish ${i % 2 ? '492' : '491'}`;await sample('search', i, async () => { const response = page.waitForResponse(r => r.url().includes(`/api/v1/menus/${state.date}?`) && new URL(r.url()).searchParams.get('search') === name);await page.getByLabel('Search dishes').fill(name);expect((await response).status()).toBe(200);await expect(page.getByRole('article')).toHaveCount(1);await expect(page.getByRole('heading', { name, exact: true })).toBeVisible(); }); }
    await menu();await add();await page.getByRole('link', { name: 'View cart (1)' }).click();await expect(page.getByLabel('Quantity', { exact: true })).toHaveText('1');
    for (let i = 0; i < count; i++) {
      const increase = i % 2 === 0, target = increase ? '2' : '1';await expect(page.getByRole('button', { name: `${increase ? 'Increase' : 'Decrease'} ${state.browserDishName}` })).toBeEnabled();
      await sample('cart', i, () => page.evaluate(({ increase, name, target }) => new Promise<number>((resolve, reject) => {
        const button = document.querySelector<HTMLButtonElement>(`button[aria-label="${increase ? 'Increase' : 'Decrease'} ${name}"]`)!;
        const start = performance.now();const timeout = setTimeout(() => { observer.disconnect();reject(new Error('DOM timeout')); }, 3000);
        const observer = new MutationObserver(() => { if (document.querySelector('[aria-label="Quantity"]')?.textContent === target) { observer.disconnect();requestAnimationFrame(() => requestAnimationFrame(() => { clearTimeout(timeout);resolve(performance.now() - start); })); } });
        observer.observe(document.body, { childList: true, subtree: true, characterData: true });button.click();
      }), { increase, name: state.browserDishName, target }));
    }
    for (let i = 0; i < count; i++) {
      if (i > 0) { await menu();await add();await page.getByRole('link', { name: 'View cart (1)' }).click(); }
      await page.getByRole('link', { name: 'Continue to checkout' }).click();await page.getByLabel('Delivery address', { exact: true }).selectOption('');await page.getByLabel('New address').fill('Performance fixture office');
      await page.getByRole('button', { name: 'Review order' }).click();await expect(page.getByRole('button', { name: 'Place Order', exact: true })).toBeEnabled();
      await sample('order', i, async () => { await page.getByRole('button', { name: 'Place Order', exact: true }).click();await expect(page.getByRole('heading', { name: 'Order details', exact: true })).toBeVisible();await expect(page.locator('.order-status')).toHaveText('Pending'); });
      const id = page.url().split('/').pop();expect((await context.request.post(`/api/v1/orders/${id}/cancel`, { headers: { 'X-XSRF-TOKEN': employeeCsrf }, data: {} })).status()).toBe(200);
    }
    await menu();const badge = page.getByRole('article').locator('.stock-badge');await expect(badge).toHaveText('10000 remaining');
    for (let i = 0; i < count; i++) {
      const quote = await writer.request.post('/api/v1/checkout/quotes', { headers: { 'X-XSRF-TOKEN': writerCsrf }, data: { deliveryDate: state.date, meal: 'Lunch', items: [{ dishId: state.browserDish, dishVersion: 1, quantity: 1, optionIds: [] }] } });expect(quote.status()).toBe(201);const quoteId = (await quote.json()).quoteId;let orderId = '';
      await sample('inventory', i, async () => { const order = await writer.request.post('/api/v1/orders', { headers: { 'X-XSRF-TOKEN': writerCsrf, 'Idempotency-Key': crypto.randomUUID() }, data: { quoteId, addressText: 'Performance writer office' } });expect(order.status()).toBe(201);orderId = (await order.json()).orderId;await expect(badge).toHaveText('9999 remaining'); });
      expect((await writer.request.post(`/api/v1/orders/${orderId}/cancel`, { headers: { 'X-XSRF-TOKEN': writerCsrf }, data: {} })).status()).toBe(200);await expect(badge).toHaveText('10000 remaining');
    }
    await dashboard.goto('/console/dashboard');await dashboard.getByLabel('Report date').fill(state.date);await expect(dashboard.getByText(`Delivery date: ${state.date}`, { exact: true })).toBeVisible();
    for (let i = 0; i < count; i++) {
      await expect(dashboard.getByRole('button', { name: 'Refresh dashboard' })).toBeEnabled();const old = await dashboard.locator('.report-time time').getAttribute('datetime');
      await sample('dashboard', i, async () => { await dashboard.getByRole('button', { name: 'Refresh dashboard' }).click();await expect(dashboard.locator('.report-time time')).not.toHaveAttribute('datetime', old!);await expect(dashboard.getByRole('table', { name: 'Seven-day totals' }).getByRole('row')).toHaveCount(8);await expect(dashboard.getByRole('button', { name: 'Refresh dashboard' })).toBeEnabled(); });
    }
  } finally {
    const summaries = Object.fromEntries(Object.entries(results).map(([name, rows]) => { const sorted = rows.map(row => row.elapsedMs ?? Infinity).sort((a, b) => a - b);const p95 = sorted[Math.ceil(.95 * count) - 1];return [name, { attempts: count, failures: rows.filter(row => row.failure).length, p95Ms: Number.isFinite(p95) ? p95 : 'Infinity', limitMs: limits[name as Metric] }]; }));
    mkdirSync(state.output, { recursive: true });writeFileSync(join(state.output, 'user-visible.json'), JSON.stringify({ smokeOnly: state.smoke, browser: browser.version(), viewport: '1440x900', source: 'production Vite build via local preview proxy', sampleCountPerMetric: count, percentile: 'nearest-rank, missing/failed attempt = positive infinity', warmup: 'one menu/search; normal fixture preparation before each operation', results, summaries }, null, 2));await admin.close();await writer.close();
  }
  for (const [name, rows] of Object.entries(results)) {expect(rows.filter(row => row.failure), name).toHaveLength(0);const sorted = rows.map(row => row.elapsedMs ?? Infinity).sort((a, b) => a - b);expect(sorted[Math.ceil(.95 * count) - 1], name).toBeLessThanOrEqual(limits[name as Metric]);}
});
