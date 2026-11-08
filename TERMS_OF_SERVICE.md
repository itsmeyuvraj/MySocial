# Terms of Service & Community Guidelines

**Effective Date:** November 8, 2026  
**Application:** MySocial — Decentralized Offline Mesh Social Network  
**Developer:** Yuvraj Poudyal  
**Repository:** [github.com/itsmeyuvraj/MySocial](https://github.com/itsmeyuvraj/MySocial)

---

## 1. Acceptance of Terms

By installing, launching, or using MySocial, you agree to these Terms of Service and Community Guidelines. If you do not agree to these terms, do not install or use the application.

---

## 2. Nature of Decentralized Peer-to-Peer Software

MySocial is an open-source, serverless peer-to-peer communications tool. It operates by broadcasting cryptographic data packets over local radio frequencies (Bluetooth Low Energy and local Wi-Fi) between devices in physical proximity.

* The developer does not operate servers, does not intermediate communications, and does not possess administrative backdoors or master cryptographic decryption keys.
* You are solely responsible for the cryptographic keys generated on your device and all content published under your key pair identity.

---

## 3. Community Guidelines & Prohibited Content

To maintain a safe environment for all participants in local radio mesh spaces, you agree not to use MySocial to publish or transmit:

1. **Illegal or Harmful Content:** Child sexual abuse material (CSAM), non-consensual intimate imagery, terrorism, human trafficking, or instructions for self-harm or violent crimes.
2. **Harassment, Threats & Stalking:** Explicit threats of violence, targeted harassment, hate speech inciting violence against protected groups, or doxxing private personal information.
3. **Malicious Mesh Abuse & Flooding:** Attempts to degrade the physical mesh network through radio flooding, Hashcash Proof-of-Work bypass attacks, or denial-of-service spam.
4. **Fraud & Impersonation:** Deceptive impersonation of public emergency services, law enforcement, or fraudulent misrepresentation of disaster relief signals.

---

## 4. Enforcement & Content Moderation

Because MySocial operates without centralized servers, enforcement is executed via local and consensus-driven moderation mechanisms:

* **Local Post Reporting & Hiding:** Any user may flag a post. Reporting a post immediately hides the content on the reporter's device and instructs the local gossip engine not to relay or re-broadcast the post to other peers.
* **Peer Blocking & Packet Dropping:** Users can block any author. Blocked authors have their broadcast packets immediately dropped at the radio transport layer before being stored in local SQLite databases.
* **Web of Trust Sybil Eviction:** If 3 mutual verified friends block an author, that author is marked as evicted from the mutual trust cluster.

---

## 5. Disclaimer of Warranties

MySocial is provided "AS IS" and "AS AVAILABLE" without warranties of any kind, whether express or implied. The developer does not warrant that physical radio mesh connectivity will be uninterrupted or error-free in all geographic environments, weather conditions, or hardware configurations.

Emergency channels (e.g. `#emergency-sos`) and the Compass Direction Finder are peer-to-peer crisis coordination aids; they are not certified replacements for official 911/112 public safety answering points when commercial cellular service is available.

---

## 6. Limitation of Liability

In no event shall the author or copyright holders be liable for any claim, damages, or other liability arising from, out of, or in connection with the software or the use or other dealings in the software.

