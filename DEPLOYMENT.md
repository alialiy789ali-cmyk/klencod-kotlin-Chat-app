# Sovereign — Implementation Status and Server Deployment

> **Status:** Android now has gateway registration/login, encrypted local storage for keypairs and the access token, P-256 envelope encryption for direct text messages, inbox synchronization, read acknowledgments, and authenticated WebSocket-triggered refresh. Use the Vault screen to connect it to a deployed gateway. This is an initial integration—not a production-ready messenger. The gateway remains unverified in a live deployment; cross-platform tests/builds and independent protocol review are still required. Group messaging, media upload, WebRTC, and push notifications are not implemented.

## 1. Android App Capabilities

- Local text chats, channels, and feed actions persist in an encrypted Android Keystore-backed snapshot.
- Voice notes can be recorded and played in app-private storage. Audio files are not individually encrypted and are excluded from portable backups.
- Disappearing text messages sent through the gateway request a server-side expiry and are removed from the local app state after the timer. A recipient may have cached/copied content, cleanup is asynchronous, and secure physical erasure is not guaranteed.
- Password-protected `.svb` export/import uses AES-GCM. It includes local chat state and account private keys, but strips the bearer token and excludes voice-note media. Treat the backup password as highly sensitive.
- The Vault screen supports gateway registration/login. Passwords are transient; private keys and the active token are stored in the Android Keystore-backed encrypted snapshot.
- Direct text messages are encrypted/signed on-device, sent to the gateway, fetched/decrypted on-device, and can update read receipts. The current UI still contains demo/local data; only real registered peer accounts exchange messages.
- The A/B switch remains demo-only and does not create server accounts. The call screen remains a simulation; there is no WebRTC media transport or call signaling.

## 2. Gateway APIs

The Node.js service in `server/src` currently provides:

- `/health` readiness check.
- `/v1/auth/register`, `/v1/auth/login`, and `/v1/me` account endpoints.
- `/v1/users/:handle/key` for public identity/encryption keys.
- `/v1/messages` inbox/send APIs, `/v1/messages/:id/read` read acknowledgments, and authenticated WebSocket notifications at `/v1/events`.
- `/v1/channels` plus channel membership and admin-only publishing endpoints.
- `/v1/feed`, post reactions, and comments.

Messages are accepted only as signed envelopes with a P-256 ephemeral public key, AES-GCM nonce/tag/ciphertext, timestamp, and ECDSA signature. The server validates the envelope and sender signature but does not perform encryption or decryption. The Android client now uses a matching P-256 ECDH/HKDF/AES-GCM/ECDSA envelope and integrates the account, send, inbox, read-receipt, and WebSocket flows. A shared test-only crypto vector now exercises Node verification/decryption and Android decryption; the Node side passes, but the Android test could not be run in this environment. A live API/PostgreSQL end-to-end test is still outstanding.

## 3. Deploy the Gateway

Requirements: a Linux host, Docker Compose v2, a public DNS name pointed at the host, and inbound TCP ports 80 and 443. Caddy provisions HTTPS certificates automatically.

```bash
cd server
cp .env.example .env
# Edit .env: set a strong Postgres password, DATABASE_URL, a random JWT_SECRET,
# and SOVEREIGN_DOMAIN to the DNS name that points to this server.
openssl rand -hex 32   # use the output for JWT_SECRET
docker compose up -d --build
docker compose ps
curl https://YOUR_DOMAIN/health
```

Use URL-encoded special characters if the PostgreSQL password contains URL-sensitive characters. The gateway and database are on a private Docker network; only Caddy publishes ports 80/443. Do not publish PostgreSQL directly. Keep `.env` private and replace every `CHANGE_ME` value. Configure `CORS_ORIGINS` only if a browser client is used; native Android does not require CORS.

For local API development, install Node.js 20+, start PostgreSQL, set `DATABASE_URL` and a `JWT_SECRET` in the environment, then run `npm ci && npm start` from `server/`. The gateway listens on port 8080 by default.

## 4. Backup and Operations

- Android backup/restore is available from the Vault screen. It requires a password of at least 10 characters. Voice-note media is excluded.
- Android Auto Backup is disabled because the local state key is device-bound in Android Keystore.
- PostgreSQL backup/restore (run from `server/`):
  ```bash
  docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > sovereign_db_backup.sql
  cat sovereign_db_backup.sql | docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
  ```
  Compose does not load `.env` values into the shell running those commands automatically; set the two shell variables first or use their literal configured names.
- Monitor with `docker compose logs -f sovereign-gateway caddy` and `docker compose ps`.

## 5. Security Limitations and Remaining Work

- Android integration is initial: only direct text messages are sent remotely. Groups and all media remain local/unsupported; account keys cannot be rotated or recovered unless the user kept a password-encrypted backup. A lost backup means those old messages cannot be decrypted.
- The server verifies registered-key ECDSA signatures and stores opaque message envelopes. It cannot prove that account registration keys belong to a real-world identity; users need a proper verification UX.
- Account and envelope flows are wired, but key verification UX, key-change warnings/rotation, a live API/PostgreSQL integration test, and independent security review remain. A shared Node/Android crypto vector is checked in; only its Node test ran here because Java/Gradle are unavailable. The protocol is not Signal/Double Ratchet and does not provide forward secrecy against later compromise of a recipient's long-term key.
- The gateway currently uses a single-process in-memory WebSocket fan-out; notifications may be missed when disconnected, and multi-instance deployments need a shared event bus/queue.
- Rate limiting is in-memory and basic. Add durable account-level limits, abuse controls, audit logging that excludes secrets, monitoring, database migration tooling, and independent penetration testing before public launch.
- No WebRTC signaling/media, TURN integration, push notifications, file/media upload pipeline, or remote self-destruct guarantees are implemented.
