# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2026-09-13

### Added

- Added bundled `zh_CN`, `ja_JP`, and `ko_KR` locale files with the existing English fallback behavior.

### Changed

- Verified the existing RWR API 5.1.2 event integration against ResourceWorldResetter 5.2.0.

## [1.0.0] - 2026-09-01

### Added
- Discord webhook embeds for configured RWR reset warnings and for successful, failed, cancelled, and interrupted resets
- Startup and reload configuration confirmation reporting RWR API availability, add-on version, and server software
- Warning embeds with world identity, remaining time, and scheduled reset time (no operation ID)
- Terminal embeds with operation ID, phase, safety classification, failure type, and message detail
- Asynchronous delivery with a bounded, expiring, persistent queue that resumes after restart
- Exact Discord rate-limit (`Retry-After`) handling, bounded ordinary retries, and webhook secret redaction from logs and the durable queue
- Optional allow-listed Discord role and user mentions (`mentions.*`); locale and event text cannot ping when mentions are disabled
- Per-category event toggles and embed colors in `config.yml`
- Locale system (`locale`, `locales/<code>.yml`) with English fallback
- `/rwrdiscord reload` and `/rwrdiscord status` (permission `rwrdiscord.admin`, default op), with aliases `rwr-discord`, `rwr-discord-webhook`, and `rwrwh`
- Hard-coded Privacy Policy and Terms of Service links on every webhook embed description
- Native Folia support alongside Spigot, CraftBukkit, Paper, and Purpur; HTTP delivery stays off the server thread
- Degraded operation when ResourceWorldResetter / `RwrApi` is unavailable (plugin loads without forwarding reset events)
- Anonymous bStats metrics for RWR-Discord-Webhook ([plugin ID 33788](https://bstats.org/plugin/bukkit/RWR-Discord-Webhook/33788); opt out via `plugins/bStats/config.yml`)

[Unreleased]: https://github.com/TamaWish/RWR-Discord-Webhook/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/TamaWish/RWR-Discord-Webhook/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/TamaWish/RWR-Discord-Webhook/releases/tag/v1.0.0
