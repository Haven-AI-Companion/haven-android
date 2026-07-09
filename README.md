# Haven Mobile 🌸

**Haven Mobile** is the native Android client application for the **Haven AI Companion** ecosystem. Written entirely in **Kotlin** and built with **Jetpack Compose**, it provides a premium, responsive, and private mobile interface to chat and speak with your custom companions hosted on your self-hosted **Haven Server**.

<p align="center">
  <img src="app/src/main/res/drawable/haven_logo.png" alt="Haven Logo" width="200">
</p>

---

## 🚀 Key Features

*   **Native & Low-Latency UI**: Built with Jetpack Compose following modern Material 3 design systems.
*   **Tavern Character Card Support**: Import companions instantly by picking standard Tavern character card PNGs (which carry embedded V1/V2 metadata).
*   **Offline-First Local Cache**: Built-in **Room Database** caches messages, active chats, group threads, companion cards, memory vaults, and diary entries locally on your device.
*   **Hands-Free Voice Calls**:
    *   Integrates **Silero VAD (Voice Activity Detection)** locally via ONNX Runtime Mobile.
    *   Automatically detects when you start and stop speaking, sending raw audio streams to the C# backend WebSocket endpoint `/ws/voice/{characterId}` for hands-free conversations.
*   **Mesh VPN Ready**: Zero-config remote access over private networks (like Tailscale).
*   **Secure Storage**: Local developer-signed signatures protect local backups and token authorization settings.

---

## 🛠️ Getting Started

### 1. Requirements
*   Android 8.0 (API Level 26) or higher.
*   An active instance of **[Haven Server](https://github.com/Haven-AI-Companion/haven-server)** running on your local network or a Mesh VPN.

### 2. Sideload Installation
1. Go to the **[Releases](https://github.com/Haven-AI-Companion/haven-android/releases)** page.
2. Download the latest **`app-release.apk`**.
3. Open the downloaded file on your Android device and tap **Install**.
4. *(If Google Play Protect warns you about an "Unknown Developer", tap **More Details** -> **Install Anyway** to whitelist the signature locally.)*

### 3. Server Configuration
1. Open Haven Mobile.
2. Tap the **Settings** gear icon in the top right.
3. Enter your **Haven Server Host** (e.g., `http://192.168.1.100` or a Tailscale IP) and your **Haven Server Port** (default `18799`).
4. Click **Save**. The app will automatically connect and load your companions!

---

## 🏗️ Codebase Structure

```
├── app
│   ├── src
│   │   ├── main
│   │   │   ├── assets
│   │   │   │   └── silero_vad.ort             # Local ONNX VAD Model
│   │   │   ├── java/xyz/ssfdre38/haven
│   │   │   │   ├── data
│   │   │   │   │   ├── database              # Room DB, entities, and DAOs
│   │   │   │   │   ├── network               # HTTP and WebSocket connection utilities
│   │   │   │   │   └── parser                # Tavern V1/V2 card parser
│   │   │   │   ├── theme                     # Compose theme (Material 3 colors & typography)
│   │   │   │   └── ui
│   │   │   │       ├── chat                  # Text chat threads and inputs
│   │   │   │       ├── diary                 # Companion auto-diaries
│   │   │   │       ├── settings              # Rebranded Server & SD configurations
│   │   │   │       └── voice                 # Low-latency Silero VAD Voice call interface
│   │   │   └── res                           # Resources (layouts, values, adaptive launcher icons)
```

---

## 🛠️ Build from Source

To build and compile the APK yourself:

1. Clone this repository:
   ```bash
   git clone https://github.com/Haven-AI-Companion/haven-android
   cd haven-android
   ```
2. Open the project in **Android Studio**.
3. Build the project using the Gradle wrapper:
   *   **Debug APK**: `.\gradlew.bat assembleDebug`
   *   **Release APK (Signed)**: `.\gradlew.bat assembleRelease`
4. The generated release APK will be located at:
   *   `app/build/outputs/apk/release/app-release.apk`

---

## 🛡️ License

This project is licensed under the MIT License - see the LICENSE file for details.
