// Available only on the explicitly declared Voiceish Site origin.
const allowed = new Set(['state','refresh','draft','send','compose-again','forget']);
const origins = new Set(['https://voipmsms.robinc.chatgpt.site', 'https://voiceish-sim.robinc.chatgpt.site']);
const origin = location.origin;
window.addEventListener('message', event => {
  if (!origins.has(origin) || event.source !== window || event.origin !== origin) return;
  const request = event.data;
  if (request?.channel !== 'voiceish-web-request' || typeof request.id !== 'string' || request.id.length > 80 || !allowed.has(request.message?.type)) return;
  chrome.runtime.sendMessage(request.message).then(response => {
    window.postMessage({channel:'voiceish-extension-response',id:request.id,...response},origin);
  }, error => {
    window.postMessage({channel:'voiceish-extension-response',id:request.id,ok:false,error:error.message||'Reload this page after updating the extension.'},origin);
  });
});
