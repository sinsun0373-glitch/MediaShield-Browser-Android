# MediaShield Browser v11.0 personal signing request

Replace the ineffective WebView-only workaround with a transparent engine-choice workflow:
- Default YouTube/youtu.be/njavtv.com to the actual installed Brave browser via explicit ACTION_VIEW.
- Permit alternate Brave Custom Tab inside app-like chrome, or existing WebView (known limitations) from the new menu item.
- Route address bar and navigation to the selected engine.
- Add explicit OS DNS/HTTPS connection probes for njavtv.com and www.youtube.com from the video diagnostics screen.
- Keep source behavior conservative: no TLS certificate bypass, no broad DNS proxy, no DRM workarounds.
- Android build, pure-Java tests, security policy, APK signing check passed in run 43734159552.
- YouTube ad blocking remains performed by Brave itself, **not** by MediaShield WebView.
- Access and playback results on the user's Galaxy S23 require testing. Do not claim the WebView connection reset itself is fixed.

Re-use original encrypted personal signing key from run 43712038023. Do not create new signing identity. Not Play Protect certified.
