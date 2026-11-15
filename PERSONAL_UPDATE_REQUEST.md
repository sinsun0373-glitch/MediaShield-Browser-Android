# MediaShield v11.1 personal signed update request

Fix a user-observed bug in v11.0: YouTube opened the YouTube app and njavtv.com opened Chrome instead of remaining tied to Brave.

Changes:
- Remove package-unpinned ACTION_VIEW / Intent.createChooser fallback entirely.
- Detect Brave stable, beta, or nightly with Android 11+ explicit package visibility.
- Prefer Brave Custom Tabs for the two problematic domains to keep an app-like return flow.
- Bind both Custom Tab and explicit Brave app choices to the found Brave package, never hand off silently to Chrome or YouTube.
- If Brave is unavailable or a pinned launch fails, show a choice to use the existing MediaShield WebView or cancel, without launching a different app.
- A voluntary WebView fallback persists to prevent reopening the same unavailable Brave workflow.
- Continue exposing the engine choice and network diagnostic tools.
- v11.1 debug APK build, pure-Java core tests, security checks and APK signature verification passed in GitHub run 43735316233 (build step tests; personal signed build still required).
- No claim that WebView's ERR_CONNECTION_RESET or YouTube ad blocking are fixed.

Use existing encrypted personal signing key from bootstrap run 43712038023. Do not create new signing identity. Not Google Play Protect certified.
