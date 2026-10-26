import test from 'node:test';
import assert from 'node:assert/strict';
import { createSign, generateKeyPairSync } from 'node:crypto';
import { canonicalEnvelope, hashPassword, normalizeHandle, validateEphemeralPublicKey, validateIdentityPublicKey, verifyEnvelopeSignature, verifyPassword, issueToken, verifyToken } from './auth.js';

test('handles normalize safely and reject invalid input', () => {
  assert.equal(normalizeHandle('@Ali_123'), 'ali_123');
  assert.throws(() => normalizeHandle('x'), { status: 400 });
  assert.throws(() => normalizeHandle('bad handle'), { status: 400 });
});

test('password hashes verify without storing the password', async () => {
  const { salt, hash } = await hashPassword('a long test password');
  assert.equal(await verifyPassword('a long test password', salt, hash), true);
  assert.equal(await verifyPassword('incorrect password', salt, hash), false);
});

test('tokens expire and reject tampering', () => {
  const secret = '0123456789abcdef0123456789abcdef';
  const token = issueToken('account-id', secret, 1000);
  assert.equal(verifyToken(token, secret, 1001)?.sub, 'account-id');
  assert.equal(verifyToken(token, secret, 1000 + 60 * 60 * 24 + 1), null);
  assert.equal(verifyToken(`${token.slice(0, -1)}x`, secret, 1001), null);
});

test('identity key validation accepts P-256 and rejects other key types', () => {
  const ec = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const pem = ec.publicKey.export({ type: 'spki', format: 'pem' });
  assert.match(validateIdentityPublicKey(pem), /BEGIN PUBLIC KEY/);
  const rsa = generateKeyPairSync('rsa', { modulusLength: 2048 });
  assert.throws(() => validateIdentityPublicKey(rsa.publicKey.export({ type: 'spki', format: 'pem' })), { status: 400 });
});

test('message envelopes require a valid P-256 ephemeral key and sender signature', () => {
  const sender = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const ephemeral = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const envelope = {
    version: 1,
    messageId: 'b6dd08d2-04e7-42d2-9e0c-0a03f6147c17',
    sentAt: 1_750_000_000_000,
    ephemeralPublicKey: ephemeral.publicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
    nonce: Buffer.alloc(12, 4).toString('base64'),
    ciphertext: Buffer.from('opaque ciphertext bytes').toString('base64'),
    tag: Buffer.alloc(16, 8).toString('base64')
  };
  const signer = createSign('SHA256');
  signer.update(canonicalEnvelope(envelope, 'alice', 'bob'));
  signer.end();
  envelope.signature = signer.sign(sender.privateKey).toString('base64');
  const senderPem = sender.publicKey.export({ type: 'spki', format: 'pem' });
  validateEphemeralPublicKey(envelope.ephemeralPublicKey);
  assert.equal(verifyEnvelopeSignature(envelope, 'alice', 'bob', senderPem), true);
  assert.equal(verifyEnvelopeSignature({ ...envelope, ciphertext: 'dGFtcGVyZWQ=' }, 'alice', 'bob', senderPem), false);
});
