# 🌐 MySocial

[![Platform: Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android)](https://www.android.com/)
[![Kotlin: 2.0](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20(M3)-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Bluetooth 5.0+](https://img.shields.io/badge/Transport-Bluetooth%205.0%2B%20(LE%202M)-0082FC.svg?style=flat&logo=bluetooth)](https://www.bluetooth.com/)
[![Local Wi-Fi mDNS](https://img.shields.io/badge/Transport-Wi--Fi%20LAN%20mDNS-FF6F00.svg?style=flat&logo=wi-fi)](https://developer.android.com/training/connect-devices-wirelessly/nsd)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg?style=flat)](LICENSE)

A decentralized, serverless **peer-to-peer (P2P) mesh social network** for Android. Operates with **zero cloud servers** and **zero cellular data**, distributing immutable signed posts, comments, reactions, and chunked media attachments across nearby devices via epidemic gossip protocols over **Bluetooth 5.0+ Extended Advertising** and **Local Wi-Fi / Hotspot mDNS**.

---

## 🌟 Key Highlights

- **Serverless & Censorship-Resistant:** No central servers, DNS dependencies, or cloud relays. Every device acts as an autonomous node that stores, verifies, and propagates data.
- **Dual-Transport Hybrid Mesh:**
  - **Bluetooth 5.0+ Extended Advertising & L2CAP CoC:** Leverages secondary **PHY LE 2M** (2 Mbps) and extended advertising sets for sub-second proximity discovery, broadcasting dynamic L2CAP PSMs for fast-path direct socket connections without GATT discovery overhead. Includes graceful fallback to LE 1M and legacy 31-byte beacons.
  - **Local Wi-Fi / Hotspot LAN:** Zero-configuration service discovery using Android `NsdManager` (mDNS Bonjour `_mysocial-mesh._tcp`) with raw high-throughput TCP streaming sockets (`FrameCodec`).
- **Cryptographic Zero-Trust Identity:** Every user identity is anchored to an **Ed25519** keypair generated on-device with Android Keystore. Every post, comment, and reaction is cryptographically signed and content-addressed via SHA-256.
- **Anti-Entropy Epidemic Gossip Sync:** Vector-clock log synchronization and Bloom-filter-inspired log exchange (`GossipSyncEngine`). Messages hop across nodes to bridge network gaps.
- **Chunked Blob Transfer Engine:** Content-addressed streaming distribution for large images and attachments. Slices payloads into verified chunks with direct-to-disk streaming assembly and chunk-by-chunk SHA-256 validation.
- **Direct End-to-End Encrypted (E2EE) Private Messaging:** 1-on-1 private channels between verified friends using birational Ed25519-to-Curve25519 (X25519) coordinate mapping, ephemeral Diffie-Hellman key exchange, authenticated AES-256-GCM encryption, store-and-forward mesh mule routing, native AAC voice notes, and photo attachments.
- **Zero-Internet P2P APK Auto-Update System:** Autonomous background update propagation across nearby devices. Devices automatically discover newer build numbers during the gossip handshake, silently stream the signed APK over local TCP socket connections without internet, verify cryptographic keystore signatures, and prompt for a seamless 1-tap in-place update.
- **Dedicated Single-Purpose Composer Modes:** Clean separation of concerns with distinct single-purpose composers: distraction-free text-only posts ("What's on your mind?"), dedicated photo posts with gallery picker & captions, interactive local polls with real-time tallying, and high-priority emergency broadcast alerts.
- **Modern Jetpack Compose UI & People Radar:** Premium social feed, slide-up comment sheets, animated sonar radar with proximity indicators, dedicated `PeerProfileScreen` displaying live radio range & recent posts, and zero-trust in-person QR friend verification.
- **QR Proximity Verification:** In-app QR code identity generator and real-time CameraX QR scanner for zero-trust out-of-band friend verification.
- **In-App Auto-Update & CI/CD Engine:** Automated GitHub Actions release pipeline (`auto_release.yml`) that builds and publishes signed release APKs on push. In-app `UpdateManager` with background checks, categorized changelog viewer (`ChangelogView`), live download progress tracking, and Android `FileProvider` package installer prompt.

---

## 🏗️ System Architecture

```
                                  ┌─────────────────────────────┐
                                  │    Jetpack Compose UI (M3)  │
                                  │ Feed • Radar • Profile • QR │
                                  └──────────────┬──────────────┘
                                                 │
                                                 ▼
                                  ┌─────────────────────────────┐
                                  │      MeshRepository         │
                                  │ TTL Pruning • Post Feeds    │
                                  └──────────────┬──────────────┘
                                                 │
                                                 ▼
                       ┌─────────────────────────────────────────────────┐
                       │                MeshSyncService                  │
                       │   Android Foreground Service • LifeCycle Watch  │
                       └──────────────┬───────────────────┬──────────────┘
                                      │                   │
                     ┌────────────────┴─────┐       ┌─────┴────────────────┐
                     │   GossipSyncEngine   │       │  BlobTransferEngine  │
                     │ Vector Clock Anti-   │       │ Content-Addressed    │
                     │ Entropy Log Sync     │       │ Chunked Media Stream │
                     └──────────┬───────────┘       └─────┬────────────────┘
                                │                         │
                                └────────────┬────────────┘
                                             │
                      ┌──────────────────────┴──────────────────────┐
                      ▼                                             ▼
       ┌──────────────────────────────┐              ┌──────────────────────────────┐
       │       BleMeshTransport       │              │      MdnsWifiTransport       │
       │ • BT 5.0+ Extended Adv (2M)  │              │ • Android NsdManager (mDNS)  │
       │ • Dynamic L2CAP CoC PSM      │              │ • Raw TCP Sockets            │
       │ • Fast-Path Direct Socket    │              │ • FrameCodec 4-Byte Prefix   │
       │ • Fallback GATT Server/Client│              │ • Subnet Peer Auto-Connect   │
       └──────────────────────────────┘              └──────────────────────────────┘
```

---

## 📡 Wireless Mesh Protocols

### 1. Bluetooth 5.0+ Extended Advertising & L2CAP CoC
In conventional BLE social applications, discovery is bottlenecked by 31-byte legacy advertising packets, requiring slow GATT connection roundtrips, MTU renegotiation, and service discovery. 

MySocial implements modern Bluetooth 5.0+ Extended Advertising:
1. **Extended Advertising Sets (`AdvertisingSetParameters`):** Broadcasts high-capacity identity payloads on secondary advertising channels using **PHY LE 2M** (2 Mbps) for fast discovery.
2. **Dynamic L2CAP PSM Broadcast:** The local node opens an insecure L2CAP Connection-Oriented Channel (`listenUsingInsecureL2capChannel`) and embeds its dynamic PSM port directly in the extended advertising payload (`BleExtendedPayload`).
3. **Fast-Path Connection:** Scanners detect the advertised PSM and directly establish a high-throughput BLE L2CAP CoC socket (`device.createInsecureL2capChannel(psm)`), bypassing GATT service discovery, MTU negotiation, and characteristic reads entirely.
4. **Hardware Budget Clamping:** Automatically measures the controller's `leMaximumAdvertisingDataLength` (up to 1650 bytes) and dynamically clamps payload budgets, preventing driver overflow crashes.
5. **Multi-Tier Graceful Fallback:** If secondary 2M PHY is unsupported by the chipset, automatically degrades to Extended Advertising on 1M PHY, and finally to Legacy 31-byte advertising beacons and GATT chunked transport (`BleChunker`).

### 2. Local Wi-Fi & Hotspot Transport
When devices are on the same Wi-Fi network, Portable Hotspot, or ad-hoc Wi-Fi direct connection:
- Discovers peers using Android `NsdManager` with service type `_mysocial-mesh._tcp`.
- Establishes direct TCP connections over length-prefixed stream framing (`FrameCodec`: `[4-byte Length][JSON Payload]`).
- Achieves full LAN throughput for bulk log synchronization and high-resolution photo blob transfers.

### 3. Zero-Internet P2P APK Auto-Update Cascading
To enable viral offline software distribution without app stores:
1. **Embedded Micro-Server:** Every running node hosts its installed base APK (`context.applicationInfo.sourceDir`) on port `42531` using an embedded non-blocking `ServerSocket` HTTP server (`OfflineApkServer`).
2. **Handshake Build Negotiation:** Nodes exchange their `appVersionCode`, `appVersionName`, and local APK server port during the initial `HelloVector` handshake.
3. **Background Socket Streaming:** If a connected peer runs a higher build number (`remoteVersionCode > localVersionCode`), the receiver automatically initiates an asynchronous background streaming download over the local TCP socket into the application cache directory (`updates/`).
4. **Cryptographic Keystore Signature Verification:** Before presenting the update to the user, the app parses the downloaded archive via `PackageManager.getPackageArchiveInfo` and verifies that the developer signing certificates match the installed application's certificates. Any modified, corrupted, or malicious APK is immediately discarded.
5. **1-Tap In-Place Update:** The UI surfaces a `PeerUpdateReadyBanner` and a high-priority Android notification. Tapping "Update" passes a secure `FileProvider` URI to Android's native `PackageInstaller` (`ACTION_VIEW`), updating the app in-place while completely preserving Room DB posts, identity keys, and chat history.

---

## 🔐 Cryptography & Identity

- **Ed25519 Signing:** Every user possesses a persistent Ed25519 private key generated via BouncyCastle and protected by the Android Keystore.
- **Immutable Log Ledger:** Every post, comment, like, and profile update is an append-only log entry containing:
  ```json
  {
    "authorId": "@<base64-pubkey>.ed25519",
    "sequence": 42,
    "previousHash": "sha256-hex...",
    "timestamp": 1729990000000,
    "payload": { ... },
    "signature": "base64-ed25519-sig..."
  }
  ```
- **Mutual Friend Trust Graph:** Friends are established by bidirectional follow relationships (or QR handshake verification). Users can choose **Public** or **Friends-Only** post audience visibility. Verified mutual friends receive blue checkmarks across the feed and radar.

---

## ⚡ Performance & Reactive Concurrency

- **O(1) Screen-Level Like Reactivity:** Lifted user reaction state (`userLikedPostHashes: StateFlow<Set<String>>`) to ViewModel scopes (`FeedViewModel`, `IdentityViewModel`, `PeerProfileScreen`), replacing $O(N)$ per-post Room cursor subscriptions with a single screen-level database flow and $O(1)$ in-memory lookups.
- **Optimized Composite SQLite Indices:** Added multi-column covering indices for high-frequency queries:
  - `posts_view(replyToHash, isSos, timestamp)` and `posts_view(authorId, replyToHash, timestamp)` for sort-free feed retrieval.
  - `direct_messages(conversationPeerId, timestamp)` and `direct_messages(isRead, isOutgoing)` for instant chat ordering and unread counts.
  - `reactions_view(authorId, pollOptionIndex)` for index-only reaction scans.
- **Non-Destructive Migration (v6 → v7):** Safe, atomic database index upgrades via `MIGRATION_6_7` preserving all local user posts, messages, and cryptographic keys.
- **Atomic Batch Log Projection:** Anti-entropy sync validates and commits entire batches in a single SQLite transaction with coalesced vector clock updates, minimizing disk `fsync` overhead and UI recomposition triggers.
- **Hardware-Delegated Duty Cycling:** Uses Bluetooth controller-level duty cycling (`ScanSettings.SCAN_MODE_BALANCED`) and bounded LRU caches for public key parameters and signature verification to avoid heap bloat and CPU thrashing in dense crowds.

---

## 📁 Repository Structure

```
app/src/main/java/com/mysocial/mesh/
├── crypto/
│   ├── CryptoManager.kt           # Ed25519 key generation, digital signing, and verification
│   └── E2eeManager.kt             # Ephemeral X25519 ECDH + AES-256-GCM End-to-End Encryption
├── transport/
│   ├── BleMeshTransport.kt        # BLE 5.0+ Extended Advertising, 2M PHY, L2CAP CoC, GATT
│   ├── MdnsWifiTransport.kt       # Wi-Fi LAN mDNS discovery & direct TCP socket connections
│   └── DiscoveredPeer.kt          # Discovered peer state, signal RSSI, transport & PHY metadata
├── protocol/
│   ├── BleExtendedPayload.kt      # BT 5.0+ wire protocol, budget clamping, binary parsing
│   ├── BleChunker.kt              # MTU packet chunking & reassembly for legacy GATT
│   ├── FrameCodec.kt              # 4-byte length-prefixed framing for TCP and L2CAP sockets
│   ├── GossipSyncEngine.kt        # Anti-entropy epidemic gossip, log exchange, hop limits
│   ├── GossipMessage.kt           # Wire protocol messages (Hello, Have, Request, Response, Blob)
│   └── PeerConnection.kt          # Transport-agnostic peer connection abstraction
├── data/
│   ├── MeshDatabase.kt            # Room DB (Posts, Comments, Reactions, Blobs, Direct Messages)
│   ├── MeshRepository.kt          # Unified data access, TTL pruner, reactive feeds, receipts
│   ├── BlobEntities.kt & BlobDao  # Content-addressed chunked blob metadata and storage
│   └── MediaProcessor.kt          # Image downscaling, WebP compression, audio, and SHA-256 chunking
├── service/
│   └── MeshSyncService.kt         # Android Foreground Service for persistent mesh synchronization
├── update/
│   └── PeerApkUpdateManager.kt    # Zero-internet P2P APK auto-update engine & signature verification
├── utils/
│   ├── AudioRecorderPlayer.kt     # Native AAC/MPEG-4 voice recording and playback engine
│   ├── OfflineApkServer.kt        # Embedded ServerSocket HTTP server serving base APK on port 42531
│   ├── PeerNotificationHelper.kt  # Android notification channels (Nearby radar & Direct messages)
│   └── UpdateManager.kt           # GitHub Releases API, streaming APK download, FileProvider install
└── ui/
    ├── feed/                      # Timeline feed, likes, post reactions, audience filter
    ├── messages/                  # E2EE 1-on-1 private chat, photos, voice notes, delivery/read ticks
    ├── composer/                  # Dedicated single-purpose composers (Text, Photo, Poll, Alert)
    ├── radar/                     # Animated radar showing nearby BLE & Wi-Fi peers
    ├── profile/                   # PeerProfileScreen (peer bio, BBID copy, radio range, recent posts)
    ├── identity/                  # User self-profile, cryptographic fingerprint, display name
    ├── settings/                  # SettingsScreen, sync intervals, dark mode, update checker
    ├── qr/                        # QR code identity generator & CameraX real-time QR scanner
    └── components/                # PostCard, PostCommentsSheet, PeerCard, UpdateDialog, ChangelogView
```

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Ladybug / Iguana or newer
- JDK 17 or higher
- Android SDK (API Level 26 to 34)
- Physical Android device with Bluetooth 5.0+ and Wi-Fi enabled (recommended for mesh testing)

### Local Build Commands

```bash
# Clone the repository
git clone git@github.com:itsmeyuvraj/MySocial.git
cd MySocial

# Build Debug APK
./gradlew assembleDebug

# Run Unit Tests
./gradlew testDebugUnitTest

# Install to connected physical phone or emulator via ADB
./gradlew installDebug

# Launch MainActivity on the device
adb shell am start -n com.mysocial.mesh/.ui.MainActivity
```

---

## 🧪 Testing

The codebase includes comprehensive unit tests verifying cryptographic integrity, binary wire formats, and boundary constraints:

- `BleExtendedPayloadTest`: Validates full binary roundtrip encoding, legacy 4-byte fallback detection, controller capacity budget enforcement, and corruption recovery.
- `BleChunkerTest`: Tests MTU-constrained packet slicing and out-of-order reassembly.
- `BlobTransferEngineTest`: Verifies content-addressed media chunking, SHA-256 checksums, and streaming disk writes.

Run the test suite with:
```bash
./gradlew testDebugUnitTest
```

---

## 📄 License

This project is open-source software licensed under the [MIT License](LICENSE).
