# Security & Privacy Policy

Minima is engineered with a privacy-by-design, offline-first philosophy.

---

## Threat Model & Privacy Boundary

Minima handles sensitive device usage data, including:
- Installed application package metadata (`QUERY_ALL_PACKAGES`).
- Foreground application usage statistics (`PACKAGE_USAGE_STATS`).
- Launch frequencies and intentional delay attempts.

To ensure total user privacy:
1. **No Network Access**: Minima deliberately omits `android.permission.INTERNET` from its `AndroidManifest.xml`. The application has zero capability to transmit telemetry, analytics, or usage statistics over the network.
2. **Local Encrypted Storage**: All data is persisted in a local Room SQLite database and DataStore preferences stored in the private application sandbox (`/data/data/com.minima/`).
3. **Restricted Exported Components**: Only the main launcher activity (`MainActivity`) is exported, strictly secured to act as a system home replacement (`CATEGORY_HOME`, `CATEGORY_DEFAULT`).

---

## Reporting a Vulnerability

If you discover an unexpected security flaw, component leakage, or privilege escalation:

1. Please do not open a public GitHub issue.
2. Send an email with reproduction details to the maintainer: `fayaspulivetty@gmail.com`.
3. Provide a clear description, affected Android API level, and steps to reproduce.
4. Vulnerabilities will be addressed and patched in the `main` branch promptly.
