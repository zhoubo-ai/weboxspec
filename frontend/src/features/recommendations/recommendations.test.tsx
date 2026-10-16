import { expect, it } from 'vitest';
import { readSse } from '../../shared/stream/readSse';
import { recommendationEvent, beginRecommendations, recommendationEof } from './recommendationState';
import { pasta } from '../cart/cartFixtures';
const requestId='10000000-0000-4000-8000-000000000001', date='2032-01-01';
const frame=(event: string, data: unknown) => ({ event, data: JSON.stringify(data) });
function bytes(text: string, chunkSize=1) { const encoded=new TextEncoder().encode(text);return new ReadableStream<Uint8Array>({ start(controller) { for (let at=0;at<encoded.length;at+=chunkSize) controller.enqueue(encoded.slice(at,at+chunkSize));controller.close(); } }); }
it('parses UTF-8 one byte at a time, CRLF, comments and multiple data lines', async () => {
  const events=[];for await (const event of readSse(bytes(': keep-alive\r\nevent: card\r\ndata: {"reason":\r\ndata: "A café choice 🍜"}\r\n\r\n'))) events.push(event);
  expect(events).toEqual([{ event: 'card', data: '{"reason":\n"A café choice 🍜"}' }]);
});
it('rejects malformed UTF-8, oversized frames and incomplete final events', async () => {
  for (const body of [bytes('event: card\ndata: unfinished'), bytes(`data: ${'a'.repeat(4_194_305)}\n\n`,1024), new ReadableStream<Uint8Array>({ start(c) { c.enqueue(new Uint8Array([0xff]));c.close(); } })]) {
    await expect(async () => { for await (const _event of readSse(body)) { /* Must fail before treating these bytes as a valid event. */ } }).rejects.toThrow();
  }
});
it('accepts one meta, consecutive cards and one matching terminal result', () => {
  let state=beginRecommendations(date,1);state=recommendationEvent(state,frame('meta',{requestId,deliveryDate:date}),1);state=recommendationEvent(state,frame('card',{requestId,index:0,dish:pasta,reason:'This mild dish offers variety.'}),1);
  expect(state.cards).toHaveLength(1);state=recommendationEvent(state,frame('done',{requestId,count:1,outcome:'COMPLETE'}),1);expect(state.status).toBe('complete');expect(recommendationEof(state,1)).toBe(state);
});
it('ignores another request or connection and treats a missing terminal event as partial failure', () => {
  let state=recommendationEvent(beginRecommendations(date,5),frame('meta',{requestId,deliveryDate:date}),5);
  const card=frame('card',{requestId,index:0,dish:pasta,reason:'This mild dish offers variety.'});expect(recommendationEvent(state,card,4)).toBe(state);
  expect(recommendationEvent(state,frame('card',{requestId:'10000000-0000-4000-8000-000000000099',index:0,dish:pasta,reason:'Wrong request.'}),5)).toBe(state);
  state=recommendationEvent(state,card,5);state=recommendationEof(state,5);expect(state.status).toBe('error');expect(state.partial).toBe(true);expect(state.cards).toHaveLength(1);
});
it('does not label invalid count, index, duplicate terminal or provider errors complete', () => {
  const meta=() => recommendationEvent(beginRecommendations(date,1),frame('meta',{requestId,deliveryDate:date}),1);
  for(const event of [frame('done',{requestId,count:1,outcome:'COMPLETE'}),frame('card',{requestId,index:1,dish:pasta,reason:'A mild choice.'}),frame('error',{requestId,code:'AI_TIMEOUT',message:'Recommendations took too long.',retryable:true,partial:false})])expect(recommendationEvent(meta(),event,1).status).toBe('error');
  const done=recommendationEvent(meta(),frame('done',{requestId,count:0,outcome:'NO_MATCH'}),1);expect(done.status).toBe('no-match');expect(recommendationEvent(done,frame('done',{requestId,count:0,outcome:'NO_MATCH'}),1).status).toBe('error');
});
