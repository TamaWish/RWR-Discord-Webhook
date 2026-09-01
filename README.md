# RWR-Discord Webhook

Discord webhook add-on for [ResourceWorldResetter](https://github.com/TamaWish/ResourceWorldResetter). A separate Discord bot add-on may ship later; this plugin is webhook-only.

**Artifact:** `io.github.tamawish:rwr-discord-webhook`  
**Package:** `io.github.tamawish.rwr.discord`  
**Repository:** [TamaWish/RWR-Discord-Webhook](https://github.com/TamaWish/RWR-Discord-Webhook)

## Features (v1.0)

- Soft-depends on both RWR runtimes (`ResourceWorldResetter` and `ResourceWorldResetter-Paper-Folia`)
- Uses only the public [RWR-API](https://github.com/TamaWish/RWR-API) service and reset events
- Disables delivery cleanly when `RwrApi` is unavailable
- Webhook URL and all Discord settings live exclusively in this plugin’s data folder
- Webhook secrets are redacted from logs
- Posts a configuration confirmation with RWR API, add-on, and server status after startup or reload
- Own locale system (`locale: en_US`, `locales/<code>.yml`, English fallback)
- Sends Discord embeds for:
  - every configured reset **warning** (`ResourceWorldResetWarningEvent`)
  - **successful** resets
  - **failures**
  - **cancellations** (`EVENT_CANCELLED`)
  - **interrupted** operations
- Does **not** send intermediate reset phases
- Mentions disabled (`allowed_mentions.parse = []`); mention syntax stripped from localized text
- Commands: `/rwr discord reload` and `/rwr discord status` (`rwrdiscord.admin`, default op)

## Requirements

- Java 21+
- **Spigot**, **CraftBukkit**, **Paper**, **Purpur**, or **Folia** (Minecraft 1.21.4+)
- ResourceWorldResetter 5.1+ (the Spigot jar or the Paper/Folia jar that matches the server)

## Installation

1. Install ResourceWorldResetter for your server (**Spigot / CraftBukkit** → Spigot jar, **Paper / Purpur / Folia** → Paper-Folia jar).
2. Drop `RWR-Discord-Webhook-1.0.0.jar` into `plugins/`.
3. Start the server once to generate `plugins/RWR-Discord-Webhook/config.yml`.
4. Set `webhook.url` to your Discord incoming webhook URL.
5. `/rwr discord reload`


## Reliable delivery

- Asynchronous HTTP only — never blocks a Bukkit or Folia server thread
- Durable bounded queue (`pending-webhooks.json`): max **500** entries, **24h** TTL
- Up to **8** attempts with exponential backoff + jitter (cap **5 minutes**)
- Exact `Retry-After` handling for HTTP **429**
- Retries network errors and HTTP **5xx**; no retries for other HTTP **4xx**
- Queue file stores payload JSON only — **never** the webhook secret
- Graceful drain on disable; pending work resumes after restart

## Configuration

```yaml
locale: en_US

webhook:
  url: "https://discord.com/api/webhooks/..."
  username: "RWR"
  avatar_url: "https://files.catbox.moe/6pm9fu.png"
  timeout-ms: 8000
  queue-capacity: 64
  queue-ttl-hours: 24
  max-attempts: 8
  min-interval-seconds: 1

events:
  warnings: true
  success: true
  failures: true
  cancellations: true
  interrupted: true
```

Locale files: `plugins/RWR-Discord-Webhook/locales/en_US.yml` (bundled default). Missing keys fall back to the bundled English strings.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/rwr discord reload` | `rwrdiscord.admin` | Reload config, locale, and webhook client |
| `/rwr discord status` | `rwrdiscord.admin` | Report API availability, queue size, last success/failure, retry count |

The direct `/rwrdiscord reload|status` command remains available as a legacy alias. It is also
the fallback for checking add-on status if ResourceWorldResetter itself is unavailable and cannot
provide the `/rwr discord ...` namespace.

## Embed fields

Warning embeds include world ID, world name, remaining time, and the scheduled reset timestamp, with no operation ID. Terminal embeds retain the operation ID, phase, failure type, safety classification, message detail, and an ISO-8601 event timestamp.

## Build

```shell
# Requires rwr-api 5.1.2 available to Maven (Central or local install)
mvn -f RWR-Discord/pom.xml verify
```

Compile-time dependencies are `rwr-api` (provided) and `spigot-api` (provided). Gson is shaded into the jar.

## Metrics (bStats)

This plugin uses [bStats](https://bstats.org/plugin/bukkit/RWR-Discord-Webhook/33788) to collect anonymous server statistics (such as Minecraft version, online player count, and plugin version). No webhook URLs, Discord content, or player identities are sent. You can opt out globally at [bstats.org/optout](https://bstats.org/optout).

## License

BSD 3-Clause. See [LICENSE](LICENSE).

Public documentation: [Changelog](CHANGELOG.md). Marketplace paste files and development notes live in `docs/marketplace/` and `docs/dev/` and are excluded by `.gitignore`.
