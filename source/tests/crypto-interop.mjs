import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {mkdtempSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {encrypt, decrypt, importKey, REQUEST_AAD, RESPONSE_AAD} from '../extension/core.js';

const dir = mkdtempSync(path.join(tmpdir(), 'voiceish-crypto-'));
const fixture = fileURLToPath(new URL('./CryptoInterop.java', import.meta.url));
const implementation = fileURLToPath(new URL('../android/app/src/main/java/com/voiceish/sim/Crypto.java', import.meta.url));
const b64 = value => Buffer.from(value).toString('base64');
try {
  execFileSync('java', ['-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', dir, implementation, fixture]);
  const keyBytes = crypto.getRandomValues(new Uint8Array(32));
  const keyText = b64(keyBytes);
  const key = await importKey(keyText);
  const request = {id: crypto.randomUUID(), ts: Date.now(), action: 'send', params: {body: 'Hello Robin — café, 中文, 🙂\nSecond line', to: '+15550100001'}};
  const encoded = await encrypt(request, key);
  const javaDecrypted = execFileSync('java', ['-cp', dir, 'CryptoInterop', 'decrypt', keyText, encoded.iv, REQUEST_AAD, encoded.data], {encoding: 'utf8'});
  assert.deepEqual(JSON.parse(Buffer.from(javaDecrypted, 'base64').toString()), request);

  const response = {id: request.id, ok: true, data: {status: 'sending'}};
  const iv = b64(crypto.getRandomValues(new Uint8Array(12)));
  const encryptedByJava = execFileSync('java', ['-cp', dir, 'CryptoInterop', 'encrypt', keyText, iv, RESPONSE_AAD, b64(JSON.stringify(response))], {encoding: 'utf8'});
  assert.deepEqual(await decrypt({v: 1, iv, data: encryptedByJava}, key), response);

  await assert.rejects(decrypt(encoded, key, RESPONSE_AAD), 'Request cannot be reflected as a response');
  const corrupted = Buffer.from(encryptedByJava, 'base64');
  corrupted[0] ^= 1;
  await assert.rejects(decrypt({v: 1, iv, data: b64(corrupted)}, key), 'Modified ciphertext must be rejected');
  const wrongKey = await importKey(b64(crypto.getRandomValues(new Uint8Array(32))));
  await assert.rejects(decrypt({v: 1, iv, data: encryptedByJava}, wrongKey), 'Unpaired key must be rejected');
  console.log('PASS: WebCrypto → Java JCE, Java JCE → WebCrypto, Unicode, direction binding, tamper rejection, wrong-key rejection. No SMS transmitted.');
} finally {
  rmSync(dir, {recursive: true, force: true});
}
