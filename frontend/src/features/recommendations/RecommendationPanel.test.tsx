import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { RecommendationPanel } from './RecommendationPanel';
import { postStream } from '../../shared/api/client';
import { pasta } from '../cart/cartFixtures';
const session=vi.hoisted(()=>({expire:vi.fn()}));
vi.mock('../auth/session',()=>({useSession:()=>session,onSessionEnd:()=>()=>{}}));
vi.mock('../preferences/usePreferences',()=>({usePreferences:()=>({data:{version:0},isPending:false,isError:false})}));
vi.mock('../inventory/useInventoryStream',()=>({useCurrentInventory:()=>undefined}));
vi.mock('../cart/usePurchaseAvailability',()=>({usePurchaseAvailability:()=>({canAdd:true,reason:null})}));
vi.mock('../../shared/api/client',async original=>({...await original<typeof import('../../shared/api/client')>(),postStream:vi.fn()}));
const requestId='10000000-0000-4000-8000-000000000001', date='2032-01-01';
const event=(name:string,data:unknown)=>new TextEncoder().encode(`event: ${name}\ndata: ${JSON.stringify(data)}\n\n`);
const meta=()=>event('meta',{requestId,deliveryDate:date});
const card=()=>event('card',{requestId,index:0,dish:pasta,reason:'A mild choice for this meal.'});
function source(){let writer!:ReadableStreamDefaultController<Uint8Array>;const cancel=vi.fn();const body=new ReadableStream<Uint8Array>({start(controller){writer=controller;},cancel});return{body,writer,cancel};}
const props=()=>({date,meal:'Lunch' as const,onMeal:vi.fn(),close:vi.fn(),choose:vi.fn()});
beforeEach(()=>{vi.clearAllMocks();});
it('keeps complete cards after a truncated response and uses the existing customize entry',async()=>{
  const stream=source();vi.mocked(postStream).mockResolvedValue(stream.body);const handlers=props();render(<RecommendationPanel {...handlers}/>);
  fireEvent.change(screen.getByLabelText('What would you like?'),{target:{value:'A mild lunch'}});fireEvent.click(screen.getByRole('button',{name:'Find recommendations'}));
  await act(async()=>{stream.writer.enqueue(meta());stream.writer.enqueue(card());stream.writer.close();});
  expect(await screen.findByText(/Only part of your recommendations arrived/)).toBeVisible();expect(screen.queryByText('Recommendations complete.')).not.toBeInTheDocument();expect(screen.getByText('A mild choice for this meal.')).toBeVisible();fireEvent.click(screen.getByRole('button',{name:'View & customize'}));expect(handlers.choose).toHaveBeenCalledWith(pasta,expect.any(HTMLElement));
  fireEvent.click(screen.getByRole('button',{name:'Return to menu'}));expect(handlers.close).toHaveBeenCalledOnce();
});
it('new input aborts the old request and date or meal changes discard old cards',async()=>{
  let firstResolve!:(value:ReadableStream<Uint8Array>)=>void;vi.mocked(postStream).mockImplementationOnce(()=>new Promise(resolve=>{firstResolve=resolve;}));
  const handlers=props(),view=render(<RecommendationPanel {...handlers}/>);fireEvent.change(screen.getByLabelText('What would you like?'),{target:{value:'First lunch'}});fireEvent.click(screen.getByRole('button',{name:'Find recommendations'}));const signal=vi.mocked(postStream).mock.calls[0][2];
  fireEvent.change(screen.getByLabelText('What would you like?'),{target:{value:'Different lunch'}});expect(signal.aborted).toBe(true);
  const first=source();await act(async()=>firstResolve(first.body));expect(first.cancel).toHaveBeenCalled();expect(screen.queryByText(/Finding dishes/)).not.toBeInTheDocument();
  const second=source();vi.mocked(postStream).mockResolvedValueOnce(second.body);fireEvent.click(screen.getByRole('button',{name:'Find recommendations'}));await act(async()=>{second.writer.enqueue(meta());second.writer.enqueue(card());});expect(screen.getByText('A mild choice for this meal.')).toBeVisible();
  view.rerender(<RecommendationPanel {...handlers} meal="Dinner"/>);expect(vi.mocked(postStream).mock.calls[1][2].aborted).toBe(true);await waitFor(()=>expect(screen.queryByText('A mild choice for this meal.')).not.toBeInTheDocument());view.unmount();
});
it('shows no-match without fake cards and expires a revoked session',async()=>{
  const first=source();vi.mocked(postStream).mockResolvedValueOnce(first.body);render(<RecommendationPanel {...props()}/>);fireEvent.change(screen.getByLabelText('What would you like?'),{target:{value:'A meal'}});fireEvent.click(screen.getByRole('button',{name:'Find recommendations'}));await act(async()=>{first.writer.enqueue(meta());first.writer.enqueue(event('done',{requestId,count:0,outcome:'NO_MATCH'}));first.writer.close();});
  expect(screen.getByText(/No matching dishes/)).toBeVisible();expect(screen.queryByRole('button',{name:'View & customize'})).not.toBeInTheDocument();
  const second=source();vi.mocked(postStream).mockResolvedValueOnce(second.body);fireEvent.click(screen.getByRole('button',{name:'Find recommendations'}));await act(async()=>{second.writer.enqueue(meta());second.writer.enqueue(event('error',{requestId,code:'SESSION_EXPIRED',message:'Your session has expired.',retryable:false,partial:false}));});expect(session.expire).toHaveBeenCalledOnce();
});
