import test from 'node:test';
import assert from 'node:assert/strict';
import {endpoint, hostPermission, parsePairing, fromBase64, toBase64, importKey, encrypt, decrypt, REQUEST_AAD, RESPONSE_AAD, validateResponse, reconcileMessages, validateSend} from '../core.js';

const keyText = toBase64(crypto.getRandomValues(new Uint8Array(32)));
test('pairing only accepts literal private IPv4 HTTP endpoints', () => {
  for (const url of ['http://10.0.0.2:8765', 'http://172.16.0.2:8765', 'http://172.31.255.254:8765', 'http://192.168.0.7:8765/']) assert.ok(endpoint(url));
  for (const url of ['http://172.32.0.1:8765', 'http://8.8.8.8', 'https://192.168.0.1:8765', 'http://192.168.0.1@evil.test', 'http://3232235521', 'http://0300.0250.0.1', 'http://192.168.0.1/v1', 'http://192.168.0.1?q=1', 'http://192.168.0.1/#x', 'http://localhost:8765', 'http://192.168.0.1.evil.test']) assert.throws(() => endpoint(url));
  assert.equal(hostPermission('http://192.168.0.7:8765'), 'http://192.168.0.7/*');
  assert.equal(parsePairing(JSON.stringify({v:1,url:'http://10.0.0.2:8765',key:keyText})).device, 'Phone');
  assert.throws(() => parsePairing(JSON.stringify({v:1,url:'http://10.0.0.2',key:'abcd'})));
});
test('AES-GCM authenticates direction, contents and random IV; response ID is bound', async () => {
  const key = await importKey(keyText);
  const payload = {id:crypto.randomUUID(),body:'Hello 🌿\n你好'};
  const first = await encrypt(payload, key);
  const second = await encrypt(payload, key);
  assert.notEqual(first.iv, second.iv);
  assert.deepEqual(await decrypt(first, key, REQUEST_AAD), payload);
  await assert.rejects(() => decrypt(first, key, RESPONSE_AAD));
  const tampered = fromBase64(first.data); tampered[0] ^= 1;
  await assert.rejects(() => decrypt({...first,data:toBase64(tampered)}, key, REQUEST_AAD));
  assert.throws(() => validateResponse({id:'wrong',ok:true,data:{}}, payload.id));
  assert.deepEqual(validateResponse({id:payload.id,ok:true,data:{ready:true}}, payload.id), {ready:true});
});
test('large inbox decrypts without regex stack overflow', async () => {
  const key = await importKey(keyText);
  const payload = {messages:Array.from({length:300}, (_,i) => ({id:i,body:'a'.repeat(1800)}))};
  const envelope = await encrypt(payload,key,RESPONSE_AAD);
  assert.ok(envelope.data.length > 300000);
  assert.deepEqual(await decrypt(envelope,key),payload);
  for (const value of ['AA=A','A===','AB==','a','!!!!']) assert.throws(() => fromBase64(value));
});
test('receipt reconciliation is conservative, one-to-one, and never hides unknown', () => {
  const message = {id:'sms:1',address:'+15551234567',body:'same',date:100000,type:'out',status:'sent',subscriptionId:1};
  const receipt = {id:'send-1',to:'+15551234567',body:'same',date:100000,status:'sent',subscriptionId:1};
  assert.equal(reconcileMessages([message],[receipt]).length,1);
  assert.equal(reconcileMessages([message],[{...receipt,status:'unknown'}]).length,2);
  assert.equal(reconcileMessages([message],[{...receipt,subscriptionId:2}]).length,2);
  assert.equal(reconcileMessages([message],[{...receipt,to:'5551234567'}]).length,2);
  assert.equal(reconcileMessages([message,{...message,id:'sms:2'}],[receipt]).length,3);
  assert.equal(reconcileMessages([],[receipt,{...receipt,id:'send-2'}]).length,2);
  assert.equal(reconcileMessages([message],[receipt,{...receipt,id:'send-2'}]).length,2);
});
test('send validates actual SMS route, length and UUID before dispatch', () => {
  const payload={sendId:crypto.randomUUID(),to:'+1 (555) 123-4567',body:'Hello',subscriptionId:1};
  assert.equal(validateSend(payload).to,'+15551234567');
  for (const patch of [{sendId:'x'},{to:'SENDER'},{to:'123,456'},{body:' '},{body:'a'.repeat(2001)},{subscriptionId:-1},{subscriptionId:null}]) assert.throws(() => validateSend({...payload,...patch}));
});
