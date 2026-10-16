import { Navigate, Outlet } from 'react-router';
import { homeFor, useSession } from '../features/auth/session';
import { ErrorNotice, Loading } from '../shared/ui/Status';
export function SessionBoundary({ admin = false }: { admin?: boolean }) {
  const session = useSession();
  if (session.loading) return <Loading>Checking your session…</Loading>;
  if (!session.user) return session.error ? <ErrorNotice message={session.error} retry={() => { void session.refresh(); }} /> : <Navigate to="/login" replace />;
  if (admin && session.user.role !== 'Admin') return <section className="panel"><h1>Access denied</h1><p>Administrator access is required.</p></section>;
  // A transient session check failure must not discard an in-flight order's frozen key.
  return <>{session.error && <ErrorNotice message={session.error} retry={() => { void session.refresh(); }} />}<div hidden={Boolean(session.error)}><Outlet /></div></>;
}
export function GuestBoundary() {
  const session = useSession();
  if (session.loading) return <Loading>Checking your session…</Loading>;
  if (session.user) return <Navigate to={homeFor(session.user)} replace />;
  return <Outlet />;
}
