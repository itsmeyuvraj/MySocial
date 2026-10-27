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
- **Modern Jetpack Compose UI:** Premium social feed with Facebook-style slide-up comment sheets, interactive animated radar with transport & RSSI indicators, bidirectional mutual friend trust graph, and verified blue checkmark badges.
- **QR Proximity Verification:** In-app QR code identity generator and real-time CameraX QR scanner for zero-trust out-of-band friend verification.

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

## 📁 Repository Structure

```
app/src/main/java/com/mysocial/mesh/
├── crypto/
│   └── CryptoManager.kt           # Ed25519 key generation, digital signing, and verification
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
│   ├── MeshDatabase.kt            # Room DB (Posts, Comments, Reactions, Blobs, Peers)
│   ├── MeshRepository.kt          # Unified data access, TTL pruner, reactive feeds
│   ├── BlobEntities.kt & BlobDao  # Content-addressed chunked blob metadata and storage
│   └── MediaProcessor.kt          # Image scaling, JPEG compression, and SHA-256 chunking
├── service/
│   └── MeshSyncService.kt         # Android Foreground Service for persistent mesh synchronization
└── ui/
    ├── feed/                      # Timeline feed, likes, post reactions, audience filter
    ├── composer/                  # Post composer with attachment picker and media processor
    ├── radar/                     # Animated radar showing nearby BLE & Wi-Fi peers
    ├── identity/                  # User profile, cryptographic fingerprint, display name
    ├── qr/                        # QR code identity generator & CameraX real-time QR scanner
    └── components/                # PostCard, PostCommentsSheet, PeerCard, VerifiedBadge
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
