# Sovereign Gateway (preview)

Node.js 22 REST/WebSocket service with PostgreSQL storage. It stores profile and social metadata server-side; message payloads are treated as opaque ciphertext. This is a development foundation and has not received an independent security audit.

## Start with Docker

1. Copy `.env.example` to `.env` and replace all `CHANGE_ME` values. Set `SOVEREIGN_DOMAIN` to a public DNS name routed to this machine; allow inbound ports 80/443.
2. Ensure `DATABASE_URL` matches the Postgres user/password/database. URL-encode reserved characters in the password.
3. Run `docker compose up -d --build` and check `https://YOUR_DOMAIN/health`.

Caddy handles public TLS and proxies to the private gateway container. PostgreSQL is not published on the host. The `server/.env` file must not be committed.

## Local development

Requires Node.js 20+ and PostgreSQL. Set `DATABASE_URL`, `JWT_SECRET` (32+ characters), and `PORT=8080`, then run `npm ci`, `npm test`, and `npm start`.

## API sketch

- `POST /v1/auth/register`: `{handle, displayName, password, identityPublicKey, encryptionPublicKey}`. Both keys are P-256 SPKI PEM public keys; private keys must remain client-side.
- `POST /v1/auth/login`: `{handle, password}`. Returns a 24-hour bearer token.
- `GET /v1/me`, `GET /v1/users/:handle/key`: authenticated identity/key directory.
- `POST /v1/messages`: `{recipient, envelope, expiresInSeconds?}`. Envelope fields: `version: 1`, UUID `messageId`, millisecond `sentAt`, Base64 SPKI DER P-256 `ephemeralPublicKey`, 12-byte Base64 `nonce`, Base64 `ciphertext`, 16-byte Base64 GCM `tag`, Base64 DER ECDSA `signature`.
- Envelope signatures use SHA256withECDSA over UTF-8 lines:
  `sovereign-envelope-v1`, messageId, normalized sender handle, normalized recipient handle, sentAt, ephemeralPublicKey, nonce, ciphertext, tag.
- `GET /v1/messages?after=<ISO-8601>&limit=50`, `PATCH /v1/messages/:id/read`.
- `GET/POST /v1/channels`, `POST /v1/channels/:id/join`, `GET/POST /v1/channels/:id/posts`.
- `GET/POST /v1/feed`, `POST /v1/posts/:id/like|repost`, `GET/POST /v1/posts/:id/comments`.
- WebSocket `/v1/events`: connect, then send `{type:"auth", token:"..."}` as the first frame. Events include `ready`, `message.new`, `message.delivered`, and `message.read`.

The Android Vault and chat flows now use these endpoints for registration/login, encrypted direct-text send/receive, read acknowledgments, and WebSocket-triggered synchronization. The client uses P-256 ephemeral-static ECDH + HKDF-SHA256 + AES-256-GCM + ECDSA. A shared test-only vector at `app/src/test/resources/e2ee-v1-vector.json` is checked by Node and Robolectric tests; the Node test passes, but Android tests have not run in this environment. The protocol is not ratcheting, provides no forward secrecy after compromise of the recipient's long-term private key, and still needs live integration testing and independent review. Group chats and media are not integrated.
