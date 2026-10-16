import { expect, test } from '@playwright/test';
import { identitySession } from '../fixtures/session';
test('register, explicitly sign in, refresh and sign out with the real database', async ({ page, context }) => {
  const email = `employee-${crypto.randomUUID()}@example.org`;
  await page.goto('/register');await page.getByLabel('Email').fill(email);await page.getByLabel('Password').fill('Example123');
  await page.getByRole('button', { name: 'Create account' }).click();await expect(page.getByText('Account created. Sign in to continue.')).toBeVisible();
  expect((await context.request.get('/api/v1/auth/me')).status()).toBe(401);
  await page.getByLabel('Email').fill(email);await page.getByLabel('Password').fill('Incorrect123');await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('Email or password is incorrect.')).toBeVisible();await expect(page.getByLabel('Email')).toHaveValue(email);
  await page.getByLabel('Password').fill('Example123');await page.getByRole('button', { name: 'Sign in' }).click();await expect(page).toHaveURL(/\/menu$/);
  await page.reload();await expect(page.getByRole('heading', { name: 'Menu', exact: true })).toBeVisible();
  await page.goto('/console/dishes');await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
  expect((await context.request.get('/api/v1/admin/dishes')).status()).toBe(403);
  await page.goto('/menu');await page.getByRole('button', { name: 'Sign out' }).click();await expect(page).toHaveURL(/\/login$/);
  expect((await context.request.get('/api/v1/auth/me')).status()).toBe(401);
  await page.goto('/orders');await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => JSON.stringify(localStorage))).not.toContain('Example123');
  await page.goto('/register');await page.getByLabel('Email').fill(email.toUpperCase());await page.getByLabel('Password').fill('Example123');
  await page.getByRole('button', { name: 'Create account' }).click();await expect(page.getByText('This email is already registered. Please sign in.')).toBeVisible();
});
test('admin defaults to the console and invalid input stays editable', async ({ page, context }) => {
  await page.goto('/register');await page.getByLabel('Email').fill(`invalid-${crypto.randomUUID()}@example.com`);await page.getByLabel('Password').fill('A123456');
  await page.getByRole('button', { name: 'Create account' }).click();await expect(page.getByRole('alert')).toContainText('8 to 128');await expect(page.getByLabel('Email')).not.toHaveValue('');
  await identitySession(context, 'Admin');await page.goto('/login');await expect(page).toHaveURL(/\/console\/dishes$/);
  await expect(page.getByRole('navigation', { name: 'Console navigation' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
