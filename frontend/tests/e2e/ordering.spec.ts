import { expect, test, type Page } from '@playwright/test';
import { identitySession } from '../fixtures/session';
import { catalog, changeDish, type MenuFixture } from '../fixtures/catalog';
async function checkout(page: Page, fixture: MenuFixture) {
  await page.goto('/menu');await page.getByLabel('Delivery date').fill(fixture.date);await page.getByRole('button', { name: 'View Tomato Pasta', exact: true }).click();
  const dialog = page.getByRole('dialog');await dialog.getByRole('radio', { name: /Spaghetti/ }).check();await dialog.getByRole('checkbox', { name: /Cheese/ }).check();
  await dialog.getByRole('button', { name: 'Add to cart' }).click();await dialog.getByRole('button', { name: 'Add to cart' }).click();await dialog.getByRole('button', { name: 'Close dish details' }).click();
  await page.getByRole('link', { name: 'View cart (2)' }).click();await page.getByRole('link', { name: 'Continue to checkout' }).click();
}
test('real quote, exact order snapshots, Unicode address and cancellation form a complete flow', async ({ page, context }, testInfo) => {
  await identitySession(context);const fixture = await catalog(context);await checkout(page, fixture);
  await page.getByRole('button', { name: 'Review order' }).click();await expect(page.locator('.cart-total')).toContainText('¥59.00');await page.getByRole('button', { name: 'Place Order' }).click();await expect(page.getByRole('alert')).toContainText('1 to 200');
  await page.getByLabel('New address').fill('🥗'.repeat(201));await page.getByRole('button', { name: 'Place Order' }).click();await expect(page.getByRole('alert')).toContainText('1 to 200');
  const address = '上海市 A 楼 3 层 <img src=x onerror=alert(1)>';await page.getByLabel('New address').fill(`  ${address}  `);
  let dialogOpened = false;page.on('dialog', async event => { dialogOpened = true;await event.dismiss(); });
  await page.getByRole('button', { name: 'Place Order' }).click();await expect(page).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);await expect(page.getByRole('heading', { name: 'Order details' })).toBeVisible();
  await expect(page.locator('.order-status')).toHaveText('Pending');await expect(page.getByText(address, { exact: true })).toBeVisible();await expect(page.locator('.cart-total')).toContainText('¥59.00');await expect(page.locator('.order-number')).toHaveText(/^WB-[0-9A-F-]{36}$/);expect(dialogOpened).toBe(false);
  if (testInfo.project.name === 'chromium-1440') await page.screenshot({ path: '../.local/screenshots/order-chromium-1440.png', fullPage: true });
  const id = page.url().split('/').pop()!;
  const addresses = await (await context.request.get('/api/v1/me/addresses')).json();expect(addresses).toHaveLength(1);expect(addresses[0].text).toBe(address);
  await changeDish(context, fixture.pasta, 'price');await page.reload();await expect(page.locator('.cart-total')).toContainText('¥59.00');await expect(page.locator('.ordered-item')).toContainText('¥29.50 each');
  await page.getByRole('button', { name: 'Cancel order' }).click();await expect(page.locator('.order-status')).toHaveText('Cancelled');await expect(page.getByRole('button', { name: 'Cancel order', exact: true })).toHaveCount(0);
  const inventory = await (await context.request.get(`/api/v1/menus/${fixture.date}/dishes/${fixture.pasta}`)).json();expect(inventory.remaining).toBe(20);expect(inventory.inventoryVersion).toBe(2);
  await page.getByRole('link', { name: 'All orders' }).click();await page.getByLabel('Order status').selectOption('Cancelled');await expect(page.getByRole('article')).toHaveCount(1);await page.getByRole('link', { name: 'Cart', exact: true }).click();await expect(page.getByText('Your cart is empty.', { exact: false })).toBeVisible();
  await checkout(page, fixture);await page.getByLabel('Meal', { exact: true }).selectOption('Dinner');await page.getByLabel('Delivery address', { exact: true }).selectOption(addresses[0].addressId);
  await expect(page.getByLabel('New address')).toHaveCount(0);await page.getByRole('button', { name: 'Review order' }).click();await expect(page.locator('.summary-line')).toContainText('Cheese (+¥3.00)');await expect(page.locator('.cart-total')).toContainText('¥66.00');
  await page.getByRole('button', { name: 'Place Order' }).click();await expect(page).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);await expect(page.getByText(address, { exact: true })).toBeVisible();await expect(page.getByRole('heading', { level: 2, name: `${fixture.date} · Dinner` })).toBeVisible();expect(await (await context.request.get('/api/v1/me/addresses')).json()).toHaveLength(1);
  await identitySession(context);await page.goto(`/orders/${id}`);await expect(page.getByRole('alert')).toContainText('may not be available to your account');await page.goto('/orders');await expect(page.getByRole('heading', { name: 'No orders found' })).toBeVisible();
});
test('lost create response retries the frozen key and preserves additions made before resolving it', async ({ page, context }) => {
  await identitySession(context);const fixture = await catalog(context);await checkout(page, fixture);await page.getByLabel('New address').fill('Office B');await page.getByRole('button', { name: 'Review order' }).click();
  const requests: { key: string | null; body: string | null }[] = [];
  await page.route('**/api/v1/orders', async route => {
    if (route.request().method() !== 'POST') return route.continue();
    requests.push({ key: await route.request().headerValue('idempotency-key'), body: route.request().postData() });
    if (requests.length === 1) { const persisted = await route.fetch();expect(persisted.status()).toBe(201);await route.abort('failed'); }
    else { const replay = await route.fetch();expect(replay.status()).toBe(200);expect(replay.headers()['idempotency-replayed']).toBe('true');await route.fulfill({ response: replay }); }
  });
  await page.getByRole('button', { name: 'Place Order' }).click();await expect(page.getByRole('button', { name: 'Retry same order request' })).toBeVisible();await expect(page.getByLabel('New address')).toBeDisabled();
  await page.route('**/api/v1/auth/me', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'TEMPORARY_UNAVAILABLE', message: 'Temporary test outage.', requestId: crypto.randomUUID(), retryable: true }) }));
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));await expect(page.getByRole('alert')).toContainText('Could not check your session');
  await page.unroute('**/api/v1/auth/me');await page.getByRole('button', { name: 'Try again' }).click();await expect(page.getByRole('button', { name: 'Retry same order request' })).toBeVisible();
  await page.getByRole('link', { name: 'Back to cart' }).click();await page.getByRole('button', { name: 'Increase Tomato Pasta' }).click();await expect(page.getByLabel('Quantity')).toHaveText('3');await page.getByRole('link', { name: 'Continue to checkout' }).click();
  await page.getByRole('button', { name: 'Retry same order request' }).click();await expect(page).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);await expect(page.locator('.cart-total')).toContainText('¥59.00');expect(requests).toHaveLength(2);expect(requests[0]).toEqual(requests[1]);
  const orders = await (await context.request.get('/api/v1/orders')).json();expect(orders.totalElements).toBe(1);
  const inventory = await (await context.request.get(`/api/v1/menus/${fixture.date}/dishes/${fixture.pasta}`)).json();expect(inventory.remaining).toBe(18);expect(inventory.inventoryVersion).toBe(1);
  await page.getByRole('link', { name: 'Cart', exact: true }).click();await expect(page.getByLabel('Quantity')).toHaveText('1');await expect(page.locator('.cart-total')).toContainText('¥29.50');
});
test('expired quotes, changed prices, empty carts and past dates remain recoverable', async ({ page, context }) => {
  await identitySession(context);const fixture = await catalog(context);await page.goto('/checkout');await expect(page.getByRole('button', { name: 'Review order' })).toBeDisabled();
  await checkout(page, fixture);await page.clock.install();await page.getByLabel('New address').fill('Office');await page.getByRole('button', { name: 'Review order' }).click();await expect(page.getByRole('button', { name: 'Place Order' })).toBeVisible();
  await page.clock.fastForward(301_000);await expect(page.getByText('This quote has expired. Review your order again.')).toBeVisible();await page.getByRole('button', { name: 'Review order' }).click();await expect(page.getByRole('button', { name: 'Place Order' })).toBeVisible();
  await changeDish(context, fixture.pasta, 'price');await page.getByRole('button', { name: 'Place Order' }).click();await expect(page.getByRole('alert')).toContainText('This dish has changed');
  expect((await (await context.request.get('/api/v1/orders')).json()).totalElements).toBe(0);expect(await (await context.request.get('/api/v1/me/addresses')).json()).toEqual([]);
  await page.getByRole('link', { name: 'Back to cart' }).click();await expect(page.getByText('This dish has changed.', { exact: false })).toBeVisible();await page.getByRole('button', { name: 'Confirm updated item' }).click();await page.getByRole('link', { name: 'Continue to checkout' }).click();
  await page.getByLabel('Delivery date').fill('2000-01-01');await expect(page.getByRole('alert')).toContainText('Choose today or a future delivery date');await expect(page.getByRole('button', { name: 'Review order' })).toBeDisabled();
});
