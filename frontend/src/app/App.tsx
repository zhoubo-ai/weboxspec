import { useState } from 'react';
import { BrowserRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AppRoutes } from './router';
import { SessionProvider } from '../features/auth/session';
import '../styles/global.css';
export function App() {
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 0, refetchOnWindowFocus: true }, mutations: { retry: false } } }));
  return <QueryClientProvider client={client}><SessionProvider><BrowserRouter><AppRoutes /></BrowserRouter></SessionProvider></QueryClientProvider>;
}
