import type { ReactNode } from 'react';
export function Loading({ children = 'Loading…' }: { children?: ReactNode }) {
  return <p role="status" aria-live="polite">{children}</p>;
}
export function ErrorNotice({ message, retry }: { message: string; retry?: () => void }) {
  return <div className="notice error" role="alert"><p>{message}</p>{retry && <button type="button" onClick={retry}>Try again</button>}</div>;
}
export function Field({ label, htmlFor, error, children }: { label: string; htmlFor: string; error?: string; children: ReactNode }) {
  return <div className="field"><label htmlFor={htmlFor}>{label}</label>{children}{error && <p id={`${htmlFor}-error`} className="field-error" role="alert">{error}</p>}</div>;
}
