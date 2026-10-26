# Sovereign Communication Super App — "Sovereign" (السيادي)

An independent, sovereign communication and social ecosystem uniting direct encrypted messaging, broad community channels, a decentralized micro-blogging timeline, and sovereign audio/video calling. Built with self-hosted REST and real-time WebSocket connectivity, local encryption vaults, and deep bilingual Arabic (RTL) & English (LTR) design.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and aesthetic choices were confirmed during Phase 1:
>
> - **Backend & Persistence**: Self-hosted REST and WebSocket protocol engine, paired with an encrypted local Room storage engine and configurable self-host server endpoint (with embedded sovereign gateway for standalone and zero-config self-hosted deployments).
> - **Visual Identity**: Sovereign Dark theme featuring deep OLED slate, obsidian surfaces, cryptographic emerald `#10B981` accents, and crisp typography.
> - **Primary Landing Experience**: Unified Sovereign Hub with seamless tabbed switching between Conversations (Chats & Groups), Sovereign Channels & Communities, and the Public Micro-Feed (Posts, Reposts, Hashtags).
> - **Self-Hosted Infrastructure Manifest**: Complete Docker Compose and server deployment guides accompanying the Android client for full self-sovereign server hosting (PostgreSQL, Redis, MinIO/S3 compatible storage, and WebSocket gateway).

---

### 1. Overview & Core Concept

- **What It Does**: A sovereign, all-in-one communications app combining the real-time private messaging and voice notes of Signal/WhatsApp, the high-scale channels and bot-ready communities of Telegram, and the public expression and micro-blogging discourse of X/Twitter.
- **Target Audience / Persona**: Users, organizations, and digital privacy advocates who demand full data sovereignty, zero dependence on proprietary big-tech surveillance silos, and complete self-hosting freedom.
- **Key Value**: Uncompromising digital sovereignty—users own their identities, cryptographic keys, self-hosted endpoints, and communication pipelines without sacrificing fluid modern usability or aesthetic elegance.

---

### 2. User Experience & Visual Design

#### Key User Flows

1. **Sovereign Onboarding & Identity Generation**:
   - The user selects their local identity alias, generates their local cryptographic keys, and configures their self-hosted server node URL (or uses the built-in sovereign local sandbox node).
   - Instant cryptographic fingerprint badge generation and session security review.

2. **Unified Hub Navigation**:
   - **Messages Tab (المراسلات)**: Direct 1-on-1 end-to-end encrypted chats, group rooms, voice notes, media attachments, delivery/read receipts, typing indicators, and disappearing message toggles.
   - **Channels & Communities Tab (القنوات والمجتمعات)**: Public broadcast channels, moderated discussion topics, media galleries, subscriber count, and broadcast publishing for authorized admins.
   - **Sovereign Feed Tab (الموجز العام)**: Chronological micro-blogging posts with hashtags, media cards, likes, reposts, quotes, nested discussion replies, and trending topics.
   - **Calls & Encrypted Voice Tab (المكالمات)**: Direct WebRTC peer audio/video calling interface with microphone mute, camera toggle, and cryptographic verification fingerprint exchange.
   - **Vault & Node Settings (إعدادات السيادة)**: Server endpoint switching, E2EE key export/audit, self-hosted sync status, local cache cleaner, and theme/language toggles.

3. **Active Conversation & Messaging Flow**:
   - Rich message input bar with voice recording, file/photo picker, emoji bar, and ephemeral timer toggle.
   - Bubble layout with encrypted shield badges, delivery checkmarks (sent, delivered, read), and reply quotes.

4. **Public Posting & Social Interaction Flow**:
   - Rich composer with hashtag suggestions, character counter, media previews, and sovereign identity tag.
   - Interactive timeline cards supporting instant bookmarking, re-sovereign (repost), and threaded comments.

#### Visual Identity & Theme

- **Aesthetic Direction**: Sovereign Luxury Dark — ultra-modern, high-security command center aesthetic. Clean obsidian and slate layers with sharp cryptographic emerald accents, avoiding generic flat gray AI slop.
- **Color Palette**:
  - `Surface (Dark)`: `#0B0F17` (Deep Obsidian Void)
  - `Surface Elevated / Cards`: `#131B2E` (Deep Slate Tonal)
  - `Primary / Accents`: `#10B981` (Cryptographic Emerald)
  - `Primary Dim / Glow`: `#059669` (Dark Emerald)
  - `Secondary Accent`: `#38BDF8` (Cyan Signal Highlight)
  - `Text Primary`: `#F8FAFC` (Pure Titanium White)
  - `Text Secondary`: `#94A3B8` (Muted Slate)
  - `Critical / Destruction`: `#F43F5E` (Sovereign Rose)
- **Typography & Hierarchy**:
  - Modern geometric sans-serif with optimized Arabic glyph rendering and font scaling.
  - Generous line height for Arabic RTL reading comfort and crisp alphanumeric hashes for crypto fingerprints.
- **Component Styling & Layout**:
  - Full edge-to-edge layout with safe area status bar and navigation bar insets.
  - Floating action controls with subtle glowing rim lights, interactive M3 ripple feedback, and 48dp minimum touch targets.
  - Native RTL directionality toggle with bidirectional support.

---

### 3. Key Product Decisions & Trade-Offs

- **Self-Hosted Engine with Standalone Fallback**:
  - *Chosen Approach*: The client architecture communicates with configurable self-hosted REST/WebSocket endpoints and includes an embedded local simulation gateway that functions out-of-the-box without requiring an external server running on day one.
  - *Why*: Allows immediate testing and execution in the cloud Android container while providing the exact schema, serialization, and network contracts for self-hosted backend production.
- **Room Database with Local Vault Storage**:
  - *Chosen Approach*: Jetpack Room with Kotlin Coroutines and StateFlow for offline-first messaging, local caching, drafted posts, and cryptographic identity keys.
  - *Why*: Zero network latency for local conversation history, total privacy, and resilient offline drafting.
- **Simulated Real-Time WebRTC and WebSocket Dispatcher**:
  - *Chosen Approach*: Robust OkHttp WebSocket connection manager with automatic reconnect, ping/pong heartbeats, and in-memory event bus.
  - *Why*: Ensures seamless bidirectional message exchange, typing status, and incoming call signaling.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        SOVEREIGN SUPER APP UI                          │
├────────────────────┬────────────────────┬──────────────────────────────┤
│    Messages Tab    │   Channels Tab     │         Feed Tab             │
│ (Chats, Voice, P2P)│(Broadcasts, Admin) │(Posts, Reposts, Hashtags)    │
└─────────┬──────────┴─────────┬──────────┴──────────────┬───────────────┘
          │                    │                         │
          ▼                    ▼                         ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    SOVEREIGN VIEWMODEL & STATE LAYER                   │
│   • Auth & Identity StateFlow    • Real-time Chat & Group StateFlow    │
│   • Social Feed & Post StateFlow • Node & Network Config StateFlow     │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        CORE DOMAIN REPOSITORY                          │
│   • SovereignRepository (Single source of truth)                       │
│   • CryptoEngine (AES-256-GCM / SHA-256 Key Derivation & Fingerprints) │
└───────────────────┬──────────────────────────────┬─────────────────────┘
                    │                              │
                    ▼                              ▼
┌──────────────────────────────────────┐ ┌───────────────────────────────┐
│         LOCAL STORAGE (ROOM)         │ │    NETWORK & GATEWAY LAYER    │
│  • User Profiles & Identity Keys     │ │  • OkHttp & Retrofit REST     │
│  • Encrypted Messages & Threads      │ │  • WebSocket Event Gateway    │
│  • Channels & Memberships            │ │  • Self-Hosted Server Switcher│
│  • Timeline Posts & Interactions     │ │  • WebRTC Call Signaling Sim  │
└──────────────────────────────────────┘ └───────────────────────────────┘
```

#### Data Model & State Entities

- `UserIdentity`: Unique handle `@username`, display name, avatar color/icon, public key fingerprint, verified status, node URL.
- `MessageEntity`: UUID, threadId, senderHandle, recipientHandle, content, timestamp, status (SENT, DELIVERED, READ), isEncrypted, mediaType (TEXT, AUDIO, IMAGE, FILE), selfDestructSeconds.
- `ChannelEntity`: Channel ID, title, handle, description, subscriberCount, avatar, isPublic, pinnedMessageId.
- `PostEntity`: Post ID, authorHandle, authorName, authorAvatar, content, timestamp, mediaUrl, likesCount, repostsCount, commentsCount, isLiked, isReposted, hashtags.
- `CallSession`: Call ID, peerHandle, peerName, callType (AUDIO, VIDEO), status (RINGING, CONNECTED, ENDED), durationSeconds.
