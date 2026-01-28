# Aparajito (অপরাজিত) 🎬❤️
**A High-Performance, P2P Native Android App for Synchronized Cinema.**

---

## 📖 The Story
Honestly, this project was born out of pure frustration. Like many couples, my girlfriend and I relied on apps like **Rave** and **Teleparty** for our movie nights. But the experience was far from romantic—constant buffering, sync lag, and intrusive ads kept ruining the moment.

I realized the problem was the "Middleman." Most apps route video data through their own slow servers. I decided to build something that actually works. **Aparajito** (meaning "Undefeated") is the result—a solution that cuts out the noise and focuses on what matters: a seamless, shared experience.

---

## 🛠️ Developmental Journey
This project evolved from a web-based proof-of-concept ([movie-date-sync](https://github.com/snigdho179/movie-date-sync)). To provide a "Pro" experience, I migrated the logic into a native Android environment to unlock better hardware acceleration and lower latency using the **ExoPlayer** engine.

---


## 🚀 Why Aparajito?

* **Zero-Buffering Architecture:** Unlike traditional streaming apps, Aparajito uses **Peer-to-Peer (P2P)** technology. The video data flows directly between devices, eliminating server-side bottlenecks.
* **No Signups, No Friction:** We hate making accounts just to watch a movie. This app requires zero logins. Create a room, share the ID, and start watching.
* **Native Video Performance:** By integrating the **ExoPlayer** engine, the app handles 4K, MKV, and high-bitrate files that standard browsers often struggle with.
* **Synchronized YouTube Engine:** Beyond local files, I coded a custom sync layer for YouTube, ensuring both viewers are on the exact same frame, every time.
* **Privacy-First:** Since it's P2P, your "movie night" stays between you and your partner. No data is stored on external servers.

---

## 🛠️ Technical Stack

| Component | Technology | Purpose |
| :--- | :--- | :--- |
| **Mobile Core** | Java (Android Native) | High-performance OS integration. |
| **Video Engine** | Google ExoPlayer | Smooth playback of 4K/MKV formats. |
| **Sync Logic** | PeerJS (WebRTC) | Real-time P2P data and video synchronization. |
| **Interface** | HTML5 / CSS3 / JavaScript | Responsive and lightweight UI. |
| **CI/CD** | GitHub Actions | Automated APK generation and delivery. |

---

## 📦 How to Install

1.  Navigate to the **[Releases](https://github.com/snigdho179/Aparajito/releases)** section of this repository.
2.  Download the latest `Aparajito-Official-APK.zip`.
3.  Extract and install the `.apk` on your Android device (Enable "Install from Unknown Sources" if prompted).

---

## 🛠️ Developmental Journey
This project started as a web-based proof-of-concept (`movie-date-sync`). To provide a "Pro" experience, I migrated the logic into a native Android environment to unlock better hardware acceleration and lower latency for mobile users.

---

## 🤝 Acknowledgments
* **PeerJS** for the robust P2P framework.
* **Google's Media3** team for the ExoPlayer engine.
* **Anuska**, for being the best beta tester and the inspiration for this project.

---
## 🛡️ License
**All Rights Reserved.**
This code is available for viewing purposes only. You may not copy, modify, or distribute it without express written permission.
