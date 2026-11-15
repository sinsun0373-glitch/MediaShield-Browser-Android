# MediaShield Browser – security review / release gate

**Status: RELEASE CANDIDATE, NOT CERTIFIED FOR END-USER INSTALLATION.**

## Confirmed by source inspection / automated checks
- Android manifest requests INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, and POST_NOTIFICATIONS; no install-packages, accessibility, SMS, or broad file-management permission is declared.
- Network security: cleartext HTTP and mixed content are disabled; downloads accept HTTPS addresses.
- SSL certificate errors are cancelled instead of bypassed.
- Download initiation requires explicit user consent.
- Download queue no longer persists web authentication cookies, and clears cookies in existing queue records.
- Download service and HLS inspector refuse automatic HTTP redirects. This may reduce CDN compatibility.
- Android WebView provides page rendering; this app is not Brave/Chromium and is not equivalent to Brave's security model.
- GitHub Actions runs the existing Java tests, checks security policies, and compiles an Android debug build.

## Unverified / required before public distribution
1. Independent review of all network, JavaScript, HLS, file-storage, and background service paths; static checks do not prove absence of malicious behavior.
2. Reproducible APK source-to-binary review, signing key protection, dependency and SBOM review.
3. Test on Galaxy S23 with Android's Play Protect enabled; verify no harmful-app classification.
4. If still blocked, submit the APK and evidence through Google's Play Protect warning appeal process (do not bypass).
5. Full functional smoke tests: navigation, video playback, ad-block exceptions, download pause/resume and media export.
6. Production signing with a securely controlled long-term release key. CI's debug key is NOT a production signing key.

## Release policy
Do NOT publish this build as a Play-Protect-approved or security-certified application until the items above are completed. 
Do NOT instruct users to select "Ignore and install" when Play Protect warns.
No VPN, DRM circumvention, hidden permission elevation, or Play Protect bypass is part of the intended product.

## References
Google Play Protect developer guidance: https://developers.google.com/android/play-protect/warning-dev-guidance
