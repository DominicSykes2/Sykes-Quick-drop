# Drop Link (C) Dominic Sykes

**Drop Link** is a local-network file transfer app for Windows PC and Android. It supports automatic discovery and a manual IP fallback, with transfers sent directly between devices without a cloud service.

## Permanent License
(C) Dominic Sykes. All rights reserved. Permanent personal and commercial license granted.

## Automated Builds (GitHub Actions)
Pushing to `main` automatically compiles and attaches two downloadable artifacts under the **Actions** tab:
1. `Drop-Link-Windows-EXE` (`Drop-Link-PC.exe`)
2. `Drop-Link-Android-APK` (`app-debug.apk`)

## Android-to-PC connection
If Android auto-discovery times out, copy the `Host IP` shown in the PC app into the Android app's `PC IP address` field and tap **Connect to PC by IP**. The app checks that the PC is reachable before enabling file transfers. Keep both apps open on the same Wi-Fi network and allow Drop Link through Windows Firewall on the Private network.
