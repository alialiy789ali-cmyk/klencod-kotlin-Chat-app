import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import pg from 'pg';
import { WebSocketServer, WebSocket } from 'ws';
import { hashPassword, issueToken, normalizeHandle, validateEphemeralPublicKey, validateIdentityPublicKey, verifyEnvelopeSignature, verifyPassword, verifyToken } from './auth.js';

const { Pool } = pg;
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 8080);
const JWT_SECRET = process.env.JWT_SECRET || '';
const DATABASE_URL = process.env.DATABASE_URL || '';
const MAX_BODY_BYTES = 1024 * 1024;
const MAX_MESSAGE_BYTES = 256 * 1024;
const ALLOWED_ORIGINS = new Set((process.env.CORS_ORIGINS || '').split(',').map(x => x.trim()).filter(Boolean));

if (JWT_SECRET.length < 32) throw new Error('JWT_SECRET must contain at least 32 characters');
if (!DATABASE_URL) throw new Error('DATABASE_URL is required');

const pool = new Pool({ connectionString: DATABASE_URL, max: 15, idleTimeoutMillis: 30_000, connectionTimeoutMillis: 5_000 });
const clients = new Map();
const attempts = new Map();

function rateLimit(req, res, max = 120, windowMs = 60_000) {
  const now = Date.now();
  const ip = req.socket.remoteAddress || 'unknown';
  let entry = attempts.get(ip);
  if (!entry || entry.resetAt <= now) entry = { count: 0, resetAt: now + windowMs };
  entry.count++;
  attempts.set(ip, entry);
  if (attempts.size > 10_000) {
    for (const [key, value] of attempts) if (value.resetAt <= now) attempts.delete(key);
  }
  if (entry.count > max) {
    res.writeHead(429, { 'content-type': 'application/json', 'retry-after': String(Math.max(1, Math.ceil((entry.resetAt - now) / 1000))) });
    res.end(JSON.stringify({ error: 'Too many requests' }));
    return false;
  }
  return true;
}

function sendJson(res, status, body, extraHeaders = {}) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff', ...extraHeaders });
  res.end(JSON.stringify(body));
}

async function readJson(req, limit = MAX_BODY_BYTES) {
  const chunks = [];
  let length = 0;
  for await (const chunk of req) {
    length += chunk.length;
    if (length > limit) throw Object.assign(new Error('Request body too large'), { status: 413 });
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw Object.assign(new Error('Invalid JSON body'), { status: 400 }); }
}

function getBearer(req) {
  const header = req.headers.authorization || '';
  return header.startsWith('Bearer ') ? header.slice(7) : '';
}

async function requireUser(req) {
  const claims = verifyToken(getBearer(req), JWT_SECRET);
  if (!claims) throw Object.assign(new Error('Authentication required'), { status: 401 });
  const result = await pool.query('SELECT id, handle, display_name, identity_public_key, encryption_public_key FROM app_users WHERE id = $1', [claims.sub]);
  if (!result.rowCount) throw Object.assign(new Error('Account no longer exists'), { status: 401 });
  return result.rows[0];
}

function broadcast(userId, event) {
  const sockets = clients.get(userId);
  if (!sockets) return;
  const payload = JSON.stringify(event);
  for (const socket of sockets) if (socket.readyState === WebSocket.OPEN) socket.send(payload);
}

function validateEncryptedEnvelope(value, senderHandle, recipientHandle, senderPublicKey) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw Object.assign(new Error('Encrypted payload is required'), { status: 400 });
  const json = JSON.stringify(value);
  if (Buffer.byteLength(json) > MAX_MESSAGE_BYTES) throw Object.assign(new Error('Encrypted payload exceeds size limit'), { status: 413 });
  const base64 = field => typeof field === 'string' && /^[A-Za-z0-9+/]+={0,2}$/.test(field);
  if (value.version !== 1 || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value.messageId || '') ||
      !Number.isSafeInteger(value.sentAt) || !base64(value.ephemeralPublicKey) ||
      !base64(value.nonce) || Buffer.from(value.nonce, 'base64').length !== 12 ||
      !base64(value.ciphertext) || Buffer.from(value.ciphertext, 'base64').length === 0 ||
      !base64(value.tag) || Buffer.from(value.tag, 'base64').length !== 16 || !base64(value.signature)) {
    throw Object.assign(new Error('Invalid ECDH/AES-GCM message envelope'), { status: 400 });
  }
  validateEphemeralPublicKey(value.ephemeralPublicKey);
  if (!verifyEnvelopeSignature(value, senderHandle, recipientHandle, senderPublicKey)) {
    throw Object.assign(new Error('Invalid sender signature'), { status: 400 });
  }
  return value;
}

function routePath(url) {
  try { return new URL(url, 'http://localhost'); } catch { return null; }
}

const server = http.createServer(async (req, res) => {
  const origin = req.headers.origin;
  if (origin && ALLOWED_ORIGINS.size && !ALLOWED_ORIGINS.has(origin)) return sendJson(res, 403, { error: 'Origin not allowed' });
  if (origin && ALLOWED_ORIGINS.has(origin)) {
    res.setHeader('access-control-allow-origin', origin);
    res.setHeader('vary', 'Origin');
    res.setHeader('access-control-allow-headers', 'authorization, content-type');
    res.setHeader('access-control-allow-methods', 'GET, POST, PATCH, OPTIONS');
  }
  if (req.method === 'OPTIONS') { res.writeHead(204); return res.end(); }

  const url = routePath(req.url);
  if (!url) return sendJson(res, 400, { error: 'Invalid URL' });
  const authRoute = url.pathname.startsWith('/v1/auth/');
  if (!rateLimit(req, res, authRoute ? 10 : 120)) return;
  const method = req.method || 'GET';

  try {
    if (method === 'GET' && url.pathname === '/health') {
      await pool.query('SELECT 1');
      return sendJson(res, 200, { status: 'ok', service: 'sovereign-gateway', time: new Date().toISOString() });
    }

    if (method === 'POST' && url.pathname === '/v1/auth/register') {
      const body = await readJson(req);
      const handle = normalizeHandle(body.handle);
      const displayName = String(body.displayName || '').trim().slice(0, 128);
      if (!displayName) throw Object.assign(new Error('Display name is required'), { status: 400 });
      const signingKey = validateIdentityPublicKey(body.identityPublicKey);
      const encryptionKey = validateIdentityPublicKey(body.encryptionPublicKey);
      const { salt, hash } = await hashPassword(body.password);
      const created = await pool.query(
        `INSERT INTO app_users(handle, display_name, password_salt, password_hash, identity_public_key, encryption_public_key)
         VALUES ($1, $2, $3, $4, $5, $6) RETURNING id, handle, display_name, identity_public_key, encryption_public_key`,
        [handle, displayName, salt, hash, signingKey, encryptionKey]
      );
      const user = created.rows[0];
      return sendJson(res, 201, { token: issueToken(user.id, JWT_SECRET), user: { ...user, handle: `@${user.handle}` } });
    }

    if (method === 'POST' && url.pathname === '/v1/auth/login') {
      const body = await readJson(req);
      const handle = normalizeHandle(body.handle);
      const result = await pool.query(
        'SELECT id, handle, display_name, identity_public_key, encryption_public_key, password_salt, password_hash FROM app_users WHERE handle = $1',
        [handle]
      );
      const user = result.rows[0];
      if (!user || !(await verifyPassword(body.password, user.password_salt, user.password_hash))) {
        return sendJson(res, 401, { error: 'Invalid handle or password' });
      }
      return sendJson(res, 200, {
        token: issueToken(user.id, JWT_SECRET),
        user: { id: user.id, handle: `@${user.handle}`, display_name: user.display_name, identity_public_key: user.identity_public_key, encryption_public_key: user.encryption_public_key }
      });
    }

    const user = await requireUser(req);

    if (method === 'GET' && url.pathname === '/v1/me') {
      return sendJson(res, 200, { id: user.id, handle: `@${user.handle}`, displayName: user.display_name, identityPublicKey: user.identity_public_key, encryptionPublicKey: user.encryption_public_key });
    }

    const keyMatch = url.pathname.match(/^\/v1\/users\/([^/]+)\/key$/);
    if (method === 'GET' && keyMatch) {
      const handle = normalizeHandle(decodeURIComponent(keyMatch[1]));
      const result = await pool.query('SELECT handle, identity_public_key, encryption_public_key FROM app_users WHERE handle = $1', [handle]);
      if (!result.rowCount) return sendJson(res, 404, { error: 'User not found' });
      return sendJson(res, 200, { handle: `@${result.rows[0].handle}`, identityPublicKey: result.rows[0].identity_public_key, encryptionPublicKey: result.rows[0].encryption_public_key });
    }

    if (method === 'POST' && url.pathname === '/v1/messages') {
      const body = await readJson(req, MAX_MESSAGE_BYTES + 16_384);
      const recipientHandle = normalizeHandle(body.recipient);
      if (recipientHandle === user.handle) throw Object.assign(new Error('Cannot message yourself'), { status: 400 });
      const recipient = await pool.query('SELECT id, handle, encryption_public_key FROM app_users WHERE handle = $1', [recipientHandle]);
      if (!recipient.rowCount) return sendJson(res, 404, { error: 'Recipient not found' });
      const envelope = validateEncryptedEnvelope(body.envelope, user.handle, recipientHandle, user.identity_public_key);
      const sent = await pool.query(
        `INSERT INTO encrypted_messages(sender_id, recipient_id, client_message_id, encrypted_payload, expires_at)
         VALUES ($1, $2, $3::uuid, $4::jsonb, CASE WHEN $5::integer > 0 THEN now() + ($5::integer * interval '1 second') ELSE NULL END)
         RETURNING id, client_message_id, encrypted_payload, created_at, expires_at`,
        [user.id, recipient.rows[0].id, envelope.messageId, JSON.stringify(envelope), Math.max(0, Math.min(86_400, Number(body.expiresInSeconds) || 0))]
      );
      const message = { ...sent.rows[0], senderHandle: `@${user.handle}`, recipientHandle: `@${recipientHandle}` };
      broadcast(recipient.rows[0].id, { type: 'message.new', message });
      return sendJson(res, 201, { message });
    }

    if (method === 'GET' && url.pathname === '/v1/messages') {
      const limit = Math.max(1, Math.min(100, Number(url.searchParams.get('limit')) || 50));
      const after = url.searchParams.get('after');
      const values = [user.id, limit];
      let cursor = '';
      if (after && Number.isFinite(Date.parse(after))) { values.push(new Date(after).toISOString()); cursor = 'AND m.created_at > $3::timestamptz'; }
      const result = await pool.query(
        `SELECT m.id, m.client_message_id, m.sender_id, m.encrypted_payload, m.created_at, m.delivered_at, m.read_at, m.expires_at,
                s.handle AS sender_handle, r.handle AS recipient_handle
           FROM encrypted_messages m
           JOIN app_users s ON s.id = m.sender_id JOIN app_users r ON r.id = m.recipient_id
          WHERE (m.sender_id = $1 OR m.recipient_id = $1)
            AND (m.expires_at IS NULL OR m.expires_at > now()) ${cursor}
          ORDER BY m.created_at DESC LIMIT $2`,
        values
      );
      const ids = result.rows.filter(row => row.recipient_handle === user.handle && !row.delivered_at).map(row => row.id);
      if (ids.length) {
        await pool.query('UPDATE encrypted_messages SET delivered_at = now() WHERE id = ANY($1::uuid[])', [ids]);
        for (const row of result.rows) if (ids.includes(row.id)) broadcast(row.sender_id, { type: 'message.delivered', messageId: row.id });
      }
      return sendJson(res, 200, { messages: result.rows.map(row => ({ ...row, senderHandle: `@${row.sender_handle}`, recipientHandle: `@${row.recipient_handle}` })).reverse() });
    }

    const readMatch = url.pathname.match(/^\/v1\/messages\/([0-9a-f-]+)\/read$/i);
    if (method === 'PATCH' && readMatch) {
      const result = await pool.query(
        `UPDATE encrypted_messages SET read_at = COALESCE(read_at, now()), delivered_at = COALESCE(delivered_at, now())
          WHERE id = $1 AND recipient_id = $2 RETURNING id, sender_id`,
        [readMatch[1], user.id]
      );
      if (!result.rowCount) return sendJson(res, 404, { error: 'Message not found' });
      broadcast(result.rows[0].sender_id, { type: 'message.read', messageId: result.rows[0].id });
      return sendJson(res, 200, { ok: true });
    }

    if (method === 'GET' && url.pathname === '/v1/channels') {
      const result = await pool.query(
        `SELECT c.id, c.title, c.handle, c.description, c.owner_id, c.created_at,
                COUNT(m.user_id)::int AS subscriber_count,
                bool_or(m.user_id = $1) AS is_subscribed
           FROM sovereign_channels c LEFT JOIN channel_members m ON m.channel_id = c.id
          GROUP BY c.id ORDER BY c.created_at DESC LIMIT 100`, [user.id]
      );
      return sendJson(res, 200, { channels: result.rows });
    }

    if (method === 'POST' && url.pathname === '/v1/channels') {
      const body = await readJson(req);
      const title = String(body.title || '').trim().slice(0, 128);
      const description = String(body.description || '').trim().slice(0, 2000);
      const handle = `@${normalizeHandle(body.handle)}`;
      if (!title) throw Object.assign(new Error('Channel title is required'), { status: 400 });
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const created = await client.query(
          'INSERT INTO sovereign_channels(title, handle, description, owner_id) VALUES ($1,$2,$3,$4) RETURNING *',
          [title, handle, description, user.id]
        );
        await client.query("INSERT INTO channel_members(channel_id,user_id,role) VALUES ($1,$2,'owner')", [created.rows[0].id, user.id]);
        await client.query('COMMIT');
        return sendJson(res, 201, { channel: created.rows[0] });
      } catch (error) { await client.query('ROLLBACK'); throw error; }
      finally { client.release(); }
    }

    const joinMatch = url.pathname.match(/^\/v1\/channels\/([0-9a-f-]+)\/join$/i);
    if (method === 'POST' && joinMatch) {
      await pool.query("INSERT INTO channel_members(channel_id,user_id,role) VALUES ($1,$2,'subscriber') ON CONFLICT DO NOTHING", [joinMatch[1], user.id]);
      return sendJson(res, 200, { ok: true });
    }

    const channelPostsMatch = url.pathname.match(/^\/v1\/channels\/([0-9a-f-]+)\/posts$/i);
    if (channelPostsMatch && method === 'GET') {
      const result = await pool.query(
        `SELECT p.id, p.content, p.created_at, u.handle AS author_handle
           FROM channel_posts p JOIN channel_members m ON m.channel_id=p.channel_id AND m.user_id=$2
           JOIN app_users u ON u.id=p.author_id WHERE p.channel_id=$1 ORDER BY p.created_at DESC LIMIT 100`,
        [channelPostsMatch[1], user.id]
      );
      return sendJson(res, 200, { posts: result.rows });
    }
    if (channelPostsMatch && method === 'POST') {
      const body = await readJson(req);
      const content = String(body.content || '').trim().slice(0, 20_000);
      if (!content) throw Object.assign(new Error('Post content is required'), { status: 400 });
      const result = await pool.query(
        `INSERT INTO channel_posts(channel_id,author_id,content)
         SELECT $1,$2,$3 WHERE EXISTS (SELECT 1 FROM channel_members WHERE channel_id=$1 AND user_id=$2 AND role IN ('owner','admin'))
         RETURNING *`, [channelPostsMatch[1], user.id, content]
      );
      if (!result.rowCount) return sendJson(res, 403, { error: 'Channel admin access required' });
      return sendJson(res, 201, { post: result.rows[0] });
    }

    if (method === 'GET' && url.pathname === '/v1/feed') {
      const result = await pool.query(
        `SELECT p.id, p.content, p.hashtags, p.created_at, u.handle AS author_handle, u.display_name AS author_name,
                COUNT(DISTINCT r_like.user_id)::int AS likes_count,
                COUNT(DISTINCT r_repost.user_id)::int AS reposts_count,
                COUNT(DISTINCT c.id)::int AS comments_count,
                bool_or(r_like.user_id=$1) AS is_liked, bool_or(r_repost.user_id=$1) AS is_reposted
           FROM social_posts p JOIN app_users u ON u.id=p.author_id
           LEFT JOIN post_reactions r_like ON r_like.post_id=p.id AND r_like.reaction='like'
           LEFT JOIN post_reactions r_repost ON r_repost.post_id=p.id AND r_repost.reaction='repost'
           LEFT JOIN post_comments c ON c.post_id=p.id
          GROUP BY p.id,u.handle,u.display_name ORDER BY p.created_at DESC LIMIT 100`, [user.id]
      );
      return sendJson(res, 200, { posts: result.rows });
    }

    if (method === 'POST' && url.pathname === '/v1/feed') {
      const body = await readJson(req);
      const content = String(body.content || '').trim().slice(0, 20_000);
      if (!content) throw Object.assign(new Error('Post content is required'), { status: 400 });
      const hashtags = Array.isArray(body.hashtags) ? body.hashtags.map(x => String(x).replace(/^#/, '').slice(0, 64)).filter(Boolean).slice(0, 20) : [];
      const result = await pool.query('INSERT INTO social_posts(author_id,content,hashtags) VALUES ($1,$2,$3) RETURNING *', [user.id, content, hashtags]);
      return sendJson(res, 201, { post: result.rows[0] });
    }

    const reactionMatch = url.pathname.match(/^\/v1\/posts\/([0-9a-f-]+)\/(like|repost)$/i);
    if (reactionMatch && method === 'POST') {
      const [, postId, reaction] = reactionMatch;
      const deleted = await pool.query('DELETE FROM post_reactions WHERE post_id=$1 AND user_id=$2 AND reaction=$3 RETURNING post_id', [postId, user.id, reaction]);
      let active = false;
      if (!deleted.rowCount) {
        const inserted = await pool.query('INSERT INTO post_reactions(post_id,user_id,reaction) VALUES ($1,$2,$3) ON CONFLICT DO NOTHING RETURNING post_id', [postId, user.id, reaction]);
        if (!inserted.rowCount) return sendJson(res, 404, { error: 'Post not found' });
        active = true;
      }
      return sendJson(res, 200, { active });
    }

    const commentsMatch = url.pathname.match(/^\/v1\/posts\/([0-9a-f-]+)\/comments$/i);
    if (commentsMatch && method === 'GET') {
      const result = await pool.query(
        `SELECT c.id,c.content,c.created_at,u.handle AS author_handle,u.display_name AS author_name
           FROM post_comments c JOIN app_users u ON u.id=c.author_id WHERE c.post_id=$1 ORDER BY c.created_at LIMIT 100`, [commentsMatch[1]]
      );
      return sendJson(res, 200, { comments: result.rows });
    }
    if (commentsMatch && method === 'POST') {
      const body = await readJson(req);
      const content = String(body.content || '').trim().slice(0, 4000);
      if (!content) throw Object.assign(new Error('Comment is required'), { status: 400 });
      const result = await pool.query('INSERT INTO post_comments(post_id,author_id,content) SELECT $1,$2,$3 WHERE EXISTS(SELECT 1 FROM social_posts WHERE id=$1) RETURNING *', [commentsMatch[1], user.id, content]);
      if (!result.rowCount) return sendJson(res, 404, { error: 'Post not found' });
      return sendJson(res, 201, { comment: result.rows[0] });
    }

    return sendJson(res, 404, { error: 'Not found' });
  } catch (error) {
    if (error.code === '23505') return sendJson(res, 409, { error: 'Handle or channel already exists' });
    if (error.code === '22P02') return sendJson(res, 400, { error: 'Invalid identifier' });
    const status = Number(error.status) || 500;
    if (status >= 500) console.error('gateway error', error.message);
    return sendJson(res, status, { error: status >= 500 ? 'Internal server error' : error.message });
  }
});

const webSockets = new WebSocketServer({ noServer: true, maxPayload: 4096, perMessageDeflate: false });
server.on('upgrade', (req, socket, head) => {
  const url = routePath(req.url);
  if (!url || url.pathname !== '/v1/events') { socket.destroy(); return; }
  webSockets.handleUpgrade(req, socket, head, ws => webSockets.emit('connection', ws, req));
});

webSockets.on('connection', ws => {
  let userId = null;
  const authTimeout = setTimeout(() => ws.close(4401, 'Authenticate first'), 5000);
  ws.on('message', async raw => {
    if (userId) { ws.close(1008, 'Client messages are not accepted'); return; }
    try {
      const frame = JSON.parse(raw.toString());
      if (frame.type !== 'auth' || typeof frame.token !== 'string') throw new Error('Invalid auth frame');
      const claims = verifyToken(frame.token, JWT_SECRET);
      if (!claims) throw new Error('Invalid token');
      const result = await pool.query('SELECT id FROM app_users WHERE id=$1', [claims.sub]);
      if (!result.rowCount) throw new Error('Unknown account');
      userId = claims.sub;
      clearTimeout(authTimeout);
      const group = clients.get(userId) || new Set();
      group.add(ws);
      clients.set(userId, group);
      ws.send(JSON.stringify({ type: 'ready' }));
    } catch { ws.close(4401, 'Invalid authentication'); }
  });
  ws.on('close', () => {
    clearTimeout(authTimeout);
    if (!userId) return;
    const group = clients.get(userId);
    group?.delete(ws);
    if (group?.size === 0) clients.delete(userId);
  });
});

const maintenance = setInterval(() => {
  pool.query('DELETE FROM encrypted_messages WHERE expires_at IS NOT NULL AND expires_at <= now()').catch(error => console.error('message cleanup failed', error.message));
  for (const sockets of clients.values()) for (const ws of sockets) if (ws.readyState === WebSocket.OPEN) ws.ping();
}, 60_000);
maintenance.unref();

async function start() {
  const schema = await readFile(path.join(__dirname, '..', 'schema.sql'), 'utf8');
  await pool.query(schema);
  server.listen(PORT, '0.0.0.0', () => console.log(`Sovereign gateway listening on ${PORT}`));
}

async function shutdown() {
  clearInterval(maintenance);
  for (const sockets of clients.values()) for (const ws of sockets) ws.close(1001, 'Server shutting down');
  server.close();
  await pool.end();
}

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
start().catch(error => { console.error('startup failed', error.message); process.exit(1); });
