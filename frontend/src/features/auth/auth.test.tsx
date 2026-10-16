import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router';
import { SessionProvider, onSessionEnd } from './session';
import { AppRoutes } from '../../app/router';
const employee = { userId: 'd65a8dfd-51f2-4009-88b0-ddd16a2c14de', email: 'employee@example.com', role: 'Employee' };
const response = (data: unknown, status = 200) => new Response(status === 204 ? null : JSON.stringify(data), { status });
let signedIn = false;
let requests: string[] = [];
beforeEach(() => {
  signedIn = false; requests = []; localStorage.clear();
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    requests.push(url);
    if (url.endsWith('/csrf')) return response({ token: 'csrf', headerName: 'X-XSRF-TOKEN' });
    if (url.endsWith('/me')) return signedIn ? response(employee) : response({ code: 'UNAUTHENTICATED', message: 'Please sign in.', retryable: false }, 401);
    if (url.endsWith('/register')) return response(employee, 201);
    if (url.endsWith('/login')) {
      if (JSON.parse(init?.body as string).password !== 'Example123') return response({ code: 'INVALID_CREDENTIALS', message: 'Email or password is incorrect.', retryable: false }, 401);
      signedIn = true; return response(employee);
    }
    if (url.endsWith('/logout')) { signedIn = false; return response(null, 204); }
    throw new Error('Unexpected endpoint');
  }));
});
afterEach(() => vi.unstubAllGlobals());
it('registration leads to explicit sign in without storing the password', async () => {
  history.replaceState(null, '', '/register');render(<App />);const user = userEvent.setup();
  await user.type(await screen.findByLabelText('Email'), 'employee@example.com');
  await user.type(screen.getByLabelText('Password'), 'Example123');
  await user.click(screen.getByRole('button', { name: 'Create account' }));
  expect(await screen.findByText('Account created. Sign in to continue.')).toBeVisible();
  expect(requests.some(url => url.endsWith('/login'))).toBe(false);
  expect(JSON.stringify(localStorage)).not.toContain('Example123');
});
it('failed login retains email and successful login routes to the employee menu', async () => {
  history.replaceState(null, '', '/login');render(<App />);const user = userEvent.setup();
  await user.type(await screen.findByLabelText('Email'), 'employee@example.com');await user.type(screen.getByLabelText('Password'), 'Wrong123');
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
  expect(await screen.findByText('Email or password is incorrect.')).toBeVisible();expect(screen.getByLabelText('Email')).toHaveValue('employee@example.com');
  await user.clear(screen.getByLabelText('Password'));await user.type(screen.getByLabelText('Password'), 'Example123');await user.click(screen.getByRole('button', { name: 'Sign in' }));
  await waitFor(() => expect(location.pathname).toBe('/menu'));expect(await screen.findByRole('button', { name: 'Sign out' })).toBeVisible();
  await user.click(screen.getByRole('button', { name: 'Sign out' }));await waitFor(() => expect(location.pathname).toBe('/login'));
});
it('restores the authenticated identity and rejects the employee console route', async () => {
  signedIn = true;history.replaceState(null, '', '/console/dishes');render(<App />);
  expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeVisible();
});

it('logout closes registered streams and removes personal query data', async () => {
  signedIn = true;history.replaceState(null, '', '/menu');
  const queries = new QueryClient();queries.setQueryData(['personal-orders', employee.userId], ['private']);
  const closed = vi.fn();const unsubscribe = onSessionEnd(closed);
  try {
    render(<QueryClientProvider client={queries}><SessionProvider><BrowserRouter><AppRoutes /></BrowserRouter></SessionProvider></QueryClientProvider>);
    const user = userEvent.setup();await user.click(await screen.findByRole('button', { name: 'Sign out' }));
    await waitFor(() => expect(location.pathname).toBe('/login'));
    expect(closed).toHaveBeenCalled();expect(queries.getQueryData(['personal-orders', employee.userId])).toBeUndefined();
  } finally { unsubscribe(); }
});
