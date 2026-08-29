# RWR-Discord

Discord webhook add-on for [ResourceWorldResetter](https://github.com/TamaWish/ResourceWorldResetter).

**Artifact:** `io.github.tamawish:rwr-discord`  
**Package:** `com.lozaine.resourceworldresetter.discord`  
**Repository:** [TamaWish/RWR-Discord](https://github.com/TamaWish/RWR-Discord)

## Features (v1.0)

- Soft-depends on both RWR runtimes (`ResourceWorldResetter` and `ResourceWorldResetter-Paper-Folia`)
- Uses only the public [RWR-API](https://github.com/TamaWish/RWR-API) (`RwrApi`, pre/post reset events)
- Disables delivery cleanly when `RwrApi` is unavailable
- Webhook URL and all Discord settings live exclusively in this plugin’s data folder
- Webhook secrets are redacted from logs
- Own locale system (`locale: en_US`, `locales/<code>.yml`, English fallback)
- Sends Discord embeds for:
  - configured reset **warnings** (`ResourceWorldPreResetEvent`)
  - **successful** resets
  - **failures**
  - **cancellations** (`EVENT_CANCELLED`)
  - **interrupted** operations
- Does **not** send intermediate reset phases
- Mentions disabled (`allowed_mentions.parse = []`); mention syntax stripped from localized text
- Commands: `/rwrdiscord reload` and `/rwrdiscord status` (`rwrdiscord.admin`, default op)

## Requirements

- Java 21+
- Spigot / Paper / Folia 1.21+
- ResourceWorldResetter 5.1+ (Spigot or Paper-Folia build) providing `rwr-api` 5.1.0

## Installation

1. Install RWR (Spigot or Paper-Folia) on the server.
2. Drop `RWR-Discord-1.0.0.jar` into `plugins/`.
3. Start the server once to generate `plugins/RWR-Discord/config.yml`.
4. Set `webhook.url` to your Discord incoming webhook URL.
5. `/rwrdiscord reload`


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
  avatar-url: ""
  timeout-ms: 8000
  queue-capacity: 64
  max-retries: 3
  min-interval-seconds: 1

events:
  warnings: true
  success: true
  failures: true
  cancellations: true
  interrupted: true
```

Locale files: `plugins/RWR-Discord/locales/en_US.yml` (bundled default). Missing keys fall back to the bundled English strings.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/rwrdiscord reload` | `rwrdiscord.admin` | Reload config, locale, and webhook client |
| `/rwrdiscord status` | `rwrdiscord.admin` | Report API availability, queue size, last success/failure, retry count |

## Embed fields

Each embed includes world ID, world name, operation ID, phase (when terminal), failure type, safety classification, localized explanation, and an ISO-8601 timestamp.

## Build

```shell
# Requires rwr-api 5.1.0 available to Maven (Central or local install)
mvn -f RWR-Discord/pom.xml verify
```

Compile-time dependencies are `rwr-api` (provided) and `spigot-api` (provided). Gson is shaded into the jar.

## License

BSD 3-Clause. See [LICENSE](LICENSE).
