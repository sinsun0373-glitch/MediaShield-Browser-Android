# MediaShield v11.3 personal signed update

User reports that the DNS/HTTPS direct test was not visible in the v11.2 diagnostic window.
Verified actual MainActivity.java: the test handler runNetworkProbe was only connected to a neutral AlertDialog button, not a direct top-level menu option.

Changes in v11.3:
- Add direct top-level menu item "DNS·HTTPS 직접 검사" immediately after "영상 연결 진단"; verifies switch indices 0–15 are unique and aligned with 16 menu options.
- Also expose DNS/HTTPS test in the main connection-error dialog as its neutral button, replacing redundant retry (refresh remains available in browser).
- Shorten the diagnostic dialog neutral button label to "네트워크 검사" to reduce narrow-screen button crowding.
- Bump report and network probe labels to v11.3.
- Preserve internal WebView navigation, native player functionality and personal signing identity.
- GitHub CI compile, JVM tests, security checks, APK inspection and debug signing checks passed in run 43738479942.
- Does not solve the upstream ERR_CONNECTION_RESET or YouTube in-stream ads. Android UI on user's device still requires testing.
- Existing personal encrypted signing key from 43712038023; do not generate a new identity. Not Google Play Protect certified.
