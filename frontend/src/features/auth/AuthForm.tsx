import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { api } from '../../shared/api/client';
import { password as validPassword, requiredText } from '../../shared/values';
import { ErrorNotice, Field } from '../../shared/ui/Status';
import { homeFor, useSession } from './session';
export function AuthForm({ register = false }: { register?: boolean }) {
  const session = useSession(); const navigate = useNavigate();const location = useLocation();
  const [email, setEmail] = useState('');const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null); const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();if (busy) return;setError(null);
    try {
      const cleanEmail = requiredText(email, 200);validPassword(password);setBusy(true);
      if (register) {
        await api('/auth/register', { method: 'POST', json: { email: cleanEmail, password } });
        setPassword('');navigate('/login', { replace: true, state: { registered: true } });
      } else {
        const user = await session.login(cleanEmail, password);setPassword('');navigate(homeFor(user), { replace: true });
      }
    } catch (e) { if (!(e instanceof DOMException && e.name === 'AbortError')) setError(e instanceof Error ? e.message : 'Please try again.'); }
    finally { setBusy(false); }
  }
  return <section className="panel auth-panel"><p className="eyebrow">WELCOME TO WEBOX</p><h1>{register ? 'Create an account' : 'Sign in'}</h1>
    {!register && location.state?.registered && <p className="notice" role="status">Account created. Sign in to continue.</p>}
    <p>{register ? 'Your next good meal starts here.' : 'Your meals, just the way you like them.'}</p>
    {error && <ErrorNotice message={error} />}
    <form onSubmit={submit} aria-busy={busy}>
      <Field label="Email" htmlFor="email"><input id="email" name="email" type="email" autoComplete="username" required value={email} onChange={e => setEmail(e.target.value)} disabled={busy} /></Field>
      <Field label="Password" htmlFor="password"><input id="password" name="password" type="password" autoComplete={register ? 'new-password' : 'current-password'} required aria-describedby="password-hint" value={password} onChange={e => setPassword(e.target.value)} disabled={busy} /></Field>
      <p id="password-hint" className="form-hint">8–128 characters, including a letter and a number.</p>
      <button type="submit" disabled={busy}>{busy ? 'Please wait…' : register ? 'Create account' : 'Sign in'}</button>
    </form><p>{register ? 'Already have an account? ' : 'New to WeBox? '}<Link to={register ? '/login' : '/register'}>{register ? 'Sign in' : 'Create an account'}</Link></p>
  </section>;
}
