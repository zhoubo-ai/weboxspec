export function PreferenceToggle({ enabled, onChange }: { enabled: boolean; onChange: (enabled: boolean) => void }) {
  return <label className="preference-toggle"><input type="checkbox" checked={enabled} onChange={event => onChange(event.target.checked)} />Recommended for Me<span>Favorite cuisines first, then matching spice level.</span></label>;
}
