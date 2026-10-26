'use strict';
// Lightweight *development* chat server for trusted LAN testing.
// Node.js >=18; no npm packages required. Not E2EE and not production-ready.
const http = require('node:http');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const PORT = Number(process.env.PORT || 8080);
const HOST = process.env.HOST || '0.0.0.0';
const BASE = path.join(__dirname, '.chat-live');
fs.mkdirSync(BASE, {recursive:true, mode: 0o700});
const DB_FILE = path.join(BASE, 'database.json');
const KEY_FILE = path.join(BASE, 'token-secret');
const secret = fs.existsSync(KEY_FILE) ? fs.readFileSync(KEY_FILE) : crypto.randomBytes(32);
if (!fs.existsSync(KEY_FILE)) fs.writeFileSync(KEY_FILE, secret, {mode:0o600});
let db = {users:{}, messages:[]};
if (fs.existsSync(DB_FILE)) db = JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
function persist() {
  const temp = DB_FILE + '.tmp';
  fs.writeFileSync(temp, JSON.stringify(db), {mode:0o600});
  fs.renameSync(temp, DB_FILE);
}
function reply(res, code, value) {
  const data = JSON.stringify(value);
  res.writeHead(code, {'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});
  res.end(data);
}
function bad(res, code, error) { reply(res, code, {error}); }
function parseBody(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', chunk => {
      raw += chunk;
      if (raw.length > 16384) { reject(new Error('body too large')); req.destroy(); }
    });
    req.on('end', () => { try { resolve(JSON.parse(raw || '{}')); } catch { reject(new Error('invalid JSON')); } });
    req.on('error', reject);
  });
}
function passwordHash(password, saltHex) {
  return crypto.scryptSync(password, Buffer.from(saltHex,'hex'), 64).toString('hex');
}
const usernameOK = s => typeof s === 'string' && /^[a-z0-9_]{3,24}$/.test(s);
const passOK = s => typeof s === 'string' && s.length >= 8 && s.length <= 128;
function issueToken(user) {
  const payload = Buffer.from(JSON.stringify({sub:user, exp:Math.floor(Date.now()/1000)+86400})).toString('base64url');
  const signature = crypto.createHmac('sha256',secret).update(payload).digest('base64url');
  return `${payload}.${signature}`;
}
function tokenUser(req) {
  const m = /^Bearer ([A-Za-z0-9_\-.]+)$/.exec(req.headers.authorization || '');
  if (!m) return null;
  const [payload,signature] = m[1].split('.');
  if (!payload || !signature) return null;
  const expected = crypto.createHmac('sha256',secret).update(payload).digest('base64url');
  const a = Buffer.from(signature), b = Buffer.from(expected);
  if (a.length !== b.length || !crypto.timingSafeEqual(a,b)) return null;
  try {
    const obj = JSON.parse(Buffer.from(payload,'base64url').toString('utf8'));
    return obj.exp > Date.now()/1000 && db.users[obj.sub] ? obj.sub : null;
  } catch { return null; }
}
const attempts = new Map();
function rateLimited(ip) {
  const now = Date.now();
  const record = (attempts.get(ip) || []).filter(x=>x > now - 5*60*1000);
  record.push(now); attempts.set(ip, record);
  return record.length > 30;
}
const server = http.createServer(async(req,res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    if (req.method === 'GET' && url.pathname === '/health') return reply(res,200,{ok:true,service:'chat-live-dev'});
    if (req.method === 'POST' && ['/register','/login'].includes(url.pathname)) {
      if (rateLimited(req.socket.remoteAddress || 'unknown')) return bad(res,429,'Too many attempts');
      const body = await parseBody(req);
      const user = String(body.username || '').toLowerCase();
      const password = body.password;
      if (!usernameOK(user) || !passOK(password)) return bad(res,400,'Username: 3-24 lowercase letters/numbers/underscore; password: 8-128 chars');
      if (url.pathname === '/register') {
        if (db.users[user]) return bad(res,409,'Username already exists');
        const salt = crypto.randomBytes(16).toString('hex');
        db.users[user] = {salt,hash:passwordHash(password,salt)};
        persist();
        return reply(res,201,{user,token:issueToken(user)});
      }
      const account = db.users[user];
      if (!account) return bad(res,401,'Invalid username or password');
      const actual = Buffer.from(passwordHash(password,account.salt),'hex');
      const expected = Buffer.from(account.hash,'hex');
      if (actual.length !== expected.length || !crypto.timingSafeEqual(actual,expected)) return bad(res,401,'Invalid username or password');
      return reply(res,200,{user,token:issueToken(user)});
    }
    const user = tokenUser(req);
    if (!user) return bad(res,401,'Please log in');
    if (req.method === 'GET' && url.pathname === '/messages') {
      const peer = String(url.searchParams.get('with') || '').toLowerCase();
      if (!usernameOK(peer)) return bad(res,400,'Invalid recipient');
      if (!db.users[peer]) return bad(res,404,'Recipient not registered');
      const messages = db.messages.filter(m=>(m.from===user && m.to===peer) || (m.from===peer && m.to===user)).slice(-200);
      return reply(res,200,{messages});
    }
    if (req.method === 'POST' && url.pathname === '/messages') {
      const body = await parseBody(req);
      const to = String(body.to || '').toLowerCase();
      const text = body.text;
      if (!usernameOK(to) || !db.users[to]) return bad(res,404,'Recipient not registered');
      if (to === user) return bad(res,400,'Cannot message yourself');
      if (typeof text !== 'string' || !text.trim() || text.length > 4000) return bad(res,400,'Message must be 1-4000 characters');
      const message = {id:crypto.randomUUID(),from:user,to,text:text.trim(),time:Date.now()};
      db.messages.push(message);
      persist();
      return reply(res,201,{message});
    }
    return bad(res,404,'Not found');
  } catch (err) {
    if (!res.headersSent && !res.destroyed) bad(res,400,'Bad request');
  }
});
server.listen(PORT,HOST,()=>console.log(`Chat test server listening on http://${HOST}:${PORT}`));
