import { stockLabel } from './inventoryState';
export function StockBadge({ remaining }: { remaining: number }) { return <span className={remaining === 0 ? 'stock-badge sold-out' : remaining <= 3 ? 'stock-badge low-stock' : 'stock-badge'} aria-live="polite">{stockLabel(remaining)}</span>; }
