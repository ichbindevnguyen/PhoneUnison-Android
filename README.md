# PhoneUnison Android

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)

**PhoneUnison Android** is the mobile companion app for connecting your Android phone to your Windows PC. Seamlessly sync notifications, messages, calls, and files between devices.

## ✨ Features

- 🔔 **Notifications Sync** - Mirror phone notifications to PC
- 💬 **SMS/MMS** - Send and receive text messages from your computer
- 📞 **Phone Calls** - Get call notifications and control calls from PC
- 📋 **Clipboard Sync** - Copy on phone, paste on PC
- 📁 **File Transfer** - Send files to your connected PC
- 🔒 **Secure Connection** - AES-256-GCM encrypted communication

## 📱 Requirements

- Android 7.0+ (API 24+)
- WiFi connection (same network as PC)

## 🚀 Getting Started

### Install from APK

1. Download the latest APK from [Releases](https://github.com/ichbindevnguyen/PhoneUnison-Android/releases)
2. Enable "Install from unknown sources" in Settings
3. Install the APK

### Build from Source

```bash
git clone https://github.com/ichbindevnguyen/PhoneUnison-Android.git
cd PhoneUnison-Android
./gradlew assembleDebug
```

## 🔗 Pairing with PC

1. Install [PhoneUnison Windows](https://github.com/ichbindevnguyen/PhoneUnison-Win) on your PC
2. Open both apps on the same WiFi network
3. Tap "Connect to PC" on Android
4. Either:
   - Scan the QR code shown on PC, or
   - Enter the IP address and 6-digit code manually
5. Done! Your devices are now connected.

## 📁 Project Structure

```
PhoneUnison-Android/
├── app/
│   └── src/main/
│       ├── java/com/phoneunison/mobile/
│       │   ├── MainActivity.kt
│       │   ├── services/
│       │   │   ├── ConnectionService.kt
│       │   │   ├── NotificationListenerService.kt
│       │   │   └── ...
│       │   ├── network/
│       │   │   └── UDPDiscovery.kt
│       │   └── ui/
│       │       └── ...
│       └── res/
├── build.gradle
└── settings.gradle
```

## 🎨 Themes

PhoneUnison supports multiple themes:
- System Default
- Light (KDE Breeze Light)
- Dark (KDE Breeze Dark)
- Catppuccin

## 🛡️ Permissions

| Permission | Purpose |
|------------|---------|
| Internet | Network communication |
| Notification Access | Sync notifications to PC |
| SMS | Read and send messages |
| Phone | Call notifications |
| Camera | QR code scanning |
| Bluetooth | Backup connection method |

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**iBDN** - [GitHub Profile](https://github.com/ichbindevnguyen)

## 🔗 Related

- [PhoneUnison Windows](https://github.com/ichbindevnguyen/PhoneUnison-Win) - Windows desktop app

---

Made with ❤️ by iBDN
