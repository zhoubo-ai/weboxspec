import type { Quote } from '../../shared/api/types';
export function CheckoutWarnings({ quote }: { quote: Quote }) {
  return <>{quote.warnings.map((warning, index) => {
    const line = warning.code === 'FLAGGED_ALLERGEN' && warning.itemIndex !== undefined ? quote.items[warning.itemIndex] : null;
    return <div className="notice checkout-warning" role="status" key={`${warning.code}-${index}`}><strong>{warning.code === 'BUDGET_EXCEEDED' ? 'Budget reminder' : 'Allergen reminder'}</strong>{line && <p>{line.quantity} × {line.dishName} · {line.selectedOptions.map(option => option.optionName).join(', ') || 'Standard preparation'}</p>}<p>{warning.message}</p></div>;
  })}</>;
}
