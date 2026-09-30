const platform = {
 storage: {local: {
  async get(keys) {const s=JSON.parse(localStorage.getItem('voiceish-direct-v1')||'{}');return Object.fromEntries(keys.map(k=>[k,s[k]]));},
  async set(patch) {const s=JSON.parse(localStorage.getItem('voiceish-direct-v1')||'{}');localStorage.setItem('voiceish-direct-v1',JSON.stringify({...s,...patch}));},
  async remove(keys) {const s=JSON.parse(localStorage.getItem('voiceish-direct-v1')||'{}');keys.forEach(k=>delete s[k]);localStorage.setItem('voiceish-direct-v1',JSON.stringify(s));}
 }},
 permissions: {async contains(){return true},async getAll(){return {origins:[]}},async remove(){}},
 tabs:{async create(){}}
};
import {parsePairing, endpoint, hostPermission, rpc, destination, validateSend} from './core.js';

let mutationTail = Promise.resolve();
function mutate(fn) {
  const next = mutationTail.then(fn);
  mutationTail = next.catch(() => {});
  return next;
}
async function readState() {
  const state = await platform.storage.local.get(['pairing', 'drafts']);
  return {pairing: state.pairing || null, drafts: state.drafts || {}};
}
function publicState(state) {
  return {pairing: state.pairing ? {url: state.pairing.url, device: state.pairing.device} : null, drafts: state.drafts};
}
async function requirePairing() {
  const {pairing} = await readState();
  if (!pairing) throw new Error('Pair this browser with your phone first.');
  if (!await platform.permissions.contains({origins: [hostPermission(pairing.url)]})) throw new Error('Access to the phone’s address is missing. Reconnect in Settings.');
  return pairing;
}
function samePairing(a, b) { return a && b && a.key === b.key && a.url === b.url; }

async function updateAttempt(pairing, address, id, patch) {
  return mutate(async () => {
    const state = await readState();
    if (!samePairing(state.pairing, pairing)) return;
    const draft = state.drafts[address];
    if (draft?.attempt?.sendId !== id) return;
    if (patch.status === 'sent') {
      // Receipt is final. Inbox remains memory-only; no permanent sent-message cache.
      state.drafts[address] = {text: '', updated: Date.now()};
    } else {
      draft.attempt = {...draft.attempt, ...patch};
    }
    await platform.storage.local.set({drafts: state.drafts});
  });
}

export async function handle(message) {
  switch (message?.type) {
    case 'state': return publicState(await readState());
    case 'pair': {
      const pairing = parsePairing(message.text);
      if (!await platform.permissions.contains({origins: [hostPermission(pairing.url)]})) throw new Error('Allow Chrome to connect to this phone’s local address.');
      return mutate(async () => {
        const old = await readState();
        if (old.pairing) throw new Error('Forget the current phone before pairing another.');
        await platform.storage.local.set({pairing, drafts: {}});
        return publicState({pairing, drafts: {}});
      });
    }
    case 'endpoint': {
      const url = endpoint(message.url);
      if (!await platform.permissions.contains({origins: [hostPermission(url)]})) throw new Error('Allow Chrome to connect to this phone address.');
      return mutate(async () => {
        const state = await readState();
        if (!state.pairing) throw new Error('Pair a phone first.');
        const previousOrigin = hostPermission(state.pairing.url);
        state.pairing.url = url;
        await platform.storage.local.set({pairing: state.pairing});
        if (previousOrigin !== hostPermission(url)) await platform.permissions.remove({origins: [previousOrigin]});
        return publicState(state);
      });
    }
    case 'forget': return mutate(async () => {
      // Remove every granted phone host, including a grant interrupted before pairing.
      const permissions = await platform.permissions.getAll();
      await platform.storage.local.remove(['pairing', 'drafts']);
      if (permissions.origins?.length) await platform.permissions.remove({origins: permissions.origins});
      return {pairing: null, drafts: {}};
    });
    case 'draft': return mutate(async () => {
      const state = await readState();
      if (!state.pairing) throw new Error('Pair a phone first.');
      const to = destination(message.to);
      if (state.drafts[to]?.attempt) return publicState(state);
      if (typeof message.text !== 'string' || message.text.length > 2000) throw new Error('Draft is too long.');
      state.drafts[to] = {text: message.text, updated: Date.now()};
      await platform.storage.local.set({drafts: state.drafts});
      return publicState(state);
    });
    case 'compose-again': return mutate(async () => {
      const state = await readState();
      const to = destination(message.to);
      const current = state.drafts[to];
      if (current?.attempt && !['failed', 'unknown'].includes(current.attempt.status)) throw new Error('This message is still being sent. Refresh its status first.');
      state.drafts[to] = {text: current?.attempt?.body || current?.text || '', updated: Date.now()};
      await platform.storage.local.set({drafts: state.drafts});
      return publicState(state);
    });
    case 'refresh': {
      const pairing = await requirePairing();
      const status = await rpc(pairing, 'status');
      await mutate(async () => {
        const state = await readState();
        if (!samePairing(state.pairing, pairing)) throw new Error('Phone connection changed. Refresh again.');
        const simId = status.sim?.id;
        if (Number.isInteger(state.pairing.boundSubscriptionId) && simId !== state.pairing.boundSubscriptionId) {
          throw new Error('The phone’s primary SMS SIM has changed. Forget this phone in Settings, then pair again to explicitly select the new SIM. Existing drafts will be erased.');
        }
        if (!Number.isInteger(state.pairing.boundSubscriptionId) && status.sim?.ready && Number.isInteger(simId) && simId >= 0) {
          state.pairing.boundSubscriptionId = simId;
          await platform.storage.local.set({pairing: state.pairing});
        }
      });
      const data = status.permissions?.readSms ? await rpc(pairing, 'messages', {limit: 300}) : {messages: [], sends: []};
      const receipts = Array.isArray(data.sends) ? data.sends : [];
      // Only settle by exact durable ID. Provider matching is presentation-only.
      await mutate(async () => {
        const state = await readState();
        if (!samePairing(state.pairing, pairing)) return;
        let changed = false;
        for (const draft of Object.values(state.drafts)) {
          if (!draft.attempt) continue;
          const receipt = receipts.find(s => s.id === draft.attempt.sendId);
          if (receipt && ['sent', 'sending', 'failed', 'unknown'].includes(receipt.status)) {
            if (receipt.status === 'sent') { draft.text = ''; delete draft.attempt; }
            else draft.attempt = {...draft.attempt, status: receipt.status, error: receipt.error || ''};
            changed = true;
          } else if (draft.attempt.status === 'sending' && Date.now() - draft.attempt.date > 90000) {
            draft.attempt.status = 'unknown';
            draft.attempt.error = 'No final receipt is available. Check the phone’s SMS inbox before composing again.';
            changed = true;
          }
        }
        if (changed) await platform.storage.local.set({drafts: state.drafts});
      });
      const latest = await readState();
      if (!samePairing(latest.pairing, pairing)) throw new Error('Phone connection changed. Refresh again.');
      return {status, messages: Array.isArray(data.messages) ? data.messages : [], sends: receipts, ...publicState(latest)};
    }
    case 'send': {
      const params = validateSend(message.params || {});
      const pairing = await requirePairing();
      if (!Number.isInteger(pairing.boundSubscriptionId) || params.subscriptionId !== pairing.boundSubscriptionId) throw new Error('The selected SIM does not match this pairing. Refresh the phone connection before sending.');
      // Commit the send ID before any network operation can dispatch an SMS.
      await mutate(async () => {
        const state = await readState();
        if (!samePairing(state.pairing, pairing)) throw new Error('Phone connection changed.');
        if (state.drafts[params.to]?.attempt) throw new Error('This conversation already has a submitted message. Check its status before composing again.');
        state.drafts[params.to] = {text: params.body, updated: Date.now(), attempt: {...params, date: Date.now(), status: 'sending'}};
        await platform.storage.local.set({drafts: state.drafts});
      });
      try {
        const result = await rpc(pairing, 'send', params);
        if (result?.sendId !== params.sendId || !['sending', 'sent', 'failed', 'unknown'].includes(result.status)) throw new Error('The phone did not return a matching send receipt.');
        await updateAttempt(pairing, params.to, params.sendId, {status: result.status, error: result.error || ''});
        return {...result, ...publicState(await readState())};
      } catch (error) {
        // An authenticated rejection may still be downstream of a persisted attempt;
        // conservatively require reconciliation instead of assuming no SMS was sent.
        await updateAttempt(pairing, params.to, params.sendId, {status: 'unknown', error: error.message});
        return {sendId: params.sendId, status: 'unknown', error: error.message, ...publicState(await readState())};
      }
    }
    case 'open-tab': await platform.tabs.create({url: platform.runtime.getURL('popup.html?mode=tab')}); return {};
    default: throw new Error('Unsupported extension action.');
  }
}

