# Aparajito - Android App

An Android WebView wrapper for **[aparajito.netlify.app](https://aparajito.netlify.app/)** — your cinema web app, packaged as a native Android app.

## Features
- 🌐 Full-screen WebView loading `https://aparajito.netlify.app/`
- ⬅️ Hardware back button support (navigates back in web history)
- 🔄 Pull-to-refresh
- ⏳ Top progress bar while loading
- 🎬 Cinema dark theme (dark status bar + nav bar)
- 📱 Works on Android 5.0+ (API 21+)

## Project Structure

```
android-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/aparajito/cinema/
│   │   │   └── MainActivity.java        ← WebView logic
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml ← UI layout
│   │   │   └── values/
│   │   │       ├── colors.xml           ← Cinema color palette
│   │   │       ├── strings.xml
│   │   │       └── themes.xml           ← Dark theme
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── .github/workflows/build.yml          ← GitHub Actions CI
├── build.gradle
├── settings.gradle
└── gradlew / gradlew.bat
```

## 🚀 Build on GitHub (Recommended — no Android SDK needed)

1. Push this folder to a **new GitHub repository**
2. Go to **Actions** tab in your GitHub repo
3. The workflow runs automatically → builds a debug APK
4. Download the APK from **Actions → your workflow run → Artifacts**
5. Install on your Android device!

> ⚠️ You need to **enable "Install from unknown sources"** on your device to install the debug APK.

## 🏠 Build Locally (requires Android Studio)

1. Open Android Studio
2. **File → Open** → select this `android-app` folder
3. Wait for Gradle sync to complete
4. Click **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. APK is at: `app/build/outputs/apk/debug/app-debug.apk`

## ⚠️ Important: gradle-wrapper.jar

The `gradle/wrapper/gradle-wrapper.jar` binary file is required but not included in source control.

**When you open this project in Android Studio**, it will automatically download this file.

**For GitHub Actions**, the `setup-java` action handles this automatically.

If building locally without Android Studio, run:
```bash
gradle wrapper --gradle-version=8.4
```
(requires Gradle installed globally)
