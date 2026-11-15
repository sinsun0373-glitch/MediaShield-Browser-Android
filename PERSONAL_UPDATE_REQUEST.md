# MediaShield v10.7 personal signed update

- Introduce per-site automatic navigation lock for video pages, blocking gesture-free automatic top-level navigation after page settles, including same-host path changes.
- Enable the lock by default for the user's reported host njavtv.com, with a reversible menu toggle.
- Keep the earlier video compatibility setting separate; this navigation protection is not disabled by video compatibility.
- Diagnostics include lock state and both cross-site / same-host automatic navigation block counters.
- Preserve TLS checks and existing personal APK signing key.
- This cannot guarantee resolution of ERR_CONNECTION_RESET(-6) when caused by the destination server or network. YouTube in-stream ads are not completely blocked.

Use previously encrypted key from run 43712038023, no new signing identity. APK is not Play Protect certified.
