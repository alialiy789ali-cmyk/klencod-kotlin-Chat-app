import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import {
  createDecipheriv,
  createHash,
  createHmac,
  createPrivateKey,
  createPublicKey,
  diffieHellman
} from 'node:crypto';
import { validateEphemeralPublicKey, verifyEnvelopeSignature } from './auth.js';

const vectorPath = fileURLToPath(new URL('../../app/src/test/resources/e2ee-v1-vector.json', import.meta.url));
const vector = JSON.parse(await readFile(vectorPath, 'utf8'));

function decryptVector() {
  const envelope = vector.envelope;
  const ephemeralPublicKey = createPublicKey({
    key: Buffer.from(envelope.ephemeralPublicKey, 'base64'),
    type: 'spki',
    format: 'der'
  });
  const recipientPrivateKey = createPrivateKey({
    key: Buffer.from(vector.recipientEncryptionPrivateKeyPkcs8Base64, 'base64'),
    type: 'pkcs8',
    format: 'der'
  });
  const sharedSecret = diffieHellman({ privateKey: recipientPrivateKey, publicKey: ephemeralPublicKey });
  const sender = vector.senderHandle.replace(/^@/, '').toLowerCase();
  const recipient = vector.recipientHandle.replace(/^@/, '').toLowerCase();
  const aad = Buffer.from(`sovereign-aes-gcm-v1\n${sender}\n${recipient}\n${envelope.sentAt}`, 'utf8');
  const salt = createHash('sha256').update(aad).digest();
  const prk = createHmac('sha256', salt).update(sharedSecret).digest();
  const key = createHmac('sha256', prk)
    .update(Buffer.concat([Buffer.from('sovereign-message-v1', 'utf8'), Buffer.from([1])]))
    .digest()
    .subarray(0, 32);
  const decipher = createDecipheriv('aes-256-gcm', key, Buffer.from(envelope.nonce, 'base64'));
  decipher.setAAD(aad);
  decipher.setAuthTag(Buffer.from(envelope.tag, 'base64'));
  return Buffer.concat([
    decipher.update(Buffer.from(envelope.ciphertext, 'base64')),
    decipher.final()
  ]).toString('utf8');
}

test('shared P-256 ECDH/HKDF/AES-GCM/ECDSA vector is valid for the gateway', () => {
  assert.equal(vector.description.startsWith('TEST-ONLY'), true);
  validateEphemeralPublicKey(vector.envelope.ephemeralPublicKey);
  assert.equal(
    verifyEnvelopeSignature(vector.envelope, vector.senderHandle.replace(/^@/, '').toLowerCase(), vector.recipientHandle.replace(/^@/, '').toLowerCase(), vector.senderSigningPublicKeyPem),
    true
  );
  assert.equal(decryptVector(), vector.plaintext);
});

test('shared protocol vector rejects envelope tampering', () => {
  const tampered = { ...vector.envelope, ciphertext: `${vector.envelope.ciphertext.slice(0, -2)}AA` };
  assert.equal(
    verifyEnvelopeSignature(tampered, vector.senderHandle.replace(/^@/, '').toLowerCase(), vector.recipientHandle.replace(/^@/, '').toLowerCase(), vector.senderSigningPublicKeyPem),
    false
  );
});
