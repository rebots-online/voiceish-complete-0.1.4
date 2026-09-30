import test from 'node:test';
import assert from 'node:assert/strict';
import {handle} from '../dist/engine.js';
import {encrypt,decrypt,importKey,REQUEST_AAD,RESPONSE_AAD,toBase64} from '../dist/core.js';

const store=new Map();
globalThis.localStorage={getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v)};
const pairing={v:1,url:'http://192.168.1.10:8765',device:'Synthetic phone',key:toBase64(crypto.getRandomValues(new Uint8Array(32)))};
const key=await importKey(pairing.key);
let sim=7,failSend=false;
const sends=[];
globalThis.fetch=async(url,options)=>{
  assert.equal(url,'http://192.168.1.10:8765/v1');
  const req=await decrypt(JSON.parse(options.body),key,REQUEST_AAD);
  let data;
  if(req.action==='status')data={sim:{id:sim,label:'Synthetic SIM',ready:true},permissions:{readSms:true,sendSms:true},device:'Synthetic phone'};
  if(req.action==='messages')data={messages:[{id:'sms:1',address:'+16135550142',body:'Test only',type:'in',status:'received',date:Date.now(),subscriptionId:sim}],sends};
  if(req.action==='send'){
    const persisted=JSON.parse(store.get('voiceish-direct-v1'));
    assert.equal(persisted.drafts[req.params.to].attempt.sendId,req.params.sendId);
    if(failSend)throw new TypeError('Synthetic interrupted network');
    sends.push({id:req.params.sendId,to:req.params.to,body:req.params.body,date:Date.now(),status:'sent',subscriptionId:sim});
    data={sendId:req.params.sendId,status:'sending'};
  }
  return new Response(JSON.stringify(await encrypt({id:req.id,ok:true,data},key,RESPONSE_AAD)),{status:200});
};
test('pair, encrypted inbox, durable send, receipt, ambiguous-send lock and SIM pinning',async()=>{
  const state=await handle({type:'pair',text:JSON.stringify(pairing)});
  assert.equal(state.pairing.device,'Synthetic phone');
  assert.equal(state.pairing.key,undefined);
  const inbox=await handle({type:'refresh'});
  assert.equal(inbox.messages[0].body,'Test only');
  const params={sendId:crypto.randomUUID(),to:'+16135550142',body:'A synthetic message — café',subscriptionId:7};
  await handle({type:'draft',to:params.to,text:params.body});
  const pending=await handle({type:'send',params});
  assert.equal(pending.status,'sending');
  await assert.rejects(handle({type:'send',params:{...params,sendId:crypto.randomUUID()}}),/submitted message/);
  const settled=await handle({type:'refresh'});
  assert.equal(settled.drafts[params.to].text,'');
  assert.equal(settled.drafts[params.to].attempt,undefined);
  failSend=true;
  const uncertain=await handle({type:'send',params:{...params,sendId:crypto.randomUUID()}});
  assert.equal(uncertain.status,'unknown');
  await assert.rejects(handle({type:'send',params:{...params,sendId:crypto.randomUUID()}}),/submitted message/);
  sim=8;
  await assert.rejects(handle({type:'refresh'}),/SIM has changed/);
  const forgotten=await handle({type:'forget'});
  assert.equal(forgotten.pairing,null);
  assert.deepEqual(forgotten.drafts,{});
});
