import { expect, test, type Page } from '@playwright/test';
import { identitySession } from '../fixtures/session';
import { catalog, changeDish, type MenuFixture } from '../fixtures/catalog';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
async function openMenu(page: Page, fixture: MenuFixture) {
  await page.goto('/menu');await page.getByLabel('Delivery date').fill(fixture.date);
  await expect(page.getByRole('heading', { name: 'Tomato Pasta', exact: true })).toBeVisible();
}
async function pasta(page: Page, times = 1) {
  await page.getByRole('button', { name: 'View Tomato Pasta', exact: true }).click();
  const dialog = page.getByRole('dialog');await expect(dialog.getByRole('button', { name: 'Add to cart' })).toBeDisabled();
  await dialog.getByRole('radio', { name: /Spaghetti/ }).check();await dialog.getByRole('checkbox', { name: /Cheese/ }).check();
  await expect(dialog.getByText('Unit price: ¥29.50', { exact: true })).toBeVisible();
  for (let i = 0; i < times; i++) await dialog.getByRole('button', { name: 'Add to cart' }).click();
  await dialog.getByRole('button', { name: 'Close dish details' }).click();
}
test('real daily menu, images, union filters and required burger configuration', async ({ page, context }, testInfo) => {
  await identitySession(context);const fixture = await catalog(context);await openMenu(page, fixture);
  const burger = page.getByRole('article').filter({ has: page.getByRole('heading', { name: 'Classic Beef Burger', exact: true }) });
  await expect(burger).toContainText('¥32.00');await burger.locator('img').scrollIntoViewIfNeeded();await expect.poll(() => burger.locator('img').evaluate((image: HTMLImageElement) => image.complete && image.naturalWidth > 0)).toBe(true);expect(await burger.locator('img').evaluate(async (image: HTMLImageElement) => { await image.decode();return image.naturalWidth > 0; })).toBe(true);
  if (testInfo.project.name.startsWith('chromium')) await page.screenshot({ path: `../.local/screenshots/menu-${testInfo.project.name}.png`, fullPage: true });
  await page.getByRole('button', { name: 'View Classic Beef Burger', exact: true }).click();const dialog = page.getByRole('dialog');
  await expect(dialog).toContainText('Protein');await expect(dialog).toContainText('Beef');await expect(dialog).toContainText('Gluten');await expect(dialog).toContainText('Spice');
  await expect(dialog.getByRole('button', { name: 'Add to cart' })).toBeDisabled();await dialog.getByRole('radio', { name: /Plain bun/ }).check();await expect(dialog.getByRole('button', { name: 'Add to cart' })).toBeDisabled();
  await dialog.getByRole('radio', { name: /Ketchup/ }).check();await dialog.getByRole('checkbox', { name: /Extra cheese/ }).check();await expect(dialog).toContainText('Unit price: ¥35.00');
  await dialog.getByRole('button', { name: 'Add to cart' }).click();await dialog.getByRole('button', { name: 'Add to cart' }).click();
  await dialog.getByRole('radio', { name: /Brioche bun/ }).check();await dialog.getByRole('button', { name: 'Add to cart' }).click();await dialog.getByRole('button', { name: 'Close dish details' }).click();
  await expect(page.getByRole('button', { name: 'View Classic Beef Burger', exact: true })).toBeFocused();
  await page.getByRole('link', { name: 'View cart (3)' }).click();await expect(page.getByRole('article')).toHaveCount(2);await expect(page.getByLabel('Quantity').first()).toHaveText('2');await expect(page.locator('.cart-total')).toContainText('¥107.00');
  await page.getByRole('link', { name: 'Keep browsing' }).click();await page.getByRole('checkbox', { name: 'Chinese', exact: true }).check();await page.getByRole('checkbox', { name: 'Japanese', exact: true }).check();await page.getByRole('searchbox', { name: 'Search dishes' }).fill('greens');
  await expect(page.getByRole('article')).toHaveCount(2);await expect(page.getByRole('article').first()).toContainText('Chinese');await expect(page.getByRole('article').last()).toContainText('Japanese');
  await page.getByRole('searchbox', { name: 'Search dishes' }).fill('nothing matches');await expect(page.getByRole('heading', { name: 'No dishes found' })).toBeVisible();await page.getByRole('button', { name: 'Clear filters' }).click();await expect(page.getByRole('article')).toHaveCount(5);
  await page.getByRole('button', { name: 'View Garden Salad', exact: true }).click();await expect(page.getByRole('dialog')).toContainText('Sold Out');await expect(page.getByRole('dialog').getByRole('button', { name: 'Add to cart' })).toBeDisabled();await page.keyboard.press('Escape');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
test('draft survives sign out and a real browser restart in the same local profile', async ({ playwright, browserName, baseURL }, testInfo) => {
  test.setTimeout(60_000);
  const profile = await mkdtemp(join(tmpdir(), 'webox-cart-browser-'));
  const options = { baseURL, viewport: testInfo.project.use.viewport, ...(browserName === 'firefox' ? { firefoxUserPrefs: { 'network.proxy.type': 0 } } : {}) };
  let browserContext = await playwright[browserName].launchPersistentContext(profile, options);
  const email = `draft-${crypto.randomUUID()}@example.com`, password = 'DraftExample123';
  try {
    const fixture = await catalog(browserContext);let tab = await browserContext.newPage();
    await tab.goto('/register');await tab.getByLabel('Email').fill(email);await tab.getByLabel('Password').fill(password);await tab.getByRole('button', { name: 'Create account' }).click();
    await expect(tab.getByText('Account created. Sign in to continue.')).toBeVisible();await tab.getByLabel('Email').fill(email);await tab.getByLabel('Password').fill(password);await tab.getByRole('button', { name: 'Sign in' }).click();
    await expect(tab).toHaveURL(/\/menu$/);await tab.getByLabel('Delivery date').fill(fixture.date);await expect(tab.getByRole('heading', { name: 'Tomato Pasta', exact: true })).toBeVisible();await pasta(tab, 2);
    await tab.getByRole('button', { name: 'Sign out' }).click();await expect(tab).toHaveURL(/\/login$/);
    await browserContext.close();browserContext = await playwright[browserName].launchPersistentContext(profile, options);tab = await browserContext.newPage();
    await tab.goto('/login');await tab.getByLabel('Email').fill(email);await tab.getByLabel('Password').fill(password);await tab.getByRole('button', { name: 'Sign in' }).click();await expect(tab).toHaveURL(/\/menu$/);
    await tab.getByLabel('Delivery date').fill(fixture.date);await expect(tab.getByRole('link', { name: 'View cart (2)' })).toBeVisible();await tab.getByRole('link', { name: 'View cart (2)' }).click();await expect(tab.locator('.cart-total')).toContainText('¥59.00');await expect(tab.getByRole('article')).toContainText('Spaghetti');
  } finally { await browserContext.close();await rm(profile, { recursive: true, force: true }); }
});
test('pasta is exactly 59.00, rapid increases stop at five and reductions enable adding again', async ({ page, context }) => {
  await identitySession(context);const fixture = await catalog(context);await openMenu(page, fixture);await pasta(page, 2);
  await page.getByRole('link', { name: 'View cart (2)' }).click();await expect(page.getByRole('article')).toHaveCount(1);await expect(page.getByRole('article')).toContainText('Subtotal: ¥59.00');await expect(page.locator('.cart-total')).toContainText('¥59.00');
  for (let i = 0; i < 3; i++) await page.getByRole('button', { name: 'Increase Tomato Pasta' }).click();await expect(page.getByLabel('Quantity')).toHaveText('5');await expect(page.getByRole('button', { name: 'Increase Tomato Pasta' })).toBeDisabled();
  await page.getByRole('button', { name: 'Decrease Tomato Pasta' }).click();await expect(page.getByRole('button', { name: 'Increase Tomato Pasta' })).toBeEnabled();await page.getByRole('button', { name: 'Increase Tomato Pasta' }).click();await expect(page.locator('.cart-total')).toContainText('¥147.50');
  await page.getByRole('button', { name: 'Remove', exact: true }).click();await expect(page.getByText('Your cart is empty.', { exact: false })).toBeVisible();
});
test('account drafts survive reload and reopening, are isolated, and require price confirmation', async ({ page, context }) => {
  const user = await identitySession(context);const fixture = await catalog(context);await openMenu(page, fixture);await pasta(page, 2);
  await page.reload();await page.getByLabel('Delivery date').fill(fixture.date);await expect(page.getByRole('link', { name: 'View cart (2)' })).toBeVisible();
  const storage = await page.evaluate(id => localStorage.getItem(`webox.cart.v1:${id}`), user.userId);expect(storage).toBeTruthy();expect(storage).not.toMatch(/password|token|address|quote|preferenceMatch/);
  const reopened = await context.newPage();await openMenu(reopened, fixture);await reopened.getByRole('link', { name: 'View cart (2)' }).click();await expect(reopened.locator('.cart-total')).toContainText('¥59.00');await reopened.close();
  const cookie = (await context.cookies()).find(value => value.name === 'WEBOXSESSION')!;
  await identitySession(context);await page.goto('/cart');await expect(page.getByText('Your cart is empty.', { exact: false })).toBeVisible();
  await context.addCookies([cookie]);await openMenu(page, fixture);await page.getByRole('link', { name: 'View cart (2)' }).click();await expect(page.locator('.cart-total')).toContainText('¥59.00');
  await changeDish(context, fixture.pasta, 'price');await page.getByRole('button', { name: 'Check current availability' }).click();await expect(page.getByText('This dish has changed.', { exact: false })).toBeVisible();await expect(page.getByRole('button', { name: 'Continue to checkout' })).toBeDisabled();
  await page.getByRole('button', { name: 'Confirm updated item' }).click();await expect(page.locator('.cart-total')).toContainText('¥66.00');await expect(page.getByRole('link', { name: 'Continue to checkout' })).toBeVisible();
  await changeDish(context, fixture.pasta, 'unlist');await page.getByRole('button', { name: 'Check current availability' }).click();await expect(page.getByRole('alert')).toContainText('unavailable');await expect(page.getByRole('button', { name: 'Continue to checkout' })).toBeDisabled();
});
