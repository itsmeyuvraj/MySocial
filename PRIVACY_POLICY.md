# Privacy Policy for MySocial

**Effective Date:** November 8, 2026  
**Application:** MySocial — Decentralized Offline Mesh Social Network  
**Developer:** Yuvraj Poudyal  
**Repository:** [github.com/itsmeyuvraj/MySocial](https://github.com/itsmeyuvraj/MySocial)

---

## 1. Core Principle: 100% Offline, Zero Cloud Telemetry

MySocial was engineered from the ground up as a **serverless, peer-to-peer (P2P) decentralized mesh network**. 

* **No Central Servers:** There are no backend databases, company servers, cloud logins, or remote relays operated by the developer or any third party.
* **No Analytics or Trackers:** MySocial contains **zero tracking SDKs, zero advertising networks, zero user telemetry, and zero third-party analytics libraries**.
* **No Personal Account Registration:** You do not register with an email address, phone number, Google account, or real name. Your identity is a purely local **Ed25519 cryptographic key pair** generated on your device.

---

## 2. Information Collected and Processed Locally

All data created or received by MySocial resides strictly on your physical Android device and is transmitted directly between nearby devices over physical radio waves (Bluetooth Low Energy and local Wi-Fi):

1. **Cryptographic Identity:**
   - When you set up MySocial, an Ed25519 public/private key pair is generated locally on your device.
   - Your private key never leaves your device's internal storage / Android Keystore.
   - Your public key is hashed using Crockford Base32 into an 8-character Base32 Badge ID (`#XXXX-XXXX`) to identify you to peers on the local mesh.

2. **User-Generated Content (UGC):**
   - Posts, photos, comments, poll votes, and 24-hour stories you publish are cryptographically signed with your private key and stored in a local SQLite/Room database on your device.
   - These are broadcast directly to nearby devices in physical radio range over Bluetooth Low Energy (BLE) and local Wi-Fi LAN / Wi-Fi Aware.

3. **Private Direct Messages:**
   - 1-on-1 private messages between verified friends are end-to-end encrypted (E2EE) using ephemeral X25519 Diffie-Hellman key exchange and authenticated AES-256-GCM encryption.
   - Intermediate "data mule" peers relaying encrypted packets have zero knowledge of message contents, sender identifiers, or recipients.

4. **Hardware Radio Usage:**
   - **Bluetooth & Nearby Devices:** Used strictly to discover and establish short-range peer-to-peer data links with nearby phones. MySocial uses the `neverForLocation` flag on Android 12+ and never records your physical GPS coordinates.
   - **Wi-Fi & Wi-Fi Aware:** Used strictly for local TCP socket synchronization across nearby peers.
   - **Microphone:** Used strictly when you hold the Push-to-Talk (PTT) Walkie-Talkie button or record a voice note in direct chat. Audio is never recorded in the background.
   - **Camera:** Used strictly when you scan a friend's in-person Web of Trust QR code or capture a photo post.

---

## 3. Data Retention and Deletion

* **Ephemeral TTL Posts:** Posts configured with a Time-To-Live (1h, 6h, 24h) and 24-Hour Stories are automatically purged and deleted from your device storage once expired.
* **Data Mule Cache Limit:** Media cached for un-followed strangers is automatically capped via an in-app ring buffer (50 MB – 250 MB) and auto-evicts oldest content.
* **Complete Data Eradication:** Uninstalling MySocial or clearing app storage in Android System Settings permanently erases all cryptographic keys, databases, messages, and media. There are no cloud backups or remote traces.

---

## 4. Content Moderation & Reporting

MySocial provides local content moderation tools:
* **Post Reporting & Hiding:** Users can report objectionable, abusive, or spam content locally, which immediately hides the post and prevents further re-gossiping from their device.
* **Peer Blocking & Sybil Eviction:** Users can block any author with 1 tap. If 3 mutual verified friends block an author, their gossip packets are dropped at the physical radio transport layer.

---

## 5. Third-Party Disclosures

MySocial does not sell, trade, rent, or transfer any user data to any external parties, governments, or advertisers.

---

## 6. Contact

For technical questions, open-source audit, or vulnerability reports:
* **GitHub Issues:** [github.com/itsmeyuvraj/MySocial/issues](https://github.com/itsmeyuvraj/MySocial/issues)
* **Lead Developer:** Yuvraj Poudyal

