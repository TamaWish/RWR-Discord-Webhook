# Changelog

Public **v1** history for RWR-Discord Webhook. All 1.x.x versions stay in this file. When v2 begins, start `CHANGELOG_v2.md`.

## 1.0.0 - 2026-09-03

- Added Discord webhook embeds for every configured RWR reset warning and for successful, failed, cancelled, and interrupted resets.
- Added a webhook configuration confirmation reporting RWR API availability, add-on version, and server software.
- Added warning details for world identity, remaining time, and scheduled reset time without an operation ID.
- Added terminal details for operation ID, phase, safety classification, failure type, and message.
- Added asynchronous delivery with a bounded, expiring, persistent queue that resumes after restart.
- Added exact Discord rate-limit handling, bounded ordinary retries, secret redaction, and disabled webhook mentions.
- Added transactional reload, bounded shutdown, degraded operation when RWR is unavailable, and `/rwrdiscord status` delivery diagnostics.
- Added Privacy Policy and Terms of Service links to every webhook embed description (hard-coded; not configurable in `config.yml`).
- Published as **RWR-Discord Webhook** (`RWR-Discord-Webhook` plugin id) so a future Discord bot add-on can use a distinct identity.
- Declared native Folia support; delivery remains off-thread without Bukkit scheduler calls.

Release remains pending final smoke testing with ResourceWorldResetter 5.1.0.
