# QuickShare Lite — Milestone Zero

A production-oriented Android Kotlin/Jetpack Compose foundation for a simple nearby file-sharing app.

## Implemented in this package

- Kotlin + Jetpack Compose + Material 3 foundation
- System-ready Light/Dark theme structure
- Short splash, profile setup, home, side menu, settings, history, and Remove Ads screens
- Local profile storage with Preferences DataStore
- Room transfer history storage
- Real Android multi-document picker
- Real Nearby Connections discovery using `P2P_POINT_TO_POINT`
- Real Nearby connection request/accept/reject callbacks
- Authentication token confirmation flow
- Real manifest handshake over Nearby BYTES payloads
- Real Nearby FILE payload for a one-file Milestone Zero transfer
- Disk staging for selected content URIs; no whole-file ByteArray/base64 transfer
- Real transfer bytes/percentage/speed/ETA sourced from Nearby callbacks
- Receiver-side size + SHA-256 integrity validation, `.partial` finalization, and duplicate filename handling
- Foreground-service declaration for later long-running transfer execution
- AdMob test banner integration, hidden when the stored ad-free entitlement is true
- Play Billing 9.1.0 integration scaffold with a `remove_ads` one-time product ID

## Milestone Zero validation

The project is intentionally scoped to prove:

Android A -> real discovery -> real authenticated connection -> real file selection -> real acceptance -> real FILE payload -> Android B validates and writes the file.

This package is a Milestone Zero foundation, not a claim that the full V1 definition of done has already been physically validated. Multi-file queueing, background service orchestration, QR session transport, richer history details, and production purchase verification are subsequent phases.

## Important release setup

1. Open in a current Android Studio that supports AGP 9.x and Android API 37; the wrapper is pinned to Gradle 9.5.0 for AGP 9.3.2 compatibility.
2. Create a Play Console one-time product named `remove_ads` before enabling production purchases.
3. Replace the sample AdMob app ID/banner ID with the app's production IDs before release.
4. Add production privacy-policy URLs and store listing metadata.
5. Test discovery/transfer on two physical Android devices; an emulator-only result does not satisfy the Milestone Zero acceptance criterion.

## Current official references used

- Nearby Connections Android: https://developers.google.com/nearby/connections/android/get-started
- Nearby advertise/discover: https://developers.google.com/nearby/connections/android/discover-devices
- Nearby connection management/authentication: https://developers.google.com/nearby/connections/android/manage-connections
- Nearby payload exchange: https://developers.google.com/nearby/connections/android/exchange-data
- Android Photo Picker: https://developer.android.com/training/data-storage/shared/photo-picker
- Android Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files
- Foreground services: https://developer.android.com/develop/background-work/services/fgs/declare
- AdMob banners: https://developers.google.com/admob/android/banner
- Play Billing: https://developer.android.com/google/play/billing/integrate
