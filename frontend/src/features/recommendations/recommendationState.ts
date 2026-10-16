import type { MenuDish, RecommendationCard } from '../../shared/api/types';
import type { SseEvent } from '../../shared/stream/readSse';
export type RecommendationState={date:string;epoch:number;requestId:string|null;status:'loading'|'streaming'|'complete'|'no-match'|'error';cards:RecommendationCard[];message:string|null;code:string|null;retryable:boolean;partial:boolean};
export const beginRecommendations=(date:string,epoch:number):RecommendationState=>({date,epoch,requestId:null,status:'loading',cards:[],message:null,code:null,retryable:false,partial:false});
const id=(value:unknown):value is string=>typeof value==='string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
const object=(value:unknown):value is Record<string,unknown>=>Boolean(value) && typeof value==='object' && !Array.isArray(value);
const integer=(value:unknown):value is number=>typeof value==='number' && Number.isSafeInteger(value) && value>=0;
const text=(value:unknown):value is string=>typeof value==='string' && value.trim().length>0;
const labels=(value:unknown):boolean=>Array.isArray(value) && value.every(label=>['Peanuts','Dairy','Egg','Gluten','Soy','Fish','Shellfish'].includes(label));
function dish(value:unknown):value is MenuDish {
  if(!object(value) || !id(value.dishId) || !text(value.name) || !text(value.description) || !text(value.protein) || typeof value.imageUrl!=='string' || !/^\/media\/[0-9a-f-]{36}$/i.test(value.imageUrl) || !integer(value.basePriceCents) || !integer(value.dishVersion) || !integer(value.remaining) || !integer(value.inventoryVersion) || !labels(value.allergens) || !['Chinese','Western','Japanese','Light Meal','Korean','Southeast Asian'].includes(value.category as string) || !['None','Mild','Medium','Hot'].includes(value.spiceLevel as string))return false;
  return object(value.preferenceMatch) && typeof value.preferenceMatch.cuisine==='boolean' && typeof value.preferenceMatch.spice==='boolean' && Array.isArray(value.optionGroups) && value.optionGroups.every(group=>object(group) && id(group.groupId) && text(group.name) && typeof group.required==='boolean' && ['Single','Multiple'].includes(group.selectionMode as string) && Array.isArray(group.options) && group.options.every(option=>object(option) && id(option.optionId) && text(option.name) && integer(option.extraCents) && labels(option.allergens)));
}
function failure(state:RecommendationState,message='Recommendations could not be completed. Retry or return to the menu.',code='AI_INVALID_RESPONSE',retryable=true):RecommendationState{return {...state,status:'error',message,code,retryable,partial:state.cards.length>0};}
export function recommendationEvent(state:RecommendationState,event:SseEvent,epoch:number):RecommendationState {
  if(epoch!==state.epoch)return state;
  try {
    const value:unknown=JSON.parse(event.data);if(!object(value) || !id(value.requestId))return failure(state);
    if(state.requestId && state.requestId!==value.requestId)return state;
    if(['complete','no-match','error'].includes(state.status))return failure(state);
    if(event.event==='meta')return state.requestId || value.deliveryDate!==state.date?failure(state):{...state,requestId:value.requestId,status:'streaming'};
    if(!state.requestId)return failure(state);
    if(event.event==='card') {
      const candidate=value.dish;
      if(value.index!==state.cards.length || state.cards.length>=3 || !dish(candidate) || !text(value.reason) || Array.from(value.reason).length>240 || /[<>\[\]`]/.test(value.reason) || state.cards.some(card=>card.dish.dishId===candidate.dishId))return failure(state);
      return {...state,cards:[...state.cards,value as RecommendationCard]};
    }
    if(event.event==='done') {
      if(value.count!==state.cards.length)return failure(state);
      if(value.outcome==='COMPLETE' && state.cards.length>=1)return {...state,status:'complete'};
      if(value.outcome==='NO_MATCH' && !state.cards.length)return {...state,status:'no-match'};return failure(state);
    }
    if(event.event==='error' && text(value.message) && typeof value.retryable==='boolean' && value.partial===(state.cards.length>0) && ['AI_BUSY','AI_UNAVAILABLE','AI_TIMEOUT','AI_INVALID_RESPONSE','INTENT_CLARIFICATION_REQUIRED','SESSION_EXPIRED'].includes(value.code as string))return failure(state,value.message,value.code as string,value.retryable);
    return failure(state);
  }catch{return failure(state);}
}
export function recommendationEof(state:RecommendationState,epoch:number):RecommendationState {return epoch!==state.epoch || ['complete','no-match','error'].includes(state.status)?state:failure(state,'The connection ended before recommendations were complete. Retry or return to the menu.');}
