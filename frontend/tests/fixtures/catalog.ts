import type { BrowserContext } from '@playwright/test';
export type MenuFixture = { date: string; pasta: string; burger: string; salad: string; chinese: string; japanese: string; options: Record<string, string> };
export async function catalog(context: BrowserContext): Promise<MenuFixture> {
  const response = await context.request.post(`${process.env.WEBOX_E2E_API_ORIGIN ?? 'http://127.0.0.1:18081'}/__test/catalog`, { headers: { 'X-WeBox-Fixture-Key': process.env.WEBOX_E2E_FIXTURE_KEY! } });
  if (!response.ok()) throw new Error(`Catalog fixture failed: ${response.status()}`);return response.json();
}
export async function changeDish(context: BrowserContext, id: string, change: 'price' | 'unlist' | 'one-cent-over-budget') {
  const response = await context.request.post(`${process.env.WEBOX_E2E_API_ORIGIN ?? 'http://127.0.0.1:18081'}/__test/catalog/${id}`, { headers: { 'X-WeBox-Fixture-Key': process.env.WEBOX_E2E_FIXTURE_KEY! }, data: { change } });
  if (!response.ok()) throw new Error(`Catalog update failed: ${response.status()}`);
}
