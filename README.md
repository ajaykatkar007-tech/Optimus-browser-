# Optimus Browser

Optimus Browser is an Android-first privacy-focused browser prototype built with Kotlin and Android WebView.

## Current scope
- URL and web search bar
- Back, reload, and clear browsing data controls
- HTTPS preference for direct domain navigation
- Third-party cookies disabled
- Basic starter tracker-domain blocking
- Android Safe Browsing support
- GitHub Actions debug APK build workflow

## Important limitations
This is an early prototype, not a complete or independently security-audited privacy browser. The starter tracker list is intentionally small and WebView request interception cannot block every tracking technique. Do not use this build as a claim of complete anonymity or comprehensive tracking protection.

## Build
Open this repository in Android Studio (JDK 17, Android SDK 35) and sync Gradle. GitHub Actions is configured to run `gradle assembleDebug` and upload the debug APK when a workflow run succeeds.

Package: `com.optimusprivacy.browser`
Minimum Android: 8.0 (API 26)
Target SDK: 35
