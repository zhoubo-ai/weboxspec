import { useEffect, useRef, useState } from 'react';
import type { Meal, MenuDish } from '../../shared/api/types';
import { ApiError, postStream } from '../../shared/api/client';
import { readSse } from '../../shared/stream/readSse';
import { beginRecommendations, recommendationEvent, recommendationEof, type RecommendationState } from './recommendationState';
import { useSession, onSessionEnd } from '../auth/session';
import { usePreferences } from '../preferences/usePreferences';
import { useCurrentInventory } from '../inventory/useInventoryStream';
import { inventoryDish } from '../inventory/inventoryState';
import { usePurchaseAvailability } from '../cart/usePurchaseAvailability';
import { DishImage } from '../menu/DishImage';
import { StockBadge } from '../inventory/StockBadge';
import { money, safeInteger } from '../../shared/values';
function RecommendedDish({dish,reason,choose}:{dish:MenuDish;reason:string;choose:(dish:MenuDish,opener:HTMLElement)=>void}) {
  const availability=usePurchaseAvailability(dish);
  const minimum=dish.optionGroups.filter(group=>group.required).reduce((price,group)=>safeInteger(price+Math.min(...group.options.map(option=>option.extraCents))),dish.basePriceCents);
  return <article className="dish-card recommendation-card"><DishImage src={dish.imageUrl} name={dish.name}/><div className="dish-card-body"><p className="eyebrow">{dish.category}</p><h3>{dish.name}</h3><p className="recommendation-reason">{reason}</p><div className="card-price"><strong>From ¥{money(minimum)}</strong><StockBadge remaining={dish.remaining}/></div><button type="button" className="secondary" onClick={event=>choose(dish,event.currentTarget)}>View & customize</button>{availability.reason && <small className="muted">{availability.reason}</small>}</div></article>;
}
export function RecommendationPanel({date,meal,onMeal,close,choose}:{date:string;meal:Meal;onMeal:(meal:Meal)=>void;close:()=>void;choose:(dish:MenuDish,opener:HTMLElement)=>void}) {
  const {expire}=useSession(),preferences=usePreferences(),inventory=useCurrentInventory(date);
  const [prompt,setPrompt]=useState(''),[state,setState]=useState<RecommendationState|null>(null),[validation,setValidation]=useState('');
  const generation=useRef(0),request=useRef<AbortController|null>(null),input=useRef<HTMLTextAreaElement>(null);
  const stop=()=>{generation.current++;request.current?.abort();request.current=null;};
  useEffect(()=>{input.current?.focus();const unsubscribe=onSessionEnd(()=>{stop();setState(null);});return()=>{stop();unsubscribe();};},[]);
  useEffect(()=>{stop();setState(null);},[date,meal,preferences.data?.version]);
  async function submit() {
    stop();const value=prompt.trim();if(!value || Array.from(value).length>1000){setValidation('Describe your meal in 1 to 1,000 characters.');return;}
    setValidation('');const epoch=generation.current,controller=new AbortController();request.current=controller;let current=beginRecommendations(date,epoch);setState(current);
    const publish=()=>{if(epoch===generation.current && !controller.signal.aborted)setState(current);};
    try {
      const body=await postStream('/recommendations/stream',{deliveryDate:date,meal,prompt:value},controller.signal);
      for await(const event of readSse(body,controller.signal)) {
        if(epoch!==generation.current)return;current=recommendationEvent(current,event,epoch);publish();
        if(current.code==='SESSION_EXPIRED'){expire();return;}
        if(current.status==='error')break;
      }
      current=recommendationEof(current,epoch);publish();
    }catch(error) {
      if(epoch!==generation.current || controller.signal.aborted)return;
      current={...current,status:'error',partial:current.cards.length>0,code:error instanceof ApiError?error.code:'AI_UNAVAILABLE',retryable:error instanceof ApiError?error.retryable:true,message:error instanceof ApiError?error.message:'Recommendations could not be completed. Retry or return to the menu.'};publish();
    }
  }
  const active=state?.status==='loading' || state?.status==='streaming',characters=Array.from(prompt.trim()).length;
  return <section className="panel recommendations" aria-labelledby="recommendation-title"><div className="page-heading"><div><p className="eyebrow">A LITTLE HELP CHOOSING</p><h2 id="recommendation-title">Find my meal</h2><p>Tell us what sounds good. We use your saved preferences and recent orders.</p></div><button type="button" className="secondary" onClick={()=>{stop();close();}}>Return to menu</button></div>
    <form onSubmit={event=>{event.preventDefault();void submit();}}><div className="field"><label htmlFor="recommendation-meal">Meal for {date}</label><select id="recommendation-meal" value={meal} onChange={event=>onMeal(event.target.value as Meal)}><option>Lunch</option><option>Dinner</option></select></div><div className="field"><label htmlFor="recommendation-prompt">What would you like?</label><textarea ref={input} id="recommendation-prompt" rows={3} value={prompt} placeholder="Something mild under ¥40. No repeats from the last 7 days." onChange={event=>{stop();setState(null);setPrompt(event.target.value);setValidation('');}} aria-describedby="recommendation-count"/><small id="recommendation-count">{characters}/1,000 characters · Final price depends on your options.</small></div>{validation && <p role="alert">{validation}</p>}<div className="form-actions"><button disabled={!characters || characters>1000 || preferences.isPending || preferences.isError} type="submit">{active?'Start new recommendation':'Find recommendations'}</button>{active && <button className="secondary" type="button" onClick={()=>{stop();setState(null);}}>Stop recommendations</button>}</div></form>
    <div aria-live="polite" role="status">{active && <p>Finding dishes for you… {state.cards.length>0 && `${state.cards.length} ready so far.`}</p>}{state?.status==='complete' && <p>Recommendations complete.</p>}{state?.status==='no-match' && <p>No matching dishes are available for this meal. Try different preferences or return to the menu.</p>}</div>
    {state?.status==='error' && <div className="notice error" role="alert"><p>{state.partial?'Only part of your recommendations arrived. ':''}{state.message}</p>{state.retryable && <button className="secondary" type="button" onClick={()=>void submit()}>Retry recommendations</button>}</div>}
    {Boolean(state?.cards.length) && <div className="menu-grid">{state!.cards.map(card=>{const dish=inventory?inventoryDish(inventory,card.dish):card.dish;return dish?<RecommendedDish key={dish.dishId} dish={dish} reason={card.reason} choose={choose}/>:null;})}</div>}
  </section>;
}
