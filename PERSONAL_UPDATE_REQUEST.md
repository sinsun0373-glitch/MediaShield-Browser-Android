# MediaShield v11.2 personal signed APK update

Requested fixes after v11.1 user testing:
- User reports APK transfer interrupted mid-download; remove AndroidX browser dependency, reduce APK size significantly.
- Never automatically transfer YouTube or video-site URLs to YouTube, Chrome, Brave, or another browser.
- Keep the browser internal to MediaShield WebView, with manual Brave action explicitly available only in menus.
- Add browser HTML5 media play, pause, stop controls (best effort; cross-origin iframes can't be controlled by page JS).
- Add fullscreen video overlay with play, pause, stop, close actions.
- Add native in-app Android VideoView player for detected direct HTTPS video URLs, with standard seek bar, playback controls, and an exit button.
- The native player does not bypass DRM or guarantee support for HLS/DASH; ERR_CONNECTION_RESET and YouTube in-stream ad skipping remain unsolved.
- Debug APK build, pure-Java tests, security checks and archive/signature checks passed for code commit 8e3947022ec3d69877db329147ce8e76db7fc841 in run 43736853162.
- Use the established personal signing identity from bootstrap run 43712038023; never generate a new key.
- Not Google Play Protect certified.
