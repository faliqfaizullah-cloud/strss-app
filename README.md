# Strss — digital well-being (Android)

Glassmorphism + frosted-glass UI inspired by your video. Kotlin + Jetpack Compose.

## Features
- Home: glass "pebble" tiles, Basic / Premium toggle, Daily Stress Index dial, Calm Score
- Screen Time: today's total, hourly chart, most-used apps (real icons)
- App Timers: daily limits per app, nudge notification + haptics when the limit is reached
- Bed Schedule: bedtime, wake time, wind-down reminder, sleep goal
- Stress: index from screen time, use during bed hours, exceeded timers
- Breathe: guided 4-4-6 session with haptic pulses
- Insights: 7-day screen-time chart
- Focus: flip-to-focus Pomodoro timer (dark OLED screen), streak, trophies, total flip time with D/W/M/Y tabs
- 2x2 home-screen widget, 28dp rounded corners, glass look
- App icon: tap the wave logo (top left of Home) to switch between the default and the shoe-box icon
- Haptic feedback on every tap, toggle, timer change and breathing phase

## Build the APK (easiest: GitHub Actions)
1. Create a GitHub repo and push this folder (from Termux: `git init && git add . && git commit -m init && git remote add origin <url> && git push -u origin main`).
2. Open the repo's Actions tab, run "Build Strss APK", download artifact **Strss-apk** (Strss.apk).
   Pushing a tag like `v1.0` also attaches Strss.apk to a GitHub Release.

## Build locally
Android Studio: open this folder and press Run. Or with Gradle 8.9 + JDK 17 + Android SDK 34: `gradle :app:assembleDebug`.
The Urbanist font is downloaded automatically on first build (or put any .ttf at app/src/main/res/font/urbanist.ttf).

## First launch
Allow notifications, then tap "Open settings" on the home screen and grant **Usage Access** to Strss.
