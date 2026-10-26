import { createHmac, randomBytes, scrypt as scryptCallback, timingSafeEqual, createPublicKey, verify as verifySignature } from 'node:crypto';
import { promisify } from 'node:util';

const scrypt = promisify(scryptCallback);
const TOKEN_TTL_SECONDS = 60 * 60 * 24;

export function normalizeHandle(value) {
  const handle = String(value ?? '').trim().replace(/^@/, '').toLowerCase();
  if (!/^[a-z0-9_]{3,32}$/.test(handle)) throw Object.assign(new Error('Invalid handle'), { status: 400 });
  return handle;
}

export async function hashPassword(password, salt = randomBytes(16)) {
  if (typeof password !== 'string' || password.length < 10 || password.length > 256) {
    throw Object.assign(new Error('Password must be 10–256 characters'), { status: 400 });
  }
  const hash = await scrypt(password, salt, 64, { N: 32768, r: 8, p: 1, maxmem: 64 * 1024 * 1024 });
  return { salt, hash };
}

export async function verifyPassword(password, salt, expectedHash) {
  if (typeof password !== 'string' || password.length > 256) return false;
  const actual = await scrypt(password, salt, expectedHash.length, { N: 32768, r: 8, p: 1, maxmem: 64 * 1024 * 1024 });
  return actual.length === expectedHash.length && timingSafeEqual(actual, expectedHash);
}

export function validateIdentityPublicKey(pem) {
  if (typeof pem !== 'string' || pem.length > 4096) throw Object.assign(new Error('Invalid public key'), { status: 400 });
  try {
    const key = createPublicKey(pem);
    const details = key.asymmetricKeyDetails ?? {};
    if (key.asymmetricKeyType !== 'ec' || !['prime256v1', 'secp256r1'].includes(details.namedCurve)) {
      throw new Error('Expected an EC P-256 public key');
    }
    return key.export({ type: 'spki', format: 'pem' }).toString();
  } catch {
    throw Object.assign(new Error('A valid EC P-256 public identity key is required'), { status: 400 });
  }
}

export function validateEphemeralPublicKey(encoded) {
  try {
    if (typeof encoded !== 'string' || !/^[A-Za-z0-9+/]+={0,2}$/.test(encoded)) throw new Error('bad base64');
    const der = Buffer.from(encoded, 'base64');
    const key = createPublicKey({ key: der, type: 'spki', format: 'der' });
    const details = key.asymmetricKeyDetails ?? {};
    if (key.asymmetricKeyType !== 'ec' || !['prime256v1', 'secp256r1'].includes(details.namedCurve)) throw new Error('wrong key type');
    return true;
  } catch {
    throw Object.assign(new Error('Invalid ephemeral P-256 public key'), { status: 400 });
  }
}

export function canonicalEnvelope(envelope, senderHandle, recipientHandle) {
  return [
    'sovereign-envelope-v1', envelope.messageId, senderHandle, recipientHandle, String(envelope.sentAt),
    envelope.ephemeralPublicKey, envelope.nonce, envelope.ciphertext, envelope.tag
  ].join('\n');
}

export function verifyEnvelopeSignature(envelope, senderHandle, recipientHandle, senderPublicKey) {
  try {
    if (typeof envelope.signature !== 'string' || !/^[A-Za-z0-9+/]+={0,2}$/.test(envelope.signature)) return false;
    const signature = Buffer.from(envelope.signature, 'base64');
    return verifySignature(
      'sha256',
      Buffer.from(canonicalEnvelope(envelope, senderHandle, recipientHandle), 'utf8'),
      createPublicKey(senderPublicKey),
      signature
    );
  } catch { return false; }
}

export function issueToken(userId, secret, now = Math.floor(Date.now() / 1000)) {
  const payload = Buffer.from(JSON.stringify({ sub: userId, iat: now, exp: now + TOKEN_TTL_SECONDS })).toString('base64url');
  const signature = createHmac('sha256', secret).update(payload).digest('base64url');
  return `${payload}.${signature}`;
}

export function verifyToken(token, secret, now = Math.floor(Date.now() / 1000)) {
  if (typeof token !== 'string' || token.length > 2048) return null;
  const parts = token.split('.');
  if (parts.length !== 2) return null;
  const [payload, signature] = parts;
  const expected = createHmac('sha256', secret).update(payload).digest();
  let actual;
  try { actual = Buffer.from(signature, 'base64url'); } catch { return null; }
  if (actual.length !== expected.length || !timingSafeEqual(actual, expected)) return null;
  try {
    const claims = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
    if (typeof claims.sub !== 'string' || !Number.isInteger(claims.exp) || claims.exp <= now) return null;
    return claims;
  } catch { return null; }
}
