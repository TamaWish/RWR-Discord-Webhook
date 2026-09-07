# Release notes

Highlights for people who install or update this plugin. Full technical history is in [CHANGELOG.md](CHANGELOG.md).

## Version 1.0.0 — 2026-09-01

**Headline:** Resource world reset warnings and outcomes now post to Discord as reliable webhook embeds.

First public release of RWR-Discord Webhook. After you set a Discord incoming webhook URL, the server posts rich embeds when ResourceWorldResetter warns that a reset is coming and when a reset succeeds, fails, is cancelled, or is interrupted. Delivery is asynchronous, retries through Discord rate limits, and resumes pending messages after a restart.

### Highlights

- Discord embeds for configured reset warnings (world, time remaining, scheduled reset time) and for successful, failed, cancelled, and interrupted resets (operation details and failure information when relevant).
- A configuration confirmation message after startup or reload that reports RWR API availability, add-on version, and server software.
- Optional allow-listed role and user mentions; when mentions are off, locale and event text cannot ping anyone.
- Per-category event toggles and embed colors in `config.yml`, plus a locale system (`locale`, `locales/<code>.yml`) with English fallback.
- Admin commands `/rwrdiscord reload` and `/rwrdiscord status` (`rwrdiscord.admin`, default op), with aliases `rwr-discord`, `rwr-discord-webhook`, and `rwrwh`.
- Works on Spigot, CraftBukkit, Paper, Purpur, and Folia; HTTP delivery stays off the server thread.
- Loads without RWR / `RwrApi`, but does not forward reset events until the API is available.
- Every embed description includes links to the Privacy Policy and Terms of Service.
- Uses **bStats** for anonymous server usage statistics under [plugin ID 33788](https://bstats.org/plugin/bukkit/RWR-Discord-Webhook/33788). Opt out in `plugins/bStats/config.yml` (`enabled: false`); that file is controlled by bStats, not this plugin's `config.yml`.

### Improvements

- Durable bounded queue with expiration so notifications survive restarts and Discord downtime, with exact `Retry-After` handling for rate limits and redacted webhook secrets in logs and the queue file.

### Upgrade notes

Nothing to migrate — this is the first release. Install ResourceWorldResetter 5.1+, drop `RWR-Discord-Webhook-1.0.0.jar` into `plugins/`, set `webhook.url`, then run `/rwrdiscord reload`.

Requires Java 21+, a 1.21 Spigot/CraftBukkit/Paper/Purpur/Folia server, and ResourceWorldResetter 5.1+ for reset notifications.

### Links

- [Changelog entry](CHANGELOG.md#100---2026-09-01)
- [README](README.md)
- [GitHub Release](https://github.com/TamaWish/RWR-Discord-Webhook/releases/tag/v1.0.0)
- [Download](https://github.com/TamaWish/RWR-Discord-Webhook/releases/download/v1.0.0/RWR-Discord-Webhook-1.0.0.jar)
