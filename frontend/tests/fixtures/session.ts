import type { BrowserContext } from '@playwright/test';
import type { Role, User } from '../../src/shared/api/types';
export async function identitySession(context: BrowserContext, role: Role = 'Employee'): Promise<User> {
  const origin = process.env.WEBOX_E2E_API_ORIGIN ?? 'http://127.0.0.1:18081';
  const key = process.env.WEBOX_E2E_FIXTURE_KEY;
  if (!key) throw new Error('E2E fixture key is missing. Start the isolated test server through Playwright.');
  const response = await context.request.post(`${origin}/__test/sessions`, { headers: { 'X-WeBox-Fixture-Key': key }, data: { role } });
  if (!response.ok()) throw new Error(`Cannot create isolated test identity (${response.status()}).`);
  const result = await response.json() as { user: User; sessionId: string };
  await context.addCookies([{ name: 'WEBOXSESSION', value: result.sessionId, domain: '127.0.0.1', path: '/', httpOnly: true, secure: false, sameSite: 'Lax' }]);
  return result.user;
}
