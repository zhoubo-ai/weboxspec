import { useRef, useState } from 'react';
import type { CartLine } from './cartReducer';
import { DishDialog } from '../menu/DishDialog';
import { Link } from 'react-router';
import { useCart } from './CartProvider';
import { purchaseAvailability } from './purchaseAvailability';
import { totalCents, totalQuantity } from './cartReducer';
import { configuration } from './configuration';
import { money } from '../../shared/values';
import { Loading, ErrorNotice } from '../../shared/ui/Status';
import { DishImage } from '../menu/DishImage';
export function CartPage() {
  const [editing, setEditing] = useState<CartLine | null>(null), opener = useRef<HTMLElement | null>(null);
  const cart = useCart(), count = totalQuantity(cart.state), blocked = cart.busy || !count || cart.state.lines.some(line => line.issue || !line.dish.remaining);
  return <section><div className="page-heading"><div><p className="eyebrow">MADE JUST FOR YOU</p><h1>Your cart</h1><p>Delivery date: {cart.date} · Up to 5 portions per order</p></div><Link to="/menu">Keep browsing</Link></div>
    {cart.busy && <Loading>Checking current prices and availability…</Loading>}{cart.notice && <ErrorNotice message={cart.notice} retry={cart.refresh} />}{cart.state.error && <ErrorNotice message={cart.state.error} />}{count > 0 && <button type="button" className="secondary recheck-cart" disabled={cart.busy} onClick={cart.refresh}>Check current availability</button>}
    {!count && !cart.busy ? <div className="panel empty-state"><h2>Your next good meal starts here</h2><p>Your cart is empty. Explore the menu to add your first dish.</p><Link className="button" to="/menu">Browse menu</Link></div> : <div className="cart-layout"><div>{cart.state.lines.map(line => {
      const names = line.original?.snapshot.optionNames ?? line.dish.optionGroups.flatMap(group => group.options.filter(option => line.optionIds.includes(option.optionId)).map(option => option.name));
      const availability = purchaseAvailability(cart.state, line.dish);
      return <article className="cart-line panel" key={line.key} aria-label={`${line.dish.name}, ${names.join(', ')}`}><DishImage src={line.dish.imageUrl} name={line.dish.name} /><div className="cart-line-body"><h2>{line.dish.name}</h2><p>{names.join(' · ') || 'Standard preparation'}</p><p>¥{money(line.unitPriceCents)} each</p>
        {line.issue === 'changed' && <div className="notice"><p>This dish has changed. Current unit price: ¥{money(configuration(line.dish, line.optionIds).unitPriceCents)}. Review and confirm to continue.</p><button type="button" disabled={cart.busy} onClick={() => cart.dispatch({ type: 'confirm', key: line.key })}>Confirm updated item</button></div>}
        {line.issue === 'unavailable' && <p className="notice error" role="alert">This configuration or quantity is unavailable on the selected date. Reduce the quantity, remove it, or choose another dish.</p>}
        <button type="button" className="text-button" disabled={cart.busy} onClick={event => { opener.current = event.currentTarget;setEditing(line); }}>Edit options</button><div className="quantity-controls"><button type="button" className="secondary" aria-label={`Decrease ${line.dish.name}`} disabled={cart.busy} onClick={() => cart.dispatch({ type: 'decrease', key: line.key })}>−</button><span aria-label="Quantity">{line.quantity}</span><button type="button" className="secondary" aria-label={`Increase ${line.dish.name}`} disabled={cart.busy || !availability.canAdd} onClick={() => cart.dispatch({ type: 'add', dish: line.dish, optionIds: line.optionIds })}>+</button><button type="button" className="text-button" disabled={cart.busy} onClick={() => cart.dispatch({ type: 'remove', key: line.key })}>Remove</button></div><strong>Subtotal: ¥{money(line.unitPriceCents * line.quantity)}</strong></div></article>;
    })}</div><aside className="panel cart-summary"><h2>Your meal</h2><p>{count} {count === 1 ? 'portion' : 'portions'}</p><p className="cart-total">Total <strong>¥{money(totalCents(cart.state))}</strong></p>{count >= 5 && <p className="form-hint">You can add up to 5 portions per order.</p>}{blocked ? <><button disabled>Continue to checkout</button><p className="form-hint">Resolve unavailable or changed items before checkout.</p></> : <Link className="button" to="/checkout">Continue to checkout</Link>}<p className="form-hint">Items in your cart do not reserve stock. Availability is confirmed when you place your order.</p></aside></div>}
    {editing && <DishDialog key={editing.key} dish={editing.dish} editing={editing} restoreFocus={opener.current} close={() => setEditing(null)} />}
  </section>;
}
