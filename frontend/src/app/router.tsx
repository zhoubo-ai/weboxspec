import { Link, NavLink, Outlet, Route, Routes, useNavigate } from 'react-router';
import { lazy, Suspense, useState } from 'react';
const DashboardPage = lazy(() => import('../features/dashboard/DashboardPage').then(module => ({ default: module.DashboardPage })));
import { CartProvider } from '../features/cart/CartProvider';
import { CartPage } from '../features/cart/CartPage';
import { SettingsPage } from '../features/preferences/SettingsPage';
import { MenuPage } from '../features/menu/MenuPage';
import { CheckoutPage } from '../features/checkout/CheckoutPage';
import { OrdersPage } from '../features/orders/OrdersPage';
import { OrderDetailPage } from '../features/orders/OrderDetailPage';
import { useSession } from '../features/auth/session';
import { SessionBoundary, GuestBoundary } from './SessionBoundary';
import { LoginPage } from '../features/auth/LoginPage';
import { RegisterPage } from '../features/auth/RegisterPage';
import { DishesPage } from '../features/console/DishesPage';
import { DailyMenusPage } from '../features/console/DailyMenusPage';
import { OrdersPage as ConsoleOrdersPage } from '../features/console/OrdersPage';
function AccountMenu() {
  const session = useSession();const navigate = useNavigate();const [error, setError] = useState('');const [busy, setBusy] = useState(false);
  if (!session.user) return <Link to="/login">Sign in</Link>;
  return <div className="account-menu">{session.user.role === 'Admin' && <Link to="/console/dishes">Console</Link>}<button className="secondary" type="button" disabled={busy} onClick={async () => {
    setBusy(true);setError('');try { await session.logout();navigate('/login', { replace: true }); } catch { setError('Could not sign out. Please try again.'); } finally { setBusy(false); }
  }}>Sign out</button>{error && <span role="alert">{error}</span>}</div>;
}
function EmployeeLayout() {
  return <><a className="skip-link" href="#main">Skip to content</a><header className="site-header"><Link className="brand" to="/">WeBox<span>WORKDAY MEALS</span></Link><nav aria-label="Main navigation"><NavLink to="/menu">Menu</NavLink><NavLink to="/cart">Cart</NavLink><NavLink to="/orders">Orders</NavLink><NavLink to="/settings">Preferences</NavLink></nav><AccountMenu /></header><main id="main" className="content"><Outlet /></main><footer>Good food. A better workday.</footer></>;
}
function ConsoleLayout() {
  return <><a className="skip-link" href="#main">Skip to content</a><header className="site-header console-header"><Link className="brand" to="/console/dishes">WeBox<span>CONSOLE</span></Link><nav aria-label="Console navigation"><NavLink to="/console/dishes">Dishes</NavLink><NavLink to="/console/menus">Daily menus</NavLink><NavLink to="/console/orders">Orders</NavLink><NavLink to="/console/dashboard">Dashboard</NavLink></nav><Link to="/menu">Employee menu</Link><AccountMenu /></header><main id="main" className="content"><Outlet /></main></>;
}
function CartLayout() { const { user } = useSession();return <CartProvider key={user!.userId}><Outlet /></CartProvider>; }
export function AppRoutes() {
  return <Routes><Route element={<EmployeeLayout />}><Route index element={<section className="welcome"><p className="eyebrow">YOUR WORKDAY, WELL FED</p><h1>WeBox</h1><p>Make room for a good meal.</p><Link className="button" to="/menu">Explore the menu</Link></section>} />
    <Route element={<GuestBoundary />}><Route path="login" element={<LoginPage />} /><Route path="register" element={<RegisterPage />} /></Route><Route element={<SessionBoundary />}><Route element={<CartLayout />}><Route path="menu" element={<MenuPage />} /><Route path="cart" element={<CartPage />} /><Route path="checkout" element={<CheckoutPage />} /><Route path="orders" element={<OrdersPage />} /><Route path="orders/:orderId" element={<OrderDetailPage />} /><Route path="settings" element={<SettingsPage />} /></Route></Route><Route path="*" element={<section className="panel"><h1>Page not found</h1><Link to="/">Back to WeBox</Link></section>} /></Route>
    <Route element={<SessionBoundary admin />}><Route path="console" element={<ConsoleLayout />}><Route index element={<DishesPage />} /><Route path="dishes" element={<DishesPage />} /><Route path="menus" element={<DailyMenusPage />} /><Route path="orders" element={<ConsoleOrdersPage />} /><Route path="dashboard" element={<Suspense fallback={<p role="status">Loading dashboard…</p>}><DashboardPage /></Suspense>} /></Route></Route></Routes>;
}
