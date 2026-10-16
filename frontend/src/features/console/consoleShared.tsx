import { useEffect, useRef, type ReactNode } from 'react';
import type { Allergen } from '../../shared/api/types';
import { allergens } from '../preferences/SettingsPage';
export function AllergenFields({ title, value, onChange }: { title: string; value: Allergen[]; onChange: (next: Allergen[]) => void }) {
  return <fieldset className="allergen-fields"><legend>{title}</legend><div className="preference-options">{allergens.map(label => <label key={label}><input type="checkbox" checked={value.includes(label)} onChange={event => onChange(event.target.checked ? [...value, label] : value.filter(x => x !== label))} />{label}</label>)}</div></fieldset>;
}
export function ConsoleDialog({ title, onClose, children, busy = false }: { title: string; onClose: () => void; children: ReactNode; busy?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => { const prior = document.activeElement;ref.current?.showModal();return () => { if (prior instanceof HTMLElement) prior.focus(); }; }, []);
  return <dialog className="console-dialog" ref={ref} aria-label={title} onCancel={event => { event.preventDefault();if (!busy) onClose(); }}><div className="dialog-heading"><h2>{title}</h2><button type="button" className="secondary" disabled={busy} onClick={onClose} aria-label={`Close ${title.toLowerCase()}`}>Close</button></div>{children}</dialog>;
}
export function Pagination({ page, size, total, onChange }: { page: number; size: number; total: number; onChange: (page: number) => void }) {
  return <nav className="pagination" aria-label="Pagination"><button type="button" className="secondary" disabled={!page} onClick={() => onChange(page - 1)}>Previous</button><span>Page {page + 1} of {Math.max(1, Math.ceil(total / size))}</span><button type="button" className="secondary" disabled={(page + 1) * size >= total} onClick={() => onChange(page + 1)}>Next</button></nav>;
}
