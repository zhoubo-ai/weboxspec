import { expect, test } from '@playwright/test';
import { identitySession } from '../fixtures/session';
test('real isolated API and persisted sessions work through the same-origin proxy', async ({ page, context }) => {
  const health = await context.request.get('/api/v1/health');expect(health.status()).toBe(200);expect(await health.json()).toEqual({ status: 'UP' });
  const anonymous = await context.request.get('/api/v1/__fixture/identity');expect(anonymous.status()).toBe(401);
  const user = await identitySession(context);
  const identity = await context.request.get('/api/v1/__fixture/identity');expect(identity.status()).toBe(200);
  expect(await identity.json()).toMatchObject({ userId: user.userId, role: 'Employee' });
  await page.goto('/menu');await expect(page.getByRole('heading', { level: 1, name: 'Menu' })).toBeVisible();
  await page.reload();expect((await context.request.get('/api/v1/__fixture/identity')).status()).toBe(200);
  const csrf = await context.request.get('/api/v1/auth/csrf');expect(await csrf.json()).toMatchObject({ headerName: 'X-XSRF-TOKEN' });
  await identitySession(context, 'Admin');
  await page.goto('/console/dishes');await expect(page.getByRole('navigation', { name: 'Console navigation' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
