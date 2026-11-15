# MediaShield Browser v10.6 personal update request

Changes:
- Fix the actual menu mapping bug: item 0 "영상 연결 진단" previously invoked video compatibility, item 1 vice versa.
- Add a prominent "진단 정보 복사" action directly to the connection failure dialog.
- Preserve Brave fallback and retry buttons.
- Capture safe navigation trace (hostname and first path segment only) to compare /ko/ to /dm60/ transitions.
- Capture gesture-free automatic main-frame navigation requests for diagnostics without exposing full URLs/query tokens.
- Existing ERR_CONNECTION_RESET(-6) and YouTube in-stream ad blocking remain unresolved pending phone-side diagnostic evidence.

Rebuild using the existing encrypted signing backup and signing identity from bootstrap run 43712038023.
Not certified by Play Protect. Do not create a fresh signing key.
