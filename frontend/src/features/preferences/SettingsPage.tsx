import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../shared/api/client';
import type { Allergen, Category, Preferences, PreferencesWrite } from '../../shared/api/types';
import { money } from '../../shared/values';
import { ErrorNotice, Loading } from '../../shared/ui/Status';
import { useSession } from '../auth/session';
import { categories } from '../menu/menuQuery';
import { usePreferences } from './usePreferences';
import { budgetRange } from './preferenceRules';
export const allergens: Allergen[] = ['Peanuts', 'Dairy', 'Egg', 'Gluten', 'Soy', 'Fish', 'Shellfish'];
type Draft = Omit<PreferencesWrite, 'budgetMinCents' | 'budgetMaxCents'> & { min: string; max: string };
const draft = (value: Preferences): Draft => ({ allergens: value.allergens, cuisines: value.cuisines, spiceLevel: value.spiceLevel, flavor: value.flavor, expectedVersion: value.version, min: value.budgetMinCents === null ? '' : money(value.budgetMinCents), max: value.budgetMaxCents === null ? '' : money(value.budgetMaxCents) });
export function SettingsPage() {
  const query = usePreferences(), queries = useQueryClient(), { user } = useSession();
  const [form, setForm] = useState<Draft | null>(null), [error, setError] = useState(''), [notice, setNotice] = useState(''), [saving, setSaving] = useState(false), [conflict, setConflict] = useState(false);
  useEffect(() => { if (query.data && !form) setForm(draft(query.data)); }, [query.data, form]);
  async function save() {
    if (!form) return;setError('');setNotice('');setSaving(true);
    try {
      const { min, max, ...fields } = form;const result = await api<Preferences>('/me/preferences', { method: 'PUT', json: { ...fields, ...budgetRange(min, max) } });
      queries.setQueryData(['preferences', user!.userId], result);setForm(draft(result));setConflict(false);setNotice('Preferences saved.');
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Preferences could not be saved.');setConflict(failure instanceof ApiError && failure.code === 'VERSION_CONFLICT'); }
    finally { setSaving(false); }
  }
  function change(patch: Partial<Draft>) { setForm(previous => previous && { ...previous, ...patch });setNotice(''); }
  function toggle<T extends Allergen | Category>(values: T[], value: T, checked: boolean) { return checked ? [...values, value] : values.filter(item => item !== value); }
  return <section className="preferences-page"><div className="page-heading"><div><p className="eyebrow">YOUR TASTE, YOUR CHOICE</p><h1>Preferences</h1><p>Personalize your menu and keep useful reminders close.</p></div></div>
    {query.isPending && <Loading>Loading your preferences…</Loading>}{query.isError && <ErrorNotice message="Preferences could not be loaded." retry={() => { void query.refetch(); }} />}{error && <ErrorNotice message={error} />}{notice && <p className="notice" role="status">{notice}</p>}
    {conflict && <div className="notice"><p>Your edits have not been saved. Reload the latest preferences, then review and save your changes.</p><button type="button" disabled={saving} onClick={async () => { const result = await query.refetch();if (result.data && !result.isError) { setForm(draft(result.data));setConflict(false);setError('');setNotice('Latest preferences loaded. Review before saving.'); } }}>Reload latest preferences</button></div>}
    {form && <form className="panel preferences-form" onSubmit={event => { event.preventDefault();void save(); }}><fieldset disabled={saving}><legend>Allergen reminders</legend><p className="form-hint">We will ask before adding selections that contain these declared allergens. You decide whether to continue. Missing labels do not guarantee safety.</p><div className="preference-options">{allergens.map(value => <label key={value}><input type="checkbox" checked={form.allergens.includes(value)} onChange={event => change({ allergens: toggle(form.allergens, value, event.target.checked) })} />{value}</label>)}</div></fieldset>
    <fieldset disabled={saving}><legend>Favorite cuisines</legend><div className="preference-options">{categories.map(value => <label key={value}><input type="checkbox" checked={form.cuisines.includes(value)} onChange={event => change({ cuisines: toggle(form.cuisines, value, event.target.checked) })} />{value}</label>)}</div></fieldset>
    <fieldset className="checkout-fields" disabled={saving}><legend>Flavor and meal budget</legend><div className="field"><label htmlFor="preferred-spice">Spice level</label><select id="preferred-spice" value={form.spiceLevel ?? ''} onChange={event => change({ spiceLevel: (event.target.value || null) as Draft['spiceLevel'] })}><option value="">No preference</option>{['None', 'Mild', 'Medium', 'Hot'].map(value => <option key={value}>{value}</option>)}</select></div><div className="field"><label htmlFor="preferred-flavor">Flavor intensity</label><select id="preferred-flavor" value={form.flavor ?? ''} onChange={event => change({ flavor: (event.target.value || null) as Draft['flavor'] })}><option value="">No preference</option>{['Light', 'Moderate', 'Strong'].map(value => <option key={value}>{value}</option>)}</select></div><div className="field"><label htmlFor="budget-min">Minimum budget (CNY)</label><input id="budget-min" inputMode="decimal" value={form.min} onChange={event => change({ min: event.target.value })} /></div><div className="field"><label htmlFor="budget-max">Maximum budget (CNY)</label><input id="budget-max" inputMode="decimal" value={form.max} onChange={event => change({ max: event.target.value })} /></div><p className="form-hint">Set both limits, or leave both blank. Exceeding your maximum shows a reminder; it does not prevent ordering.</p></fieldset>
    <div className="form-actions"><button type="submit" disabled={saving || conflict}>{saving ? 'Saving…' : 'Save preferences'}</button><button type="button" className="secondary" disabled={saving} onClick={() => { change({ allergens: [], cuisines: [], spiceLevel: null, flavor: null, min: '', max: '' });setNotice('All fields cleared. Save preferences to apply.'); }}>Clear preferences</button></div></form>}
  </section>;
}
