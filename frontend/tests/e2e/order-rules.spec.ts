import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { identitySession } from '../fixtures/session';
import { catalog, type MenuFixture } from '../fixtures/catalog';
async function serverTime(context: BrowserContext, date: string, time: string) {
  await context.setExtraHTTPHeaders({ 'X-WeBox-Fixture-Key': process.env.WEBOX_E2E_FIXTURE_KEY!, 'X-WeBox-Fixture-Time': new Date(`${date}T${time}+08:00`).toISOString() });
}
async function addPasta(page: Page, fixture: MenuFixture, count = 1) {
  await page.goto('/menu');await page.getByLabel('Delivery date').fill(fixture.date);await page.getByRole('button', { name: 'View Tomato Pasta', exact: true }).click();
  const dialog = page.getByRole('dialog');await dialog.getByRole('radio', { name: /Spaghetti/ }).check();for (let i = 0; i < count; i++) await dialog.getByRole('button', { name: 'Add to cart' }).click();
  await dialog.getByRole('button', { name: 'Close dish details' }).click();await page.getByRole('link', { name: `View cart (${count})` }).click();await page.getByRole('link', { name: 'Continue to checkout' }).click();
}
test('server time switches the closed slot, requires confirmation and keeps an empty next date', async ({ page, context }) => {
  await identitySession(context);const fixture = await catalog(context);await serverTime(context, fixture.date, '09:59:58');await page.clock.install();await addPasta(page, fixture);
  await page.getByLabel('New address').fill('Office');await expect(page.getByLabel('Meal', { exact: true })).toHaveValue('Lunch');await expect(page.getByRole('button', { name: 'Review order' })).toBeEnabled();
  await serverTime(context, fixture.date, '10:00:00');await page.clock.fastForward(3000);
  await expect(page.getByLabel('Meal', { exact: true })).toHaveValue('Dinner');await expect(page.getByText('Lunch has closed.', { exact: false })).toBeVisible();await expect(page.getByRole('button', { name: 'Review order' })).toBeDisabled();
  await page.getByRole('button', { name: 'Confirm delivery selection' }).click();await expect(page.getByText('Lunch has closed.', { exact: false })).toHaveCount(0);await page.getByRole('button', { name: 'Review order' }).click();await expect(page.getByRole('button', { name: 'Place Order' })).toBeVisible();
  await serverTime(context, fixture.date, '15:00:00');await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  const tomorrow = new Date(Date.parse(`${fixture.date}T00:00:00Z`) + 86400000).toISOString().slice(0, 10);
  await expect(page.getByLabel('Delivery date')).toHaveValue(tomorrow);await expect(page.getByLabel('Meal', { exact: true })).toHaveValue('Lunch');await page.getByRole('button', { name: 'Confirm delivery selection' }).click();
  await expect(page.getByRole('button', { name: 'Review order' })).toBeDisabled();await expect(page.getByText('Review changed or unavailable items', { exact: false })).toBeVisible();await expect(page.getByLabel('Delivery date')).toHaveValue(tomorrow);
  await page.getByRole('link', { name: 'Back to cart' }).click();await expect(page.getByRole('article')).toContainText('Tomato Pasta');await expect(page.getByRole('alert')).toContainText('unavailable');
  expect((await (await context.request.get('/api/v1/orders')).json()).totalElements).toBe(0);
});
test('existing orders replace ordering, future Dinner remains selected and cancellation permits a new intent', async ({ page, context }) => {
  await identitySession(context);const fixture = await catalog(context);await addPasta(page, fixture);
  await page.getByLabel('Meal', { exact: true }).selectOption('Dinner');await page.getByLabel('New address').fill('Office');await page.getByRole('button', { name: 'Review order' }).click();await page.getByRole('button', { name: 'Place Order' }).click();await expect(page).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);const firstId = page.url().split('/').pop()!;
  await addPasta(page, fixture);await page.getByLabel('Meal', { exact: true }).selectOption('Dinner');await expect(page.getByRole('link', { name: 'View Existing Order', exact: true })).toHaveAttribute('href', `/orders/${firstId}`);await expect(page.getByRole('button', { name: 'Place Order' })).toHaveCount(0);await expect(page.getByLabel('Delivery date')).toHaveValue(fixture.date);await expect(page.getByLabel('Meal', { exact: true })).toHaveValue('Dinner');
  await page.getByRole('link', { name: 'View Existing Order' }).click();await page.getByRole('button', { name: 'Cancel order' }).click();await expect(page.locator('.order-status')).toHaveText('Cancelled');
  await page.getByRole('link', { name: 'Cart', exact: true }).click();await page.getByRole('link', { name: 'Continue to checkout' }).click();await page.getByLabel('Meal', { exact: true }).selectOption('Dinner');await page.getByLabel('New address').fill('Office');await page.getByRole('button', { name: 'Review order' }).click();await page.getByRole('button', { name: 'Place Order' }).click();await expect(page).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);expect(page.url().split('/').pop()).not.toBe(firstId);await expect(page.locator('.order-status')).toHaveText('Pending');
});
test('a cutoff at the final submission changes the slot and requires a fresh confirmed quote', async ({ page, context }) => {
  await identitySession(context);const fixture = await catalog(context);await serverTime(context, fixture.date, '09:59:58');await page.clock.install();await addPasta(page, fixture);
  await page.getByLabel('New address').fill('Office');await page.getByRole('button', { name: 'Review order' }).click();await expect(page.getByRole('button', { name: 'Place Order' })).toBeVisible();
  await serverTime(context, fixture.date, '10:30:00');await page.getByRole('button', { name: 'Place Order' }).click();
  await expect(page.getByLabel('Meal', { exact: true })).toHaveValue('Dinner');await expect(page.getByRole('button', { name: 'Review order' })).toBeDisabled();expect((await (await context.request.get('/api/v1/orders')).json()).totalElements).toBe(0);
  await page.getByRole('button', { name: 'Confirm delivery selection' }).click();await page.getByRole('button', { name: 'Review order' }).click();await page.getByRole('button', { name: 'Place Order' }).click();await expect(page).toHaveURL(/\/orders\/[0-9a-f-]{36}$/);await expect(page.locator('main')).toContainText('Dinner');
});
test('all add controls stop at five and a direct six-portion request is rejected', async ({ page, context }) => {
  await identitySession(context);const fixture = await catalog(context);await addPasta(page, fixture, 5);await page.getByRole('link', { name: 'Back to cart' }).click();await expect(page.getByRole('button', { name: 'Increase Tomato Pasta' })).toBeDisabled();await page.getByRole('link', { name: 'Keep browsing' }).click();await page.getByRole('button', { name: 'View Tomato Pasta', exact: true }).click();await expect(page.getByRole('dialog').getByRole('button', { name: 'Add to cart' })).toBeDisabled();await expect(page.getByRole('dialog')).toContainText('up to 5');await page.keyboard.press('Escape');
  const csrf = await (await context.request.get('/api/v1/auth/csrf')).json();const response = await context.request.post('/api/v1/checkout/quotes', { headers: { 'X-XSRF-TOKEN': csrf.token }, data: { deliveryDate: fixture.date, meal: 'Lunch', items: [fixture.options.Spaghetti, fixture.options.Fusilli].map(option => ({ dishId: fixture.pasta, dishVersion: 1, quantity: 3, optionIds: [option] })) } });expect(response.status()).toBe(422);expect((await response.json()).code).toBe('ORDER_LIMIT_EXCEEDED');
  await page.getByRole('link', { name: 'View cart (5)' }).click();await page.getByRole('button', { name: 'Decrease Tomato Pasta' }).click();await expect(page.getByRole('button', { name: 'Increase Tomato Pasta' })).toBeEnabled();
});
