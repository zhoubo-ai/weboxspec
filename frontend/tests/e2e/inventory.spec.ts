import { expect, test, type BrowserContext } from '@playwright/test';
import { writeFile } from 'node:fs/promises';
import { identitySession } from '../fixtures/session';
import { catalog, type MenuFixture } from '../fixtures/catalog';
async function write(context: BrowserContext, path: string, method: string, data?: unknown, key?: string) {
  const csrf = await (await context.request.get('/api/v1/auth/csrf')).json();return context.request.fetch(`/api/v1${path}`, { method, headers: { 'X-XSRF-TOKEN': csrf.token, ...(key ? { 'Idempotency-Key': key } : {}) }, data });
}
async function order(context: BrowserContext, fixture: MenuFixture, quantity = 1) {
  const dish = await (await context.request.get(`/api/v1/menus/${fixture.date}/dishes/${fixture.pasta}`)).json();const quote = await write(context, '/checkout/quotes', 'POST', { deliveryDate: fixture.date, meal: 'Lunch', items: [{ dishId: fixture.pasta, dishVersion: dish.dishVersion, quantity, optionIds: [fixture.options.Spaghetti] }] });expect(quote.status()).toBe(201);const result = await write(context, '/orders', 'POST', { quoteId: (await quote.json()).quoteId, addressText: 'Inventory test office' }, crypto.randomUUID());expect(result.status()).toBe(201);return result.json();
}
test('another buyer changes live absolute stock, cancellation restores it and sold-out blocks adding', async ({ page, context, browser, baseURL }, info) => {
  await identitySession(context);const fixture = await catalog(context);const buyer = await browser.newContext({ baseURL, viewport: page.viewportSize() });await identitySession(buyer);
  try {
    await page.goto('/menu');await page.getByLabel('Delivery date').fill(fixture.date);await expect(page.getByText('Live inventory', { exact: true })).toBeVisible();const card = page.getByRole('article').filter({ hasText: 'Tomato Pasta' });await expect(card).toContainText('20 remaining');
    const placed = await order(buyer, fixture);const start = performance.now(), commitObservedAt = new Date().toISOString();await expect(card).toContainText('19 remaining', { timeout: 1000 });const elapsedMs = performance.now() - start;const path = info.outputPath('inventory-latency.json');await writeFile(path, JSON.stringify({ project: info.project.name, commitObservedAt, domObservedAt: new Date().toISOString(), businessCreatedAt: placed.createdAt, elapsedMs, limitMs: 1000, observation: 'Order HTTP 201 body received to rendered remaining 19; business timestamp uses the fixed fixture clock.' }));await info.attach('inventory-latency', { path, contentType: 'application/json' });
    expect((await write(buyer, `/orders/${placed.orderId}/cancel`, 'POST')).status()).toBe(200);await expect(card).toContainText('20 remaining');
    await identitySession(buyer, 'Admin');const current = await (await buyer.request.get(`/api/v1/admin/menus/${fixture.date}`)).json();expect((await write(buyer, `/admin/menus/${fixture.date}`, 'PUT', { expectedRevision: current.revision, items: current.items.filter((s: { scheduled: boolean }) => s.scheduled).map((s: { dishId: string; totalQuantity: number }) => ({ dishId: s.dishId, totalQuantity: s.dishId === fixture.pasta ? 3 : s.totalQuantity })) })).status()).toBe(200);
    await expect(card).toContainText('Low Stock · 3 remaining');await page.getByRole('button', { name: 'View Tomato Pasta', exact: true }).click();const dialog = page.getByRole('dialog');await dialog.getByRole('radio', { name: /Spaghetti/ }).check();await expect(dialog.getByRole('button', { name: 'Add to cart' })).toBeEnabled();await order(buyer, fixture, 3);await expect(card).toContainText('Sold Out');await expect(dialog.getByRole('button', { name: 'Add to cart' })).toBeDisabled();
  } finally { await buyer.close(); }
});
test('offline recovery takes a fresh snapshot and unpublished membership cannot reappear', async ({ page, context, browser, baseURL }) => {
  await identitySession(context);const fixture = await catalog(context);const admin = await browser.newContext({ baseURL, viewport: page.viewportSize() });await identitySession(admin, 'Admin');
  try {
    await page.goto('/menu');await page.getByLabel('Delivery date').fill(fixture.date);await expect(page.getByText('Live inventory', { exact: true })).toBeVisible();const card = page.getByRole('article').filter({ hasText: 'Tomato Pasta' });await expect(card).toContainText('20 remaining');await context.setOffline(true);await expect(page.getByText('Inventory connection interrupted.', { exact: true })).toBeVisible();await order(admin, fixture);await context.setOffline(false);await page.evaluate(() => window.dispatchEvent(new Event('online')));await expect(page.getByText('Live inventory', { exact: true })).toBeVisible();await expect(card).toContainText('19 remaining');
    const dish = await (await admin.request.get(`/api/v1/admin/dishes/${fixture.pasta}`)).json();expect((await write(admin, `/admin/dishes/${fixture.pasta}/publication`, 'PATCH', { published: false, expectedVersion: dish.dishVersion })).status()).toBe(200);await expect(card).toHaveCount(0);expect((await context.request.get(`/api/v1/menus/${fixture.date}/dishes/${fixture.pasta}`)).status()).toBe(404);
    expect((await write(context, '/auth/logout', 'POST')).status()).toBe(204);expect((await write(admin, `/admin/dishes/${fixture.pasta}/publication`, 'PATCH', { published: true, expectedVersion: dish.dishVersion + 1 })).status()).toBe(200);await expect(page).toHaveURL(/\/login$/);await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible();
  } finally { await context.setOffline(false);await admin.close(); }
});
test('one unavailable line rejects the whole order and preserves both cart lines for correction', async ({ page, context, browser, baseURL }) => {
  await identitySession(context);const fixture=await catalog(context);const admin=await browser.newContext({ baseURL });await identitySession(admin, 'Admin');
  try {
    await page.goto('/menu');await page.getByLabel('Delivery date').fill(fixture.date);
    for (const name of ['Tomato Pasta', 'Chinese Garden Salad']) {
      await page.getByRole('button', { name: `View ${name}`, exact: true }).click();const dialog=page.getByRole('dialog');if (name === 'Tomato Pasta') await dialog.getByRole('radio', { name: /Spaghetti/ }).check();await dialog.getByRole('button', { name: 'Add to cart' }).click();await dialog.getByRole('button', { name: 'Close dish details' }).click();
    }
    await page.getByRole('link', { name: 'View cart (2)' }).click();await page.getByRole('link', { name: 'Continue to checkout' }).click();await page.getByLabel('New address').fill('Stock conflict office');await page.getByRole('button', { name: 'Review order' }).click();await expect(page.getByRole('button', { name: 'Place Order' })).toBeVisible();
    const current=await (await admin.request.get(`/api/v1/admin/menus/${fixture.date}`)).json();expect((await write(admin, `/admin/menus/${fixture.date}`, 'PUT', { expectedRevision: current.revision, items: current.items.filter((s: { scheduled: boolean }) => s.scheduled).map((s: { dishId: string; totalQuantity: number }) => ({ dishId: s.dishId, totalQuantity: s.dishId === fixture.pasta ? 0 : s.totalQuantity })) })).status()).toBe(200);
    await page.getByRole('button', { name: 'Place Order' }).click();await expect(page.getByText('Tomato Pasta · 0 portions available.', { exact: true })).toBeVisible();expect((await (await context.request.get('/api/v1/orders')).json()).totalElements).toBe(0);
    const after=await (await admin.request.get(`/api/v1/admin/menus/${fixture.date}`)).json();expect(after.items.every((s: { allocatedQuantity: number }) => s.allocatedQuantity === 0)).toBe(true);
    await page.getByRole('link', { name: 'Back to cart' }).click();await expect(page.getByRole('article')).toHaveCount(2);const unavailable=page.getByRole('article').filter({ hasText: 'Tomato Pasta' });await unavailable.getByRole('button', { name: 'Remove', exact: true }).click();await page.getByRole('link', { name: 'Continue to checkout' }).click();await page.getByLabel('New address').fill('Stock conflict office');await page.getByRole('button', { name: 'Review order' }).click();await page.getByRole('button', { name: 'Place Order' }).click();await expect(page.locator('.order-status')).toHaveText('Pending');
  } finally { await admin.close(); }
});
