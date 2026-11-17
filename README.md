# AirAssistant 🎧

<p align="center">
  <img src="app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" alt="AirAssistant Logo" width="96" height="96" />
</p>

<p align="center">
  <strong>AirPods on Android, supercharged with AI.</strong><br>
  Double-tap or stem-squeeze your AirPods to summon <strong>Google Assistant</strong> or <strong>Google Gemini</strong> on Android — even while Spotify or YouTube Music is actively playing.
</p>

<p align="center">
  <a href="https://github.com/Ahmerkhan12/Air-Assistant/releases/latest"><img src="https://img.shields.io/badge/Release-v1.0.0-blue.svg?style=flat-square" alt="Version"></a>
  <img src="https://img.shields.io/badge/Platform-Android%2010%2B%20(API%2029--35)-brightgreen.svg?style=flat-square" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin%20%2F%20Compose-purple.svg?style=flat-square" alt="Language">
  <img src="https://img.shields.io/badge/UI-Material%203-orange.svg?style=flat-square" alt="Material 3">
  <img src="https://img.shields.io/badge/License-MIT-lightgrey.svg?style=flat-square" alt="License">
</p>

---

## 🚀 Overview

Apple AirPods offer exceptional sound and gesture hardware, but on Android, they are treated as generic Bluetooth headphones. The built-in gesture triggers (double-tap on AirPods 1/2 or force sensor stem squeezes on AirPods Pro) cannot summon Siri on Android.

**AirAssistant** fixes this missing bridge:
- Intercepts Bluetooth media button events sent by your earbuds.
- Seamlessly triggers **Google Gemini** or the Android **Voice Assist overlay**.
- Incorporates a lightweight **Accessibility Service** that ensures gesture interception works even when music streaming apps (Spotify, Apple Music, YouTube Music) have active audio focus.

---

## ✨ Features

- ⚡ **Instant Voice Assistant Summoning**: Map double-tap or stem-squeeze gestures to launch Google Gemini (`com.google.android.apps.bard`) or standard Google Assistant voice overlay.
- 🎵 **Music-Proof Interception**: Media apps usually hijack Bluetooth controls. AirAssistant's background `AirAccessibilityService` catches double-taps without interrupting music playback.
- 🔋 **Zero Battery Drain Idle**: Operates passively using Android's system event bus and Bluetooth callbacks. No CPU-heavy polling loops.
- 📱 **Bluetooth Device Dashboard**: Live device status, connection monitor, and signal detection.
- 🔒 **100% On-Device & Private**: No cloud accounts, zero analytics, zero trackers. Runs strictly locally on your phone.
- 🎨 **Modern Material 3 Interface**: Built 100% in Jetpack Compose with smooth animations, dark mode aesthetics, and haptic feedback.

---

## 🎧 Supported Devices

| Device | Supported Gestures |
| :--- | :--- |
| **AirPods (1st, 2nd, 3rd Gen)** | Double-tap on earbud |
| **AirPods Pro (1st & 2nd Gen)** | Force-sensor stem squeeze / press |
| **AirPods Max** | Digital Crown & button triggers |
| **Samsung Galaxy Buds, Sony, Bose** | Any Bluetooth headset sending media button clicks |

---

## 📥 Download & Installation

Because AirAssistant is distributed independently (no Play Store account required), you can download the APK directly:

1. Download **`AirAssistant-v1.0.0.apk`** from [GitHub Releases](https://github.com/Ahmerkhan12/Air-Assistant/releases/latest).
2. Open the downloaded `.apk` file on your Android device.
3. If prompted with *"Install unknown apps"*, tap **Settings** &rarr; toggle **Allow from this source** &rarr; tap **Install**.
4. Launch **AirAssistant** and grant:
   - **Nearby Devices (Bluetooth)**: To detect your connected AirPods.
   - **Accessibility Service**: Required to intercept double-taps while music is playing.
5. Pop in your AirPods and double-tap to summon your AI assistant!

---

## 🌐 Website & Download Portal

A dedicated, mobile-friendly landing and download website is included in the [`website/`](./website) folder.

Features of the web portal:
- Interactive phone mockup with Gemini waveform animation.
- Dynamic QR code generation for one-scan downloading on Android.
- Sideloading walkthrough and FAQ accordion.
- Ready to host for free via **GitHub Pages** or **Netlify Drop**.

---

## 🛠️ Architecture & Tech Stack

- **UI Framework**: Jetpack Compose + Material 3 (Material You)
- **Programming Language**: Kotlin (100%)
- **Target SDK**: Android 15 (API 35), Min SDK: Android 10 (API 29)
- **Asynchronous Flow**: Kotlin Coroutines & `StateFlow`
- **Key Services**:
  - `AirAssistantService`: Foreground service monitoring Bluetooth connections and audio routing.
  - `AirAccessibilityService`: Accessibility service for music-proof media key event interception.
  - `AssistantLauncher`: Intelligent fallback launcher targeting Google Gemini or system voice assist.
  - `AirBluetoothManager`: Monitors bonded device states and active A2DP / Headset profiles.

---

## 🏗️ Building from Source

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17
- Android SDK 35

### Build Commands
```bash
# Clone the repository
git clone https://github.com/Ahmerkhan12/Air-Assistant.git
cd Air-Assistant

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```
The resulting APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

This project is open-source under the [MIT License](LICENSE).

---

## ⚠️ Disclaimer

*AirAssistant is an independent project. "AirPods", "Apple", and "Siri" are registered trademarks of Apple Inc. "Android", "Google Assistant", and "Google Gemini" are trademarks of Google LLC. This app is not affiliated with or endorsed by Apple Inc. or Google LLC.*
