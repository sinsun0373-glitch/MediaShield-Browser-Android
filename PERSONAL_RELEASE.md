# MediaShield personal APK signing

This workflow prepares a personally signed APK. **It does not certify security or prevent Google Play Protect warnings.** Never disable Play Protect to install an unverified build.

## One-time private key generation (on your own computer)

On a computer with Java installed, run:

```bash
keytool -genkeypair -v -keystore mediashield-personal.jks -storetype PKCS12 -alias mediashield-personal -keyalg RSA -keysize 3072 -validity 10000
```

Choose a strong password and keep the .jks file and password offline in a safe location. Losing either prevents signed upgrades.

## GitHub Actions secrets

GitHub > repository > Settings > Secrets and variables > Actions > New repository secret

- `MEDIASHIELD_KEYSTORE_BASE64`: Base64 contents of the local JKS/PKCS12 file, as **one line**. On PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("mediashield-personal.jks"))`. On Linux/macOS: `base64 < mediashield-personal.jks | tr -d '\\n'`.
- `MEDIASHIELD_KEY_ALIAS`: `mediashield-personal`
- `MEDIASHIELD_STORE_PASSWORD`: keystore password
- `MEDIASHIELD_KEY_PASSWORD`: private key password (usually same as store password for PKCS12)

**Never paste credentials in this chat or commit signing files/passwords to GitHub.** The workflow validates required secrets before attempting to build and discards its temporary keystore after signing.

## Build

Actions > **Build personally signed APK** > Run workflow. On success download `MediaShield-Personal-Release-APK` from Artifacts.

Important: earlier v8/v9 debug APKs are signed with different ephemeral keys. This release APK will not upgrade them in place. Preserve any needed exported files, uninstall the old debug app, then install only after Play Protect status and security checks are satisfactory.

## Status

This build is for private evaluation, not security-certified. Independent source/security review, Play Protect review, and real-device tests remain required.
