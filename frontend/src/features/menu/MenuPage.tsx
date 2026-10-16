import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import type { Category, MenuDish } from '../../shared/api/types';
import { money } from '../../shared/values';
import { Loading, ErrorNotice } from '../../shared/ui/Status';
import { useCart } from '../cart/CartProvider';
import { totalQuantity } from '../cart/cartReducer';
import { usePurchaseAvailability } from '../cart/usePurchaseAvailability';
import { categories, useMenuQuery } from './menuQuery';
import { DishDialog } from './DishDialog';
import { PreferenceToggle } from '../preferences/PreferenceToggle';
import { useInventoryStream } from '../inventory/useInventoryStream';
import { inventoryDish } from '../inventory/inventoryState';
import { StockBadge } from '../inventory/StockBadge';
import { DishImage } from './DishImage';
import { RecommendationPanel } from '../recommendations/RecommendationPanel';
function AvailabilityNotice({ dish }: { dish: MenuDish }) { const availability = usePurchaseAvailability(dish);return availability.reason ? <small className="muted">{availability.reason}</small> : null; }
export function MenuPage() {
  const cart = useCart(), queries = useQueryClient();const [showRecommendations, setShowRecommendations] = useState(false);const aiOpener = useRef<HTMLButtonElement>(null);const [recommended, setRecommended] = useState(false);const opener = useRef<HTMLElement | null>(null);const [selected, setSelected] = useState<Category[]>([]), [search, setSearch] = useState(''), [debounced, setDebounced] = useState(''), [page, setPage] = useState(0), [dish, setDish] = useState<MenuDish | null>(null);
  useEffect(() => { const timer = setTimeout(() => { setDebounced(search.trim());setPage(0); }, 200);return () => clearTimeout(timer); }, [search]);
  const { state: inventory, mergeMenu } = useInventoryStream(cart.date);
  const menu = useMenuQuery(cart.date, selected, debounced, page, recommended, Boolean(inventory?.snapshot || inventory?.status === 'offline'), inventory?.epoch ?? 0);
  useEffect(() => {
    if (!menu.data || !inventory) return;
    if (menu.data.menuRevision < inventory.revision) { void queries.invalidateQueries({ queryKey: ['menu', cart.date] });return; }
    mergeMenu(menu.data, inventory.epoch);
  }, [menu.data, inventory?.epoch, inventory?.revision, cart.date, queries, mergeMenu]);
  const lastRevision = useRef<{ date: string; revision: number } | null>(null), refreshCart = useRef(cart.refresh);refreshCart.current = cart.refresh;
  useEffect(() => {
    if (!inventory?.snapshot) return;const previous = lastRevision.current;
    if (previous?.date === cart.date && previous.revision !== inventory.revision) { setDish(null);refreshCart.current(); }
    lastRevision.current = { date: cart.date, revision: inventory.revision };
  }, [cart.date, inventory?.revision, inventory?.snapshot]);
  const items = (menu.data?.items ?? []).flatMap(item => { const current = inventory ? inventoryDish(inventory, item) : item;return current ? [current] : []; });
  const currentDish = dish && (inventory ? inventoryDish(inventory, dish) : dish);
  useEffect(() => { setDish(null);setPage(0); }, [cart.date]);
  return <section><div className="page-heading"><div><p className="eyebrow">FRESH PICKS FOR YOUR WORKDAY</p><h1>Menu</h1><p className="muted">A good lunch starts here. Find your favorite, make it yours.</p></div><Link className="button" to="/cart">View cart ({totalQuantity(cart.state)})</Link></div>
    <div className="menu-toolbar"><div className="field"><label htmlFor="menu-date">Delivery date</label><input id="menu-date" type="date" value={cart.date} onChange={event => { if (/^\d{4}-\d{2}-\d{2}$/.test(event.target.value)) cart.setDate(event.target.value); }} /></div><div className="field search-field"><label htmlFor="menu-search">Search dishes</label><input id="menu-search" type="search" placeholder="Search name or ingredients" value={search} onChange={event => setSearch(Array.from(event.target.value).slice(0, 50).join(''))} aria-describedby="search-limit" /><small id="search-limit">{Array.from(search).length}/50 characters</small></div></div>
    <fieldset className="category-filter"><legend>Cuisines</legend>{categories.map(category => <label key={category} className={selected.includes(category) ? 'chip selected' : 'chip'}><input type="checkbox" checked={selected.includes(category)} onChange={event => { setSelected(previous => event.target.checked ? [...previous, category] : previous.filter(value => value !== category));setPage(0); }} />{category}</label>)}</fieldset>
    <div className="recommendation-entry"><button ref={aiOpener} type="button" className="secondary" aria-expanded={showRecommendations} onClick={() => setShowRecommendations(value => !value)}>Help me choose</button><span className="muted">Personalized suggestions for your next meal.</span></div>
    {showRecommendations && <RecommendationPanel key={`${cart.date}:${inventory?.revision ?? 0}`} date={cart.date} meal={cart.meal} onMeal={cart.setMeal} close={() => { setShowRecommendations(false);aiOpener.current?.focus(); }} choose={(item, element) => { opener.current=element;setDish(item); }} />}
    <PreferenceToggle enabled={recommended} onChange={enabled => { setRecommended(enabled);setPage(0); }} />
    <p role="status" className={`inventory-status ${inventory?.status === 'live' ? 'live' : ''}`}>{inventory?.status === 'live' ? 'Live inventory' : inventory?.status === 'offline' ? 'Inventory connection interrupted.' : 'Connecting to live inventory…'}</p>
    {inventory?.status === 'offline' && <p className="muted">Quantities may be out of date. Availability will be checked when you order.</p>}
    {cart.notice && <ErrorNotice message={cart.notice} retry={cart.refresh} />}{menu.isPending && <Loading>Preparing your menu…</Loading>}{menu.isError && <ErrorNotice message="The menu could not be loaded. Please try again." retry={() => { void menu.refetch(); }} />}
    {menu.data && <><div className="results-heading"><p>{menu.data.totalElements} dishes · {cart.date}</p><small>Lunch & dinner share the daily supply.</small></div>{!items.length ? <div className="panel empty-state"><h2>No dishes found</h2><p>Try another date or clear your search and cuisine filters.</p><button type="button" className="secondary" onClick={() => { setSelected([]);setSearch('');setPage(0); }}>Clear filters</button></div> : <div className="menu-grid">{items.map(item => <article className="dish-card" key={item.dishId}><DishImage src={item.imageUrl} name={item.name} /><div className="dish-card-body"><p className="eyebrow">{item.category}</p><h2>{item.name}</h2>{recommended && (item.preferenceMatch.cuisine || item.preferenceMatch.spice) && <p className="preference-match">{[item.preferenceMatch.cuisine && "Favorite cuisine", item.preferenceMatch.spice && "Matches your spice preference"].filter(Boolean).join(" · ")}</p>}<p className="dish-description">{item.description}</p><div className="card-price"><strong>¥{money(item.basePriceCents)}</strong><StockBadge remaining={item.remaining} /></div><button className="secondary" type="button" aria-label={`View ${item.name}`} onClick={event => { opener.current = event.currentTarget;setDish(item); }}>View & customize</button><AvailabilityNotice dish={item} /></div></article>)}</div>}
      {menu.data.totalElements > 24 && <nav className="pagination" aria-label="Menu pages"><button type="button" className="secondary" disabled={page === 0} onClick={() => setPage(previous => previous - 1)}>Previous</button><span>Page {page + 1} of {Math.ceil(menu.data.totalElements / 24)}</span><button type="button" className="secondary" disabled={(page + 1) * 24 >= menu.data.totalElements} onClick={() => setPage(previous => previous + 1)}>Next</button></nav>}</>}
    {currentDish && <DishDialog key={`${currentDish.dishId}:${currentDish.dishVersion}`} dish={currentDish} restoreFocus={opener.current} close={() => setDish(null)} />}
  </section>;
}
