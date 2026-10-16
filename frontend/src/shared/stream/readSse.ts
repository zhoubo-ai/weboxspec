export type SseEvent = { event: string; data: string };
/** Bounded UTF-8 SSE decoder. HTTP chunks and code points need not align with lines or events. */
export async function* readSse(body: ReadableStream<Uint8Array>, signal?: AbortSignal): AsyncGenerator<SseEvent> {
  const reader=body.getReader(), decoder=new TextDecoder('utf-8',{fatal:true});let pending='', event='message', data:string[]=[], frameBytes=0, total=0, skipLf=false;
  const cancelled=() => { void reader.cancel().catch(() => {}); };signal?.addEventListener('abort',cancelled,{once:true});
  function line(value:string): SseEvent | null {
    if (!value) { const result=data.length?{event,data:data.join('\n')}:null;event='message';data=[];frameBytes=0;return result; }
    if(value.startsWith(':'))return null;const colon=value.indexOf(':'),field=colon<0?value:value.slice(0,colon);let content=colon<0?'':value.slice(colon+1);if(content.startsWith(' '))content=content.slice(1);
    if(field==='event'){if(content.length>64)throw new Error('The recommendation event is invalid.');event=content || 'message';}
    if(field==='data'){frameBytes+=content.length;if(frameBytes>4_194_304)throw new Error('The recommendation event is too large.');data.push(content);}return null;
  }
  try {
    while(true) {
      signal?.throwIfAborted();const part=await reader.read();signal?.throwIfAborted();total+=part.value?.byteLength??0;if(total>16_777_216)throw new Error('The recommendation stream is too large.');
      const decoded=decoder.decode(part.value,{stream:!part.done});
      for(const char of decoded) {
        if(skipLf){skipLf=false;if(char==='\n')continue;}
        if(char==='\r' || char==='\n'){const result=line(pending);pending='';skipLf=char==='\r';if(result)yield result;}
        else {pending+=char;if(pending.length>4_194_304)throw new Error('The recommendation event is too large.');}
      }
      if(part.done)break;
    }
    if(data.length || (pending && !pending.startsWith(':')))throw new Error('The recommendation stream ended before an event was complete.');
  } finally {signal?.removeEventListener('abort',cancelled);await reader.cancel().catch(() => {});reader.releaseLock();}
}
