# MediaShield v11.4 personal signed update

- Keep application ID and the existing encrypted personal signing key from run 43712038023.
- Eight-item primary menu; labeled bottom actions and accessible button descriptions.
- Handle system bars, display cutout and keyboard insets for Android 15+.
- Keep download address input mounted while queue rows update; preserve its value across rotation.
- Korean queue states, empty states, internal retry from connection errors.
- Await DOM detection before presenting downloadable videos; exclude unsupported DASH.
- Reset diagnostic errors per navigation; report effective third-party cookies and request blocking.
- Probe only the current site, reject duplicate probes, suppress results after Activity destruction.
- Report navigation hosts without paths; native VideoView no longer receives cookies or private referer headers that could follow redirects.
- Keep all automatic browsing internal. No TLS bypass or replacement signing keys.
- Run existing JVM core/security checks, APK signature verification and Android 35 emulator UX smoke checks.
- ERR_CONNECTION_RESET, YouTube in-stream ads and Galaxy S23 playback remain device-validation items.
