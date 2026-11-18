# MediaShield v11.5 internal compatibility engine

User reports: WebView ERR_CONNECTION_RESET, Android root HEAD SocketException, but Brave works on the same device/network. Root cause is not confirmed.

- Add an explicit in-app GeckoView 160.0.20261116130455 compatibility Activity; no external browser handoff.
- Error screen and main menu provide an internal-engine action. HTTPS only; normal certificate validation; popup/external scheme guards.
- Separate Gecko cookies and default tracking protection. Existing WebView filters and download detection do not apply to Gecko; explain this before opening.
- Gecko engine is browsing/playback-only in this iteration. Keep existing WebView downloads unchanged.
- Add combined WebView + DNS + HEAD/GET diagnostics with classified errors; do not log private URL paths/cookies.
- Compile with SDK 37.2, AGP 9.4.0, Gradle 9.6.0 to match current Gecko dependencies; retain targetSdk 35 and minSdk 30.
- Release arm64-v8a for the user's Galaxy S23; debug x86_64 for emulator tests.
- Test navigation policy, existing downloader core, APK signatures, Android emulator UI including HTTPS loading in Gecko.
- Preserve the existing signing key backup from run 43712038023.
- Not Play Protect approved. Actual problematic-site playback remains unverified until tested on the user's device.

Final validation: preserve error-page failure state and reopen the Gecko session after a content-process crash. Rebuild both signed release and emulator-tested debug from the final source.
