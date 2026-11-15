# MediaShield Browser v10.5 personal signed update

Requested changes:
- Address-bar download button now opens an actionable video selection dialog, rather than a queue-only screen.
- Video first selection filters out images and allows HLS quality choice or direct video URL downloads when supported.
- Prevent unsolicited automatic cross-origin top-level navigations after site load when popup protection is enabled.
- Preserve HTTPS-only download restrictions, existing private signing identity, and user-configurable per-site video compatibility.
- In-stream YouTube advertisements and ERR_CONNECTION_RESET(-6) are NOT confirmed fixed; device testing required.

Trigger signed update via the existing encrypted personal signing-key backup from workflow run 43712038023. Never regenerate a signing identity. Not Play Protect certified.
