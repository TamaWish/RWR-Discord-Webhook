# Privacy Policy

**Product:** RWR-Discord  
**Effective date:** 29 August 2026  
**Last updated:** 29 August 2026

This Privacy Policy describes how the RWR-Discord Minecraft server plugin (“**Plugin**”, “**we**”, “**us**”) handles information when a server operator installs and runs it.

RWR-Discord is self-hosted software. We do not operate a central service that receives your server’s data by default. Processing occurs on the hardware you control and, when you configure a Discord webhook, on Discord’s infrastructure under Discord’s own terms and policies.

## 1. Who this policy applies to

This policy is directed at **server operators and administrators** who install the Plugin. End players on a Minecraft server typically do not interact with RWR-Discord directly.

## 2. Information the Plugin processes

Depending on configuration, the Plugin may process:

| Category | Examples | Purpose |
|----------|----------|---------|
| **Operational event data** | Resource world ID, world name, reset operation ID, reset phase, failure type, safety classification, diagnostic message text, timestamps | To build Discord embeds announcing reset warnings, successes, failures, cancellations, and interrupted operations |
| **Configuration** | Webhook URL (including secret token), locale, event toggles, embed colors, queue settings | Stored only in the Plugin data folder on your server |
| **Delivery queue state** | Pending embed JSON payloads, attempt counts, next-attempt times, non-secret error summaries | Durable delivery and resume after restart |

The Plugin does **not** intentionally collect player account credentials, payment data, or private Discord user tokens beyond the **incoming webhook URL** you paste into configuration.

## 3. Where data is stored

- **On your Minecraft server:** `plugins/RWR-Discord/` (for example `config.yml`, locale files, and `pending-webhooks.json`).
- **Discord:** When delivery succeeds, embed content is sent to the webhook endpoint you configure and is thereafter subject to [Discord’s Privacy Policy](https://discord.com/privacy) and your Discord server’s settings.

The durable queue file stores **notification payload JSON only**. The webhook URL and secret token are **not** written into the queue file; they are read from live configuration at send time.

## 4. Logging

Log lines may include world identifiers, operation IDs, HTTP status classes, and redacted error text. Webhook secrets are redacted from log output using pattern-based filtering. You remain responsible for protecting your server log files and access controls.

## 5. Network transmission

Outbound HTTPS requests are made only to the Discord webhook URL you configure (or related Discord API hosts implied by that URL). The Plugin does not phone home to the Plugin authors for analytics or licensing unless a future optional feature is documented and enabled by you.

## 6. Data retention

- Configuration remains until you delete it.
- Pending queue entries expire after the configured TTL (default and maximum **24 hours**) or after successful delivery / permanent failure / exhaustion of attempts.
- Discord retains message content according to Discord’s policies and your channel settings.

## 7. Children

The Plugin is infrastructure software for game servers. It is not directed at children. Server operators must comply with applicable laws (including age-related rules for their communities) when operating Minecraft and Discord services.

## 8. Your responsibilities

You are the controller of data on your server. You should:

- Keep webhook URLs secret and rotate them if exposed.
- Avoid putting personal data into RWR display names or messages that will be forwarded to Discord.
- Ensure your use of Discord webhooks complies with Discord’s terms and any local privacy law (for example GDPR, UK GDPR, or CCPA/CPRA) that applies to your operation.

## 9. Third parties

| Party | Role |
|-------|------|
| **Discord** | Receives webhook HTTP requests you authorize |
| **Hosting provider** | Stores server disk and logs under your hosting arrangement |
| **ResourceWorldResetter** | Supplies reset events via its public API on the same server; not a network recipient of Discord deliveries |

## 10. Changes

We may update this Privacy Policy in the Plugin repository. Material changes will be reflected by updating the “Last updated” date. Continued use of a new Plugin version after publication constitutes acceptance of the revised policy for that version.

## 11. Contact

For privacy questions about this Plugin’s design, open an issue or discussion in the public repository: [https://github.com/TamaWish/RWR-Discord](https://github.com/TamaWish/RWR-Discord).

For privacy requests about data held by Discord, contact Discord through their published channels.
