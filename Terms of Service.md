# Terms of Service

**Product:** RWR-Discord Webhook  
**Plugin id:** `RWR-Discord-Webhook`  
**Effective date:** 29 August 2026  
**Last updated:** 1 September 2026

These Terms of Service (“**Terms**”) govern download, installation, and use of **RWR-Discord Webhook** (“**Plugin**”). By using the Plugin, you agree to these Terms. If you do not agree, do not install or use it.

## 1. License

The Plugin source and binaries are provided under the **BSD 3-Clause License** included in the distribution (`LICENSE`). These Terms do not reduce the permissions granted by that license. Where the BSD license and these Terms conflict on license grants, the BSD license controls for copyright permissions; these Terms control operational use expectations and disclaimers of service-level obligations.

## 2. Description of the Plugin

RWR-Discord Webhook is an optional, **webhook-only** add-on for ResourceWorldResetter. It listens to public RWR API reset events on your Minecraft server and delivers configured notifications to a Discord **incoming webhook** that **you** supply.

**What this Plugin is**

- A self-hosted Minecraft server plugin (`RWR-Discord-Webhook`) that posts embed notifications via HTTPS to your webhook URL
- Dependent on ResourceWorldResetter and the public RWR API on the same server
- Distributed as `io.github.tamawish:rwr-discord-webhook` (Maven artifact) with package `io.github.tamawish.rwr.discord`

**What this Plugin is not**

- A Discord bot application (no bot token, no Gateway connection, no slash commands in Discord, no channel read access)
- A hosted SaaS product with guaranteed uptime, moderation, or Discord account services
- A combined bot + webhook product; a separate Discord bot add-on may ship later under a different plugin identity

Webhook delivery settings (URL, username, avatar, queue behavior, and event toggles) live exclusively in this Plugin’s data folder. Legal links to these Terms and the [Privacy Policy](Privacy%20Policy.md) are included in outbound embed descriptions by default and are not configurable in `config.yml`.

## 3. Eligibility and authority

You represent that you have authority to install plugins on the target Minecraft server and to create or configure a Discord **incoming webhook** for the destination channel or server.

## 4. Acceptable use

You agree not to use the Plugin to:

- Violate Discord’s Terms of Service, Developer Terms, or community guidelines
- Spam, harass, or unlawfully target individuals or groups
- Attempt to bypass Discord rate limits in a way that abuses Discord infrastructure beyond normal retry behavior
- Probe, disrupt, or gain unauthorized access to systems you do not operate
- Distribute malware or misrepresent the origin of messages in a deceptive or unlawful manner
- Present the Plugin as a Discord bot or imply capabilities it does not provide (webhook-only delivery)

You are solely responsible for content forwarded to Discord (including world names and diagnostic strings originating from your server configuration).

## 5. Configuration and secrets

Webhook URLs contain secret tokens. You must:

- Store them only in the Plugin data folder or other controls you secure
- Not commit them to public repositories
- Rotate compromised webhooks promptly in the Discord UI

The Plugin attempts to redact secrets from logs and to avoid persisting webhook secrets in the durable queue file, but you remain responsible for server access control.

Do not substitute a Discord bot token for a webhook URL. The Plugin is designed for incoming webhooks only.

## 6. Dependencies and third-party services

Reliable operation may require:

- A compatible ResourceWorldResetter runtime exposing `RwrApi` (Spigot or Paper/Folia build matching your server)
- A reachable Discord incoming webhook endpoint
- Correct Java and server platform versions as documented in the README

Third-party outages (Discord, your host, DNS, or RWR) may delay or prevent delivery. The durable queue and retry policy mitigate transient failures but do not guarantee delivery.

## 7. No warranty

THE PLUGIN IS PROVIDED “AS IS” AND “AS AVAILABLE,” WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT. WE DO NOT WARRANT THAT WEBHOOK DELIVERY WILL BE UNINTERRUPTED, TIMELY, SECURE, OR ERROR-FREE.

## 8. Limitation of liability

TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE AUTHORS AND COPYRIGHT HOLDERS SHALL NOT BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES, OR ANY LOSS OF PROFITS, DATA, GOODWILL, OR BUSINESS OPPORTUNITY, ARISING FROM USE OF THE PLUGIN—EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGES. AGGREGATE LIABILITY FOR CLAIMS RELATING TO THE PLUGIN SHALL NOT EXCEED THE GREATER OF (A) THE AMOUNT YOU PAID THE AUTHORS FOR THE PLUGIN IN THE TWELVE MONTHS BEFORE THE CLAIM (TYPICALLY ZERO FOR FREE DISTRIBUTION) OR (B) TWENTY-FIVE US DOLLARS (US$25).

## 9. Indemnity

You agree to indemnify and hold harmless the authors from claims arising out of your server configuration, Discord webhook usage, player disputes, or violation of these Terms or applicable law—except to the extent caused by the authors’ willful misconduct where such limitation is not permitted.

## 10. Updates and breaking changes

The authors may publish updates that change configuration keys, delivery behavior, or supported Minecraft/API versions. You are responsible for reading release notes and testing before production use.

Future Discord integration products (for example a bot add-on) may be released separately with their own terms.

## 11. Termination

You may stop using the Plugin at any time by removing it from your server and deleting its data folder. Rights that survive by nature (license grants already exercised under BSD, disclaimers, limitations of liability) continue.

## 12. Governing law

Unless mandatory local law requires otherwise, these Terms are interpreted under the laws of the jurisdiction of the primary copyright holder as published in the repository, without regard to conflict-of-law rules. Courts in that jurisdiction are the exclusive venue for disputes that cannot be resolved informally—subject to any non-waivable consumer protections that apply to you.

## 13. Changes to these Terms

Updated Terms may be published in the Plugin repository. The “Last updated” date will change when revisions are made. Using a new version of the Plugin after updated Terms are published constitutes acceptance of those Terms for that version.

## 14. Contact

Project repository: [https://github.com/TamaWish/RWR-Discord-Webhook](https://github.com/TamaWish/RWR-Discord-Webhook)

## 15. Related documents

- [Privacy Policy.md](Privacy%20Policy.md)
- [LICENSE](LICENSE) (BSD 3-Clause)
- [README.md](README.md)
