// Voice-ish SIM. Original Voice-ish interface © 2026 Robin, MIT licensed.
export const REQUEST_AAD = 'voiceish-sim:v1:request';
export const RESPONSE_AAD = 'voiceish-sim:v1:response';
const utf8 = new TextEncoder();
const decode = new TextDecoder('utf-8', {fatal: true});

export function endpoint(value) {
  let url;
  try { url = new URL(value); } catch { throw new Error('Enter the phone’s local HTTP address, such as http://192.168.1.10:8765.'); }
  // Require canonical dotted decimal, preventing alternate numeric host spellings.
  const suppliedHost = String(value).match(/^http:\/\/([^/:?#]+)(?::\d+)?\/?$/)?.[1];
  const parts = (suppliedHost || '').split('.');
  const valid = parts.length === 4 && parts.every(p => /^(0|[1-9]\d{0,2})$/.test(p) && Number(p) <= 255);
  const [a,b] = parts.map(Number);
  const local = a === 10 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168);
  if (!valid || !local || suppliedHost !== url.hostname || url.protocol !== 'http:' || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error('Use the phone’s private IPv4 address (10.x.x.x, 172.16–31.x.x, or 192.168.x.x), with http:// and its port.');
  }
  return url.origin;
}

export function hostPermission(url) { return `${new URL(endpoint(url)).origin.replace(/:\d+$/, '')}/*`; }

export function fromBase64(value, maxBytes = 65536) {
  if (typeof value !== 'string' || value.length > Math.ceil(maxBytes / 3) * 4 || value.length % 4 || /[^A-Za-z0-9+/=]/.test(value) || /=/.test(value.slice(0, -2)) || (value.at(-2) === '=' && value.at(-1) !== '=')) throw new Error('Invalid encoded data.');
  const bytes = Uint8Array.from(atob(value), c => c.charCodeAt(0));
  if (toBase64(bytes) !== value) throw new Error('Noncanonical encoded data.');
  if (bytes.length > maxBytes) throw new Error('Response is too large.');
  return bytes;
}
export function toBase64(bytes) { return btoa(Array.from(bytes, b => String.fromCharCode(b)).join('')); }

export function parsePairing(text) {
  let value;
  try { value = JSON.parse(text); } catch { throw new Error('Paste the complete pairing text from Voice-ish SIM on your phone.'); }
  if (!value || value.v !== 1 || typeof value.key !== 'string' || fromBase64(value.key, 32).length !== 32) throw new Error('This pairing text needs a version 1, 32-byte phone key.');
  return {v: 1, url: endpoint(value.url), key: value.key, device: String(value.device || 'Phone').slice(0, 100)};
}
export async function importKey(base64) {
  const bytes = fromBase64(base64, 32);
  if (bytes.length !== 32) throw new Error('The pairing key must contain 32 bytes.');
  return crypto.subtle.importKey('raw', bytes, 'AES-GCM', false, ['encrypt', 'decrypt']);
}
export async function encrypt(value, key, aad = REQUEST_AAD) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const data = await crypto.subtle.encrypt({name: 'AES-GCM', iv, additionalData: utf8.encode(aad), tagLength: 128}, key, utf8.encode(JSON.stringify(value)));
  return {v: 1, iv: toBase64(iv), data: toBase64(new Uint8Array(data))};
}
export async function decrypt(envelope, key, aad = RESPONSE_AAD) {
  if (!envelope || envelope.v !== 1) throw new Error('Unsupported phone response.');
  const iv = fromBase64(envelope.iv, 12);
  const ciphertext = fromBase64(envelope.data, 2 * 1024 * 1024);
  if (iv.length !== 12 || ciphertext.length < 16) throw new Error('Invalid encrypted phone response.');
  const bytes = await crypto.subtle.decrypt({name: 'AES-GCM', iv, additionalData: utf8.encode(aad), tagLength: 128}, key, ciphertext);
  return JSON.parse(decode.decode(bytes));
}
export function validateResponse(response, id) {
  if (!response || response.id !== id || typeof response.ok !== 'boolean') throw new Error('The phone response did not match this request.');
  if (!response.ok) {
    const error = new Error(String(response.error?.message || 'The phone could not complete this action.'));
    error.code = String(response.error?.code || 'PHONE_ERROR');
    throw error;
  }
  return response.data;
}
export async function rpc(pairing, action, params = {}, fetcher = fetch, timeoutMs = 12000) {
  const id = crypto.randomUUID();
  const key = await importKey(pairing.key);
  const request = await encrypt({id, ts: Date.now(), action, params}, key);
  const abort = new AbortController();
  const timeout = setTimeout(() => abort.abort(), timeoutMs);
  try {
    const response = await fetcher(`${endpoint(pairing.url)}/v1`, {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(request),
      signal: abort.signal, credentials: 'omit', cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer'
    });
    if (!response.ok) throw new Error(response.status === 401 ? 'Phone pairing was rejected. Copy new pairing text from the phone.' : `Phone returned HTTP ${response.status}.`);
    const text = await response.text();
    if (text.length > 3 * 1024 * 1024) throw new Error('Phone response exceeded the message limit.');
    return validateResponse(await decrypt(JSON.parse(text), key), id);
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('Phone timed out. Check that its bridge is running and both devices are on the same network.');
    if (error instanceof TypeError) throw new Error('Phone is offline or unreachable. Check Wi-Fi, bridge, and Chrome’s local-network permission.');
    if (error.name === 'OperationError') throw new Error('Could not authenticate the phone response. Check the pairing key.');
    throw error;
  } finally { clearTimeout(timeout); }
}

export function addressKey(value) { return String(value || '').replace(/[\s().-]/g, ''); }
export function destination(value) {
  const normalized = addressKey(value);
  if (!/^\+?[0-9]{3,20}$/.test(normalized)) throw new Error('Enter one phone number, including country code when needed.');
  return normalized;
}
export function validateSend({sendId, to, body, subscriptionId}) {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(sendId)) throw new Error('Invalid send identity.');
  if (typeof body !== 'string' || !body.trim() || body.length > 2000) throw new Error('Write a message of 1–2000 characters.');
  if (!Number.isInteger(subscriptionId) || subscriptionId < 0) throw new Error('The phone has no active primary SMS SIM.');
  return {sendId, to: destination(to), body, subscriptionId};
}

// Conservative, one-to-one matching: never hide an uncertain/failed receipt.
// Numbers match exactly after punctuation removal; no guessed country codes.
export function reconcileMessages(messages = [], sends = []) {
  const result = messages.filter(m => m && typeof m.body === 'string' && Number.isFinite(m.date)).map(m => ({...m}));
  const providerCount = result.length;
  const used = new Set();
  for (const send of sends) {
    if (!send || typeof send.body !== 'string' || !Number.isFinite(send.date)) continue;
    const matches = send.status === 'sent' ? result.slice(0, providerCount).map((m, i) => ({m, i})).filter(({m, i}) => !used.has(i) && m.type === 'out' && m.status === 'sent' && m.subscriptionId === send.subscriptionId && addressKey(m.address) === addressKey(send.to) && m.body === send.body && Math.abs(m.date - send.date) <= 90000) : [];
    if (matches.length === 1) { used.add(matches[0].i); result[matches[0].i].sendId = send.id; }
    else result.push({id: `send:${send.id}`, sendId: send.id, address: send.to, body: send.body, date: send.date, type: 'out', status: send.status, subscriptionId: send.subscriptionId, error: send.error});
  }
  return result.sort((a, b) => a.date - b.date);
}
