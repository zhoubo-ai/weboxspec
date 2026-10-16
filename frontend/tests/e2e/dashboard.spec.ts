import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { readFile, writeFile } from 'node:fs/promises';
import { identitySession } from '../fixtures/session';
import type { Dashboard } from '../../src/shared/api/types';
type Fixture = { date: string; today: string; dishes: Record<string, string>; orders: Record<string, string> };
const source = JSON.parse(await readFile('../backend/src/test/resources/reporting/expected-metrics.json', 'utf8'));
function shift(date: string, offset: number) { const day = new Date(`${date}T12:00:00Z`);day.setUTCDate(day.getUTCDate() + offset);return day.toISOString().slice(0, 10); }
function expected(fixture: Fixture, which: 'D' | 'T') {
  const value = structuredClone(source[which]);value.date = which === 'D' ? fixture.date : fixture.today;
  value.topDishes = value.topDishes.map(({ dish, ...row }: { dish: string }) => ({ ...row, dishId: fixture.dishes[dish] }));
  value.inventoryAlerts = value.inventoryAlerts.map(({ dish, ...row }: { dish: string }) => ({ ...row, dishId: fixture.dishes[dish], name: `Current menu ${dish}` }));
  value.trend = value.trend.map(({ offset, ...row }: { offset: number }) => ({ ...row, date: shift(value.date, offset) }));return value as Omit<Dashboard, 'generatedAt' | 'countingPolicy'>;
}
async function fixture(context: BrowserContext): Promise<Fixture> {
  await identitySession(context, 'Admin');const key = process.env.WEBOX_E2E_FIXTURE_KEY!;
  const response = await context.request.post(`${process.env.WEBOX_E2E_API_ORIGIN ?? 'http://127.0.0.1:18081'}/__test/reporting`, { headers: { 'X-WeBox-Fixture-Key': key } });expect(response.status()).toBe(200);const value: Fixture = await response.json();await context.setExtraHTTPHeaders({ 'X-WeBox-Fixture-Key': key, 'X-WeBox-Fixture-Time': `${value.today}T01:00:00Z` });return value;
}
async function checkPage(page: Page, value: Omit<Dashboard, 'generatedAt' | 'countingPolicy'>) {
  await expect(page.getByText(`Delivery date: ${value.date}`, { exact: true })).toBeVisible();
  for (const [label, amount] of Object.entries({ 'Total orders': value.totalOrders, 'Completed Order Amount': `¥${(value.revenueCents / 100).toFixed(2)}`, 'Completed orders': value.completedOrders, 'Pending orders': value.pendingOrders, 'Cancelled orders': value.cancelledOrders })) await expect(page.locator('.dashboard-metrics > div').filter({ has: page.locator('dt').filter({ hasText: new RegExp(`^${label}$`) }) })).toContainText(String(amount));
  const top = page.getByRole('table', { name: 'Top dishes by completed portions' });for (const dish of value.topDishes) await expect(top.getByRole('row').filter({ hasText: dish.name })).toContainText(String(dish.quantity));
  const meals = page.getByRole('table', { name: 'Orders by meal' });for (const meal of value.mealDistribution) await expect(meals.getByRole('row').filter({ hasText: meal.meal })).toHaveText(`${meal.meal}${meal.orderCount}`);
  const trend = page.getByRole('table', { name: 'Seven-day totals' });await expect(trend.getByRole('row')).toHaveCount(8);for (const day of value.trend) await expect(trend.getByRole('row').filter({ hasText: day.date })).toHaveText(`${day.date}${day.orderCount}¥${(day.revenueCents / 100).toFixed(2)}`);
  const alerts = page.getByRole('table', { name: 'Low inventory for the selected delivery date' });await expect(alerts.getByRole('row')).toHaveCount(value.inventoryAlerts.length + 1);for (const dish of value.inventoryAlerts) await expect(alerts.getByRole('row').filter({ hasText: dish.name })).toHaveText(`${dish.name}${dish.remaining}${dish.level === 'SoldOut' ? 'Sold Out' : 'Low Stock'}`);
}
async function transition(context: BrowserContext, orderId: string, status: string) {
  const current = await (await context.request.get(`/api/v1/admin/orders/${orderId}`)).json();const csrf = await (await context.request.get('/api/v1/auth/csrf')).json();const response = await context.request.patch(`/api/v1/admin/orders/${orderId}/status`, { headers: { 'X-XSRF-TOKEN': csrf.token }, data: { status, expectedVersion: current.version } });expect(response.status()).toBe(200);
}
test('business today and a different selected delivery day match all handwritten totals, charts and alerts', async ({ page, context }, info) => {
  const data = await fixture(context);await page.goto('/console/dashboard');await checkPage(page, expected(data, 'T'));await expect(page.getByText(/Updated:/)).toContainText(data.today.slice(0, 4));await expect(page.getByText(/not payment received/)).toBeVisible();
  await page.getByLabel('Report date').fill(data.date);await checkPage(page, expected(data, 'D'));
  for (const which of ['D', 'T'] as const) {const response = await context.request.get(`/api/v1/admin/dashboard${which === 'D' ? `?date=${data.date}` : ''}`);expect(response.status()).toBe(200);const value = await response.json();expect(value.generatedAt).toBe(`${data.today}T01:00:00Z`);delete value.generatedAt;delete value.countingPolicy;expect(value).toEqual(expected(data, which));}
  await expect(page.locator('.dashboard-chart .recharts-wrapper')).toHaveCount(3);expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Refresh dashboard' }).focus();await page.keyboard.press('Enter');await expect(page.getByRole('button', { name: 'Refresh dashboard' })).toBeEnabled();
  if (info.project.name.startsWith('chromium')) await page.screenshot({ path: `../.local/screenshots/dashboard-${info.project.name}.png`, fullPage: true });
  await page.getByRole('button', { name: 'Use today' }).click();await checkPage(page, expected(data, 'T'));
});
test('refresh shows committed completion and cancellation within three seconds without repricing historical orders', async ({ page, context }, info) => {
  const data = await fixture(context);await page.goto('/console/dashboard');await page.getByLabel('Report date').fill(data.date);await checkPage(page, expected(data, 'D'));
  await transition(context, data.orders.o9, 'Cancelled');await transition(context, data.orders.o11, 'Completed');
  const start = performance.now();await page.getByRole('button', { name: 'Refresh dashboard' }).click();await expect(page.locator('.dashboard-metrics')).toContainText('¥113.00', { timeout: 3000 });await expect(page.locator('.dashboard-metrics > div').filter({ hasText: 'Pending orders' })).toContainText('0');await expect(page.locator('.dashboard-metrics > div').filter({ hasText: 'Cancelled orders' })).toContainText('2');const elapsedMs = performance.now() - start;
  await expect(page.getByRole('table', { name: 'Top dishes by completed portions' })).toContainText('Garden Greens');await expect(page.getByRole('table', { name: 'Top dishes by completed portions' })).toContainText('Latest Burger');const response = await (await context.request.get(`/api/v1/admin/dashboard?date=${data.date}`)).json();expect(response.revenueCents).toBe(11300);expect(response.completedOrders).toBe(4);expect(response.totalOrders).toBe(6);
  const path = info.outputPath('dashboard-refresh.json');await writeFile(path, JSON.stringify({ project: info.project.name, elapsedMs, limitMs: 3000, date: data.date, expectedRevenueCents: 11300, actual: response }, null, 2));await info.attach('dashboard-refresh', { path, contentType: 'application/json' });expect(elapsedMs).toBeLessThan(3000);
});
test('an empty date retains seven zero days and employees cannot access the dashboard', async ({ page, context }) => {
  await identitySession(context, 'Admin');await page.goto('/console/dashboard');await page.getByLabel('Report date').fill('1971-01-08');await expect(page.getByText('No completed dishes for this date.', { exact: true })).toBeVisible();await expect(page.getByText('No low-stock alerts for this date.', { exact: true })).toBeVisible();await expect(page.getByRole('table', { name: 'Seven-day totals' }).getByRole('row')).toHaveCount(8);await expect(page.locator('.dashboard-metrics')).toContainText('¥0.00');await identitySession(context, 'Employee');await page.reload();await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();expect((await context.request.get('/api/v1/admin/dashboard')).status()).toBe(403);
});
