import { useEffect, useRef } from 'react';
import type { Allergen } from '../../shared/api/types';
export function AllergenConfirmation({ name, matches, resolve }: { name: string; matches: Allergen[]; resolve: (accepted: boolean) => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => { const previous = document.activeElement, element = dialog.current;element?.showModal();return () => { element?.close();if (previous instanceof HTMLElement && previous.isConnected) previous.focus(); }; }, []);
  return <dialog ref={dialog} className="allergen-dialog" aria-labelledby="allergen-title" aria-describedby="allergen-description" onCancel={event => { event.preventDefault();resolve(false); }}><p className="eyebrow">CHECK YOUR SELECTION</p><h2 id="allergen-title">Allergen reminder</h2><p id="allergen-description"><strong>{name}</strong> contains allergens you flagged: <strong>{matches.join(', ')}</strong>.</p><p>These labels describe the base dish and your selected options. You decide whether to continue. Missing labels do not guarantee safety.</p><div className="form-actions"><button type="button" className="secondary" autoFocus onClick={() => resolve(false)}>Go back</button><button type="button" onClick={() => resolve(true)}>Continue with this selection</button></div></dialog>;
}
