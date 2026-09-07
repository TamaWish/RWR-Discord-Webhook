# RWR-Discord Webhook

Discord webhook notifications for [ResourceWorldResetter](https://github.com/TamaWish/ResourceWorldResetter) on Spigot, CraftBukkit, Paper, Purpur, and Folia.

[![CI](https://github.com/TamaWish/RWR-Discord-Webhook/actions/workflows/ci.yml/badge.svg)](https://github.com/TamaWish/RWR-Discord-Webhook/actions/workflows/ci.yml)
[![License: BSD-3-Clause](https://img.shields.io/github/license/TamaWish/RWR-Discord-Webhook)](LICENSE)
[![Release](https://img.shields.io/github/v/release/TamaWish/RWR-Discord-Webhook)](https://github.com/TamaWish/RWR-Discord-Webhook/releases)

**Artifact:** `io.github.tamawish:rwr-discord-webhook`  
**Package:** `io.github.tamawish.rwr.discord`  
**Repository:** [TamaWish/RWR-Discord-Webhook](https://github.com/TamaWish/RWR-Discord-Webhook)

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Configuration](#configuration)
- [Commands](#commands)
- [Reliable delivery](#reliable-delivery)
- [Embeds](#embeds)
- [Development](#development)
- [Contributing](#contributing)
- [Metrics](#metrics-bstats)
- [License](#license)
- [Related links](#related-links)

## Features

- Soft-depends on both RWR runtimes (`ResourceWorldResetter` and `ResourceWorldResetter-Paper-Folia`)
- Uses the public [RWR-API](https://github.com/TamaWish/RWR-API) service and reset events only
- Disables event delivery cleanly when `RwrApi` is unavailable
- Webhook URL and Discord settings live in this plugin’s data folder
- Webhook secrets are redacted from logs and never stored in the durable queue
- Posts a configuration confirmation (RWR API, add-on version, server) after startup or reload
- Own locale system (`locale: en_US`, `locales/<code>.yml`, English fallback)
- Discord embeds for configured reset **warnings**, **success**, **failures**, **cancellations** (`EVENT_CANCELLED`), and **interrupted** operations — not intermediate reset phases
- Optional role/user mentions from an allow-list; locale and event text are sanitized so they cannot ping
- Folia-supported; HTTP delivery stays off the server thread

## Requirements

- **Java 21+** (bytecode is `--release 21`)
- **Spigot**, **CraftBukkit**, **Paper**, **Purpur**, or **Folia** (`api-version` `1.21`; compiled against Spigot API `1.21.4`)
- **ResourceWorldResetter 5.1+** (Spigot jar, or Paper/Folia jar matching the server) for reset notifications

> [!NOTE]
> Without a running RWR runtime that registers `RwrApi`, the add-on still loads but does not forward reset events.

## Installation

1. Install ResourceWorldResetter for your server (**Spigot / CraftBukkit** → Spigot jar, **Paper / Purpur / Folia** → Paper-Folia jar).
2. Download `RWR-Discord-Webhook-1.0.0.jar` from [Releases](https://github.com/TamaWish/RWR-Discord-Webhook/releases), or build it locally (see [Development](#development)).
3. Place the jar in `plugins/`.
4. Start the server once to generate `plugins/RWR-Discord-Webhook/config.yml`.
5. Set `webhook.url` to your Discord incoming webhook URL.
6. Run `/rwrdiscord reload` (or `/rwr discord reload` if that namespace is available).

### Quick start (build from source)

```bash
git clone https://github.com/TamaWish/RWR-Discord-Webhook.git
cd RWR-Discord-Webhook
mvn --batch-mode clean verify
```

The shaded jar is written to `target/RWR-Discord-Webhook-1.0.0.jar`.

## Configuration

All settings are in `plugins/RWR-Discord-Webhook/config.yml`. Leave `webhook.url` empty to disable delivery.

| Key | Required | Default | Description |
|-----|----------|---------|-------------|
| `locale` | No | `en_US` | Locale code; loads `locales/<code>.yml` from the data folder |
| `webhook.url` | Yes (for delivery) | `""` | HTTPS Discord incoming webhook URL |
| `webhook.username` | No | `RWR` | Webhook display name |
| `webhook.avatar_url` | No | bundled default URL | Webhook avatar |
| `webhook.timeout-ms` | No | `8000` | HTTP timeout (minimum `1000`) |
| `webhook.queue-capacity` | No | `500` | Durable queue size (clamped `1`–`500`) |
| `webhook.queue-ttl-hours` | No | `24` | Pending entry TTL in hours (clamped `1`–`24`) |
| `webhook.max-attempts` | No | `8` | Total delivery attempts including the first (clamped `1`–`8`) |
| `webhook.min-interval-seconds` | No | `1` | Minimum spacing between HTTP posts |
| `events.warnings` | No | `true` | Send configured RWR warning thresholds |
| `events.success` | No | `true` | Send terminal `COMPLETE` resets |
| `events.failures` | No | `true` | Send terminal `FAILED` resets |
| `events.cancellations` | No | `true` | Send `EVENT_CANCELLED` failures |
| `events.interrupted` | No | `true` | Send `INTERRUPTED` recoveries |
| `embeds.*-color` | No | see `config.yml` | Decimal RGB embed colors per category |
| `mentions.enabled` | No | `false` | Allow configured role/user pings |
| `mentions.roles.*` | No | `""` | Discord role snowflake IDs (`all`, per category) |
| `mentions.users` | No | `[]` | Discord user snowflake IDs |

Example:

```yaml
locale: en_US

webhook:
  url: "https://discord.com/api/webhooks/..."
  username: "RWR"
  avatar_url: "https://files.catbox.moe/6pm9fu.png"
  timeout-ms: 8000
  queue-capacity: 500
  queue-ttl-hours: 24
  max-attempts: 8
  min-interval-seconds: 1

events:
  warnings: true
  success: true
  failures: true
  cancellations: true
  interrupted: true

mentions:
  enabled: false
  roles:
    all: ""
    configuration: ""
    warnings: ""
    success: ""
    failures: ""
    cancellations: ""
    interrupted: ""
  users: []
```

Locale files: `plugins/RWR-Discord-Webhook/locales/en_US.yml` (bundled default). Missing keys fall back to bundled English strings. Placeholders use `{name}` form — keep the braces and names unchanged.

> [!IMPORTANT]
> `webhook.url` must be an HTTPS Discord incoming webhook (`discord.com` / `discordapp.com`). Invalid URLs fail reload validation.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/rwrdiscord reload` | `rwrdiscord.admin` (default: op) | Reload config, locale, and webhook client |
| `/rwrdiscord status` | `rwrdiscord.admin` (default: op) | Report API availability, webhook state, queue size, last success/failure, session retries |

Aliases: `rwr-discord`, `rwr-discord-webhook`, `rwrwh`.

In-game help text and `config.yml` comments also refer to `/rwr discord reload|status` as the preferred wording when that namespace is available from ResourceWorldResetter.

## Reliable delivery

- Asynchronous HTTP only — never blocks a Bukkit or Folia server thread
- Durable bounded queue (`pending-webhooks.json`): max **500** entries, **24h** TTL
- Up to **8** attempts with exponential backoff + jitter (cap **5 minutes**)
- Exact `Retry-After` handling for HTTP **429**
- Retries network errors and HTTP **5xx**; no retries for other HTTP **4xx**
- Queue file stores payload JSON only — never the webhook secret
- Graceful drain on disable; pending work resumes after restart

## Embeds

- **Warning:** world ID, world name, remaining time, scheduled reset timestamp (no operation ID)
- **Terminal** (success / failure / cancellation / interrupted): operation ID, phase, failure type, safety classification, message detail, ISO-8601 embed timestamp
- Every embed description appends hard-coded links to the [Privacy Policy](Privacy%20Policy.md) and [Terms of Service](Terms%20of%20Service.md) (not configurable in `config.yml`)

When `mentions.enabled` is `false`, payloads use `allowed_mentions.parse = []`. When enabled, only the configured role/user IDs may ping; mention syntax in locale strings and RWR event messages is stripped.

## Development

Requires JDK 21+ and Maven.

```bash
mvn --batch-mode clean verify
```

That runs Spotless (Google Java Format), compiles with `--release 21`, and executes tests — matching [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

Compile-time dependencies (both `provided`): [`rwr-api` 5.1.2](https://repo1.maven.org/maven2/io/github/tamawish/rwr-api/5.1.2/) from Maven Central and Spigot API. Gson and bStats are shaded into the jar.

```bash
mvn spotless:apply
```

Apply formatting before commit if Spotless reports diffs.

See [CHANGELOG.md](CHANGELOG.md) for release history.

## Contributing

1. Open an issue or pull request on [GitHub](https://github.com/TamaWish/RWR-Discord-Webhook).
2. Keep changes focused; match existing package layout and terminology.
3. Run `mvn --batch-mode clean verify` before opening a PR.

## Metrics (bStats)

RWR-Discord-Webhook uses **bStats** for anonymous server usage statistics. It reports under [plugin ID 33788](https://bstats.org/plugin/bukkit/RWR-Discord-Webhook/33788). No webhook URLs, Discord content, or player identities are sent.

To opt out, open `plugins/bStats/config.yml` and set:

```yaml
enabled: false
```

This is controlled by bStats, not RWR-Discord-Webhook's own `config.yml`.

## License

[BSD 3-Clause](https://opensource.org/license/bsd-3-clause). See [LICENSE](LICENSE).

## Related links

- [Privacy Policy](Privacy%20Policy.md)
- [Terms of Service](Terms%20of%20Service.md)
- [ResourceWorldResetter](https://github.com/TamaWish/ResourceWorldResetter)
- [RWR-API](https://github.com/TamaWish/RWR-API)
