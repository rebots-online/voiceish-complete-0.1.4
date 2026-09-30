import test from 'node:test';
import assert from 'node:assert/strict';
import {toBase64,importKey,encrypt,decrypt,REQUEST_AAD,RESPONSE_AAD} from '../core.js';

const storage = {};
const origins = new Set(['http://192.168.1.10/*']);
globalThis.chrome = {
  storage:{local:{
    get:async keys => structuredClone(Object.fromEntries(keys.filter(k=>k in storage).map(k=>[k,storage[k]]))),
    set:async values => Object.assign(storage,structuredClone(values)),
    remove:async keys => keys.forEach(k=>delete storage[k]),
    setAccessLevel:async()=>{}
  }},
  permissions:{contains:async p=>p.origins.every(o=>origins.has(o)),remove:async p=>p.origins.forEach(o=>origins.delete(o)),getAll:async()=>({origins:[...origins]})},
  runtime:{id:'test-extension',onMessage:{addListener:()=>{}},getURL:p=>p},tabs:{create:async()=>{}}
};
let handle = (await import('../background.js')).handle;
const pairing={v:1,url:'http://192.168.1.10:8765',key:toBase64(crypto.getRandomValues(new Uint8Array(32))),device:'Synthetic phone'};
const key=await importKey(pairing.key);
let simId=1, sends=0, failSend=true, receipts=[];
globalThis.fetch=async (url,options)=>{
  assert.equal(url,`${pairing.url}/v1`);
  assert.equal(options.redirect,'error');
  const request=await decrypt(JSON.parse(options.body),key,REQUEST_AAD);
  let data;
  if(request.action==='status') data={sim:{id:simId,label:'SIM 1',ready:true},permissions:{readSms:true,sendSms:true,phoneState:true}};
  else if(request.action==='messages') data={messages:[],sends:receipts};
  else if(request.action==='send') {
    sends++;
    assert.equal(storage.drafts[request.params.to].attempt.sendId,request.params.sendId,'send identity persisted before network dispatch');
    if(failSend) throw new TypeError('simulated network interruption');
    data={sendId:request.params.sendId,status:'sent'};
  }
  const wire=await encrypt({id:request.id,ok:true,data},key,RESPONSE_AAD);
  return {ok:true,status:200,text:async()=>JSON.stringify(wire)};
};

test('unknown sends survive worker reload, refuse automatic retry, preserve SIM route',async t=>{
  await handle({type:'pair',text:JSON.stringify(pairing)});
  await handle({type:'refresh'});
  assert.equal(storage.pairing.boundSubscriptionId,1);
  const params={sendId:crypto.randomUUID(),to:'+15551234567',body:'Synthetic test only',subscriptionId:1};
  const result=await handle({type:'send',params});
  assert.equal(result.status,'unknown');
  assert.equal(sends,1);
  assert.equal(storage.drafts[params.to].attempt.sendId,params.sendId);
  handle=(await import(`../background.js?reload=${Date.now()}`)).handle;
  await assert.rejects(()=>handle({type:'send',params}),/already has a submitted message/);
  await handle({type:'refresh'});
  assert.equal(sends,1,'refresh never dispatches SMS');
  assert.equal(storage.drafts[params.to].attempt.status,'unknown');
  await handle({type:'draft',to:params.to,text:'accidental overwrite'});
  assert.equal(storage.drafts[params.to].text,params.body);
  simId=2;
  await assert.rejects(()=>handle({type:'refresh'}),/SIM has changed/);
  await assert.rejects(()=>handle({type:'send',params:{...params,sendId:crypto.randomUUID(),subscriptionId:2}}),/does not match/);
  assert.equal(sends,1);
  simId=1;
  receipts=[{id:params.sendId,to:params.to,body:params.body,date:Date.now(),status:'sent',subscriptionId:1}];
  await handle({type:'refresh'});
  assert.equal(storage.drafts[params.to].text,'');
  assert.equal(storage.drafts[params.to].attempt,undefined);
  failSend=false;
  const next=await handle({type:'send',params:{...params,sendId:crypto.randomUUID(),body:'Another synthetic test'}});
  assert.equal(next.status,'sent');
  assert.equal(sends,2);
  assert.equal(storage.drafts[params.to].text,'');
  await handle({type:'forget'});
  assert.deepEqual(storage,{});
  assert.equal(origins.size,0);
});
