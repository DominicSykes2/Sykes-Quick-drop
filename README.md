# SykesLink © 2026 Dominic Sykes

**SykesLink** is a high-speed, local-network peer-to-peer file and document transfer suite built for Windows PC and Android devices. It features zero cloud dependency, automatic UDP device discovery, and direct local sockets.

## Permanent License
© 2026 Dominic Sykes. All rights reserved. Permanent personal and commercial license granted.

## Automated Builds (GitHub Actions)
Pushing to `main` automatically compiles and attaches two downloadable artifacts under the **Actions** tab:
1. `SykesLink-Windows-EXE` (`SykesLink-PC.exe`)
2. `SykesLink-Android-APK` (`app-debug.apk`)

## Android-to-PC connection
If Android auto-discovery times out, copy the `Host IP` shown in the PC app into the Android app's `PC IP address` field and tap **Connect to PC by IP**. The app checks that the PC is reachable before enabling file transfers. Keep both apps open on the same Wi-Fi network and allow SykesLink through Windows Firewall on the Private network.
