import http from 'node:http';
import {createCipheriv,createDecipheriv,randomBytes} from 'node:crypto';
const key=Buffer.alloc(32,42);
const since=Date.now();
const messages=[
{id:'sms:1',address:'+15550100001',body:'Synthetic test conversation. Can you text from the computer?',date:since-90000,type:'in',status:'received',subscriptionId:1},
{id:'sms:2',address:'+15550100001',body:'Testing the familiar Voiceish conversation layout.',date:since-60000,type:'out',status:'sent',subscriptionId:1},
{id:'sms:3',address:'+15550100002',body:'A second synthetic conversation for search and navigation.',date:since-30000,type:'in',status:'received',subscriptionId:1}];
const sends=[];
function decrypt(e){const c=Buffer.from(e.data,'base64');const d=createDecipheriv('aes-256-gcm',key,Buffer.from(e.iv,'base64'));d.setAAD(Buffer.from('voiceish-sim:v1:request'));d.setAuthTag(c.subarray(-16));return JSON.parse(Buffer.concat([d.update(c.subarray(0,-16)),d.final()]));}
function encrypt(v){const iv=randomBytes(12);const c=createCipheriv('aes-256-gcm',key,iv);c.setAAD(Buffer.from('voiceish-sim:v1:response'));const data=Buffer.concat([c.update(JSON.stringify(v)),c.final(),c.getAuthTag()]);return {v:1,iv:iv.toString('base64'),data:data.toString('base64')};}
export const testServer=http.createServer(async(req,res)=>{try{
const url=new URL(req.url,'http://localhost');
if(url.pathname==='/v1'){
let body='';for await(const chunk of req)body+=chunk;
const q=decrypt(JSON.parse(body));let data;
if(q.action==='status')data={version:'0.1.0',device:'Synthetic test phone',sim:{id:1,label:'Primary SMS SIM · test',ready:true},permissions:{readSms:true,sendSms:true,phoneState:true}};
else if(q.action==='messages')data={messages:[...messages].reverse(),sends};
else if(q.action==='send'){
let receipt=sends.find(s=>s.id===q.params.sendId);
if(!receipt){receipt={id:q.params.sendId,...q.params,date:Date.now(),status:'sent'};sends.push(receipt);messages.push({id:'sms:'+messages.length+10,address:receipt.to,body:receipt.body,date:receipt.date,type:'out',status:'sent',subscriptionId:1});}
data={sendId:q.params.sendId,status:'sent'};
} else throw Error('Bad action');
res.setHeader('Content-Type','application/json');res.end(JSON.stringify(encrypt({id:q.id,ok:true,data})));return;
}
res.statusCode=404;res.end('Synthetic SMS API only');
}catch(e){res.statusCode=500;res.end(e.message);}}).listen(8066,'127.0.0.1',()=>console.log('Synthetic UI test at http://127.0.0.1:8066; no real phone or SMS transport.'));
