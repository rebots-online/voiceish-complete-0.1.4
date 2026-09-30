import {parsePairing, endpoint, hostPermission, destination, addressKey, reconcileMessages} from './core.js';

const $ = id => document.getElementById(id);
const state = {pairing: null, drafts: {}, status: null, messages: [], sends: [], selected: '', online: false, refreshing: false, submitting: false};
let renderedAddress = '', renderedAttemptId = '', messageSignature = '', modalReturnFocus = null;
if (new URLSearchParams(location.search).get('mode') === 'tab') document.documentElement.classList.add('full-page');

function show(id, visible = true) { $(id).classList.toggle('hidden', !visible); }
function notice(id, message = '') { $(id).textContent = message; show(id, !!message); }
async function call(type, values = {}) {
  const result = await chrome.runtime.sendMessage({type, ...values});
  if (!result?.ok) throw new Error(result?.error || 'The extension connection was interrupted. Reopen it to check the current state.');
  return result.data;
}
function absorb(data) {
  if ('pairing' in data) state.pairing = data.pairing;
  if (data.drafts) state.drafts = data.drafts;
}
function resetMemory() {
  Object.assign(state, {pairing: null, drafts: {}, status: null, messages: [], sends: [], selected: '', online: false});
  renderedAddress = ''; renderedAttemptId = ''; messageSignature = '';
  $('messageInput').value = ''; $('pairingInput').value = '';
  $('threadList').replaceChildren(); $('messageList').replaceChildren(); $('contactHeading').textContent = '';
}
function render() {
  show('loadingView', false);
  show('setupView', !state.pairing);
  show('messengerView', !!state.pairing);
  if (!state.pairing) return;
  const onlineText = state.online ? 'Phone online' : 'Phone offline';
  $('connectionBadge').textContent = onlineText;
  $('connectionBadge').classList.toggle('online', state.online);
  const ready = state.online && state.status?.sim?.ready && state.status?.permissions?.sendSms && state.status?.permissions?.phoneState;
  $('simHeading').textContent = state.status?.sim?.label || 'Primary SMS SIM';
  $('newRouteLabel').textContent = `Sends from ${state.status?.sim?.label || 'your phone’s primary SMS SIM'}.`;
  const draft = state.drafts[state.selected];
  const attempt = draft?.attempt;
  let canReply = true;
  try { destination(state.selected); } catch { canReply = false; }
  show('emptyConversation', !state.selected);
  show('conversationView', !!state.selected);
  $('contactHeading').textContent = state.selected;
  $('messageInput').disabled = !!attempt || state.submitting || !canReply;
  if (state.selected !== renderedAddress || (renderedAttemptId && !attempt) || attempt) {
    $('messageInput').value = attempt?.body || draft?.text || '';
  }
  renderedAddress = state.selected;
  renderedAttemptId = attempt?.sendId || '';
  $('sendButton').disabled = !ready || !canReply || !state.selected || !!attempt || state.submitting || !$('messageInput').value.trim();
  $('characterCount').textContent = `${$('messageInput').value.length} / 2000`;
  $('messageInput').style.height = 'auto';
  $('messageInput').style.height = `${Math.min(96, $('messageInput').scrollHeight)}px`;
  if (!canReply && state.selected) notice('attemptNotice', 'This sender name cannot receive SMS replies. Start a new message to a phone number.');
  else if (attempt?.status === 'unknown') notice('attemptNotice', 'Delivery is uncertain. Check your phone’s SMS inbox before composing again. This message will not be resent automatically.');
  else if (attempt?.status === 'failed') notice('attemptNotice', `Phone reported a send failure${attempt.error ? `: ${attempt.error}` : '.'} Some multipart text may have sent. Check your phone before composing again.`);
  else if (attempt) notice('attemptNotice', 'Sending through your phone… Waiting for its SMS result.');
  else notice('attemptNotice');
  show('composeAgainButton', !!attempt && ['failed', 'unknown'].includes(attempt.status));
  renderThreads();
  renderMessages();
}
function combinedMessages() {
  const sends = [...state.sends];
  for (const draft of Object.values(state.drafts)) {
    const attempt = draft.attempt;
    if (attempt && !sends.some(s => s.id === attempt.sendId)) sends.push({id: attempt.sendId, to: attempt.to, body: attempt.body, date: attempt.date, subscriptionId: attempt.subscriptionId, status: attempt.status, error: attempt.error});
  }
  return reconcileMessages(state.messages, sends);
}
function threads() {
  const rows = new Map();
  const messages = combinedMessages();
  for (const message of messages) {
    const key = addressKey(message.address);
    if (!key) continue;
    rows.set(key, {key, latest: message, date: message.date});
  }
  for (const [key, draft] of Object.entries(state.drafts)) {
    if (!rows.has(key) && (draft.text || draft.attempt)) rows.set(key, {key, latest: null, date: draft.updated || 0});
  }
  if (state.selected && !rows.has(state.selected)) rows.set(state.selected, {key: state.selected, latest: null, date: Date.now()});
  const search = $('searchInput').value.trim().toLocaleLowerCase();
  return [...rows.values()].filter(row => !search || row.key.toLocaleLowerCase().includes(search) || messages.some(m => addressKey(m.address) === row.key && m.body.toLocaleLowerCase().includes(search))).sort((a,b) => b.date - a.date);
}
function element(tag, className, text) {
  const item = document.createElement(tag);
  if (className) item.className = className;
  if (text !== undefined) item.textContent = text;
  return item;
}
function renderThreads() {
  const list = $('threadList');
  const nodes = threads().map(row => {
    const button = element('button', `thread-item${state.selected === row.key ? ' active' : ''}`);
    button.type = 'button';
    button.setAttribute('aria-pressed', String(state.selected === row.key));
    const copy = element('span', 'thread-copy');
    copy.append(element('span', 'thread-contact', row.key));
    const draft = state.drafts[row.key];
    copy.append(element('span', 'thread-preview', draft?.attempt ? `${statusLabel(draft.attempt.status)} · ${draft.attempt.body}` : draft?.text ? `Draft · ${draft.text}` : row.latest?.body || 'New conversation'));
    copy.append(element('span', 'thread-route', state.status?.sim?.label || 'Primary SMS SIM'));
    button.append(element('span', 'avatar', row.key.slice(-2)), copy);
    button.addEventListener('click', () => select(row.key));
    return button;
  });
  if (!nodes.length) nodes.push(element('p', 'list-empty', state.online ? 'No conversations here yet. Start a new message.' : 'Your phone’s conversations appear when connected.'));
  list.replaceChildren(...nodes);
}
function statusLabel(status) {
  return {received: 'Received', sent: 'Sent', pending: 'Pending', sending: 'Sending', failed: 'Failed', unknown: 'Uncertain — check phone'}[status] || 'Status unavailable';
}
function renderMessages() {
  const messages = combinedMessages().filter(m => addressKey(m.address) === state.selected);
  const signature = JSON.stringify([state.selected, messages]);
  if (signature === messageSignature) return;
  messageSignature = signature;
  const list = $('messageList');
  const nearBottom = list.scrollHeight - list.scrollTop - list.clientHeight < 70;
  const scroll = list.scrollTop;
  const nodes = [];
  let day = '';
  for (const message of messages) {
    const date = new Date(message.date);
    const label = date.toLocaleDateString(undefined, {month: 'short', day: 'numeric', year: 'numeric'});
    if (label !== day) { nodes.push(element('div', 'day-divider', label)); day = label; }
    const row = element('div', `message-row ${message.type === 'in' ? 'in' : 'out'}`);
    const bubble = element('div', 'message-bubble');
    bubble.append(element('span', '', message.body));
    const status = message.type === 'in' ? '' : ` · ${statusLabel(message.status)}`;
    bubble.append(element('time', 'message-time', `${date.toLocaleTimeString(undefined, {hour: 'numeric', minute: '2-digit'})}${status}`));
    if (['unknown', 'failed'].includes(message.status)) bubble.classList.add('uncertain');
    row.append(bubble); nodes.push(row);
  }
  if (!nodes.length) nodes.push(element('p', 'list-empty', 'Your next message starts this conversation.'));
  list.replaceChildren(...nodes);
  list.scrollTop = nearBottom ? list.scrollHeight : scroll;
}
function select(address) {
  state.selected = address;
  messageSignature = '';
  notice('composerStatus');
  render();
  $('messageList').scrollTop = $('messageList').scrollHeight;
  $('messageInput').focus();
}
async function refresh() {
  if (!state.pairing || state.refreshing) return;
  state.refreshing = true;
  $('syncButton').classList.add('syncing');
  try {
    const data = await call('refresh');
    absorb(data);
    state.status = data.status; state.messages = data.messages; state.sends = data.sends; state.online = true;
    let warning = '';
    if (!data.status.permissions?.phoneState) warning = 'Allow phone-state permission in the Android app to identify your primary SMS SIM.';
    else if (!data.status.sim?.ready) warning = 'Your pinned SMS SIM is unavailable or changed. Restart the bridge on your phone.';
    else if (!data.status.permissions?.readSms) warning = 'Allow SMS reading in the Android app to see conversations.';
    else if (!data.status.permissions?.sendSms) warning = 'Allow SMS sending in the Android app to send texts.';
    notice('connectionNotice', warning);
    $('syncStatus').textContent = `${data.status.device || state.pairing.device} · Updated ${new Date().toLocaleTimeString(undefined, {hour: 'numeric', minute: '2-digit'})}`;
    if (!state.selected) state.selected = threads()[0]?.key || '';
  } catch (error) {
    state.online = false;
    notice('connectionNotice', error.message);
    $('syncStatus').textContent = 'Offline · drafts are saved in this browser';
  } finally { state.refreshing = false; $('syncButton').classList.remove('syncing'); render(); }
}
function openModal(id) {
  modalReturnFocus = document.activeElement;
  show(id);
  $(id).querySelector('input, button')?.focus();
}
function closeModal(id) { show(id, false); modalReturnFocus?.focus(); }
document.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', () => closeModal(button.dataset.close)));
document.addEventListener('keydown', event => {
  const modal = document.querySelector('.modal:not(.hidden)');
  if (!modal) return;
  if (event.key === 'Escape') { event.preventDefault(); closeModal(modal.id); }
  if (event.key === 'Tab') {
    const items = [...modal.querySelectorAll('button, input, textarea')].filter(el => !el.disabled && !el.classList.contains('hidden'));
    const first = items[0], last = items.at(-1);
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }
});
$('connectForm').addEventListener('submit', async event => {
  event.preventDefault(); notice('setupError');
  $('connectButton').disabled = true;
  try {
    const pairing = parsePairing($('pairingInput').value);
    // Request exact host access directly in the user's submit gesture.
    if (!await chrome.permissions.request({origins: [hostPermission(pairing.url)]})) throw new Error('Chrome needs access to this phone’s local address to connect.');
    absorb(await call('pair', {text: JSON.stringify(pairing)}));
    $('pairingInput').value = ''; render(); await refresh();
  } catch (error) { notice('setupError', error.message); }
  finally { $('connectButton').disabled = false; }
});
$('settingsButton').addEventListener('click', () => {
  $('accountLabel').textContent = state.status?.device || state.pairing.device;
  $('endpointInput').value = state.pairing.url;
  notice('settingsError'); openModal('settingsModal');
});
$('settingsForm').addEventListener('submit', async event => {
  event.preventDefault(); notice('settingsError'); $('saveSettingsButton').disabled = true;
  try {
    const url = endpoint($('endpointInput').value);
    if (!await chrome.permissions.request({origins: [hostPermission(url)]})) throw new Error('Allow Chrome to connect to this phone address.');
    absorb(await call('endpoint', {url})); closeModal('settingsModal'); await refresh();
  } catch (error) { notice('settingsError', error.message); }
  finally { $('saveSettingsButton').disabled = false; }
});
$('disconnectButton').addEventListener('click', async () => {
  try { await call('forget'); closeModal('settingsModal'); resetMemory(); notice('setupError'); render(); }
  catch (error) { notice('settingsError', error.message); }
});
$('newMessageButton').addEventListener('click', () => { notice('newMessageError'); $('newContactInput').value = ''; openModal('newMessageModal'); $('newContactInput').focus(); });
$('newMessageForm').addEventListener('submit', event => {
  event.preventDefault();
  try { const to = destination($('newContactInput').value); closeModal('newMessageModal'); select(to); }
  catch (error) { notice('newMessageError', error.message); }
});
$('searchInput').addEventListener('input', renderThreads);
$('syncButton').addEventListener('click', refresh);
$('openTabButton').addEventListener('click', async () => { try { await call('open-tab'); if (!document.documentElement.classList.contains('full-page')) window.close(); } catch (error) { notice('connectionNotice', error.message); } });
$('messageInput').addEventListener('input', () => {
  const to = state.selected, text = $('messageInput').value;
  if (!to || state.drafts[to]?.attempt) return;
  state.drafts[to] = {text, updated: Date.now()};
  render();
  // No debounce: a popup may close immediately after the last keystroke.
  call('draft', {to, text}).catch(error => notice('composerStatus', `Draft was not saved: ${error.message}`));
});
$('messageInput').addEventListener('keydown', event => {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229) {
    event.preventDefault(); if (!$('sendButton').disabled) $('composerForm').requestSubmit();
  }
});
$('composerForm').addEventListener('submit', async event => {
  event.preventDefault();
  if (state.submitting || $('sendButton').disabled || state.drafts[state.selected]?.attempt) return;
  const to = state.selected, body = $('messageInput').value;
  const params = {sendId: crypto.randomUUID(), to, body, subscriptionId: state.status?.sim?.id};
  state.submitting = true;
  state.drafts[to] = {text: body, updated: Date.now(), attempt: {...params, date: Date.now(), status: 'sending'}};
  notice('composerStatus'); render();
  try {
    const data = await call('send', {params});
    absorb(data);
    if (data.status === 'sent' && state.selected === to) $('messageInput').value = '';
    if (state.selected === to) notice('composerStatus', data.status === 'sent' ? 'Sent through your phone.' : data.status === 'sending' ? 'Phone accepted the message. Waiting for its send result…' : 'Check your phone before sending this message again.');
  } catch (error) {
    // Reload durable state; a lost worker response is never a reason to retry.
    try { absorb(await call('state')); } catch {}
    notice('composerStatus', `${error.message} Check your phone and refresh before sending again.`);
  } finally {
    state.submitting = false; render(); await refresh();
  }
});
$('composeAgainButton').addEventListener('click', async () => {
  try { absorb(await call('compose-again', {to: state.selected})); notice('composerStatus', 'Draft restored. Sending it will create a new SMS.'); render(); $('messageInput').focus(); }
  catch (error) { notice('composerStatus', error.message); }
});
chrome.storage.onChanged.addListener((changes, area) => {
  if (area !== 'local') return;
  if (changes.pairing && !changes.pairing.newValue) { resetMemory(); render(); return; }
  if (changes.drafts) { state.drafts = changes.drafts.newValue || {}; render(); }
});

try { absorb(await call('state')); render(); await refresh(); }
catch (error) { show('loadingView', false); show('setupView'); notice('setupError', error.message); }
setInterval(() => { if (!document.hidden) refresh(); }, 5000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
