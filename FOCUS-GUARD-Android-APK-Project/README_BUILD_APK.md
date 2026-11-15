# FOCUS GUARD — Native Android APK Build Guide

## Option 1: Build APK in the Cloud via GitHub Actions (No PC Software Required)
1. Unzip this file and upload all contents (including `.github/workflows/build-apk.yml` and `android/`) to a GitHub repository.
2. Open the **Actions** tab on GitHub and run **Build FOCUS GUARD Android APK**.
3. Download `FOCUS-GUARD-debug-apk` from Artifacts and install `app-debug.apk` on your Android phone.

## Option 2: Build APK Locally in Android Studio
1. Unzip this file.
2. Open Android Studio -> **Open** -> select the `android/` folder.
3. Wait for Gradle Sync, then click **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
