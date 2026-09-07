# Contributing to RWR-Discord Webhook

Thank you for taking an interest in this project. Documentation improvements, bug reports, feature ideas, translations, tests, and code are all welcome.

## Table of Contents

- [Ways to contribute](#ways-to-contribute)
- [I have a question](#i-have-a-question)
- [Reporting bugs](#reporting-bugs)
- [Security vulnerabilities](#security-vulnerabilities)
- [Suggesting enhancements](#suggesting-enhancements)
- [Finding something to work on](#finding-something-to-work-on)
- [Development setup](#development-setup)
- [Making changes](#making-changes)
- [Style guides](#style-guides)
- [Commit messages](#commit-messages)
- [Pull requests](#pull-requests)
- [Legal](#legal)
- [Getting help](#getting-help)

## Ways to contribute

- Ask a clarifying question (after checking the docs)
- Report a bug
- Suggest an enhancement
- Improve [README.md](README.md), [CHANGELOG.md](CHANGELOG.md), or other docs
- Add or improve locale files under `src/main/resources/locales/`
- Add or strengthen tests
- Submit a focused pull request

Do **not** use the public issue tracker for security vulnerabilities (see [Security vulnerabilities](#security-vulnerabilities)).

For installation and configuration, start with [README.md](README.md).

## I have a question

1. Read [README.md](README.md) (requirements, config, commands, and development notes).
2. Search [existing issues](https://github.com/TamaWish/RWR-Discord-Webhook/issues) for the same question.
3. If nothing fits, open a GitHub issue and describe what you already tried.

This repository does not enable GitHub Discussions.

## Reporting bugs

Before filing:

- Confirm you are on a current release (or a build from `main`)
- Rule out misconfiguration (empty/invalid `webhook.url`, missing ResourceWorldResetter / `RwrApi`, wrong jar for Spigot vs Paper/Folia)
- Search [existing issues](https://github.com/TamaWish/RWR-Discord-Webhook/issues)

A useful report includes:

- Expected behavior vs actual behavior
- Steps to reproduce
- Plugin version, ResourceWorldResetter version, server software (Spigot / Paper / Folia / etc.) and Minecraft version
- Java version and OS
- Relevant server logs (webhook secrets must stay redacted)

This repository has no issue templates yet; a clear free-form report is fine. The `bug` label is available for triage.

## Security vulnerabilities

> [!WARNING]
> Do not report security issues in the public issue tracker.

Use GitHub’s private vulnerability reporting for this repository (Security tab → Advisories / Report a vulnerability). Do not open a public issue or pull request that discloses the problem.

## Suggesting enhancements

1. Search existing issues for a similar request.
2. Open an issue that describes:
   - The problem or gap
   - Your proposed approach
   - Alternatives you considered
   - Why it helps most users of this add-on

Large or breaking changes should be discussed in an issue before a pull request. The `enhancement` label is available for triage.

## Finding something to work on

Browse open issues, especially:

- [`good first issue`](https://github.com/TamaWish/RWR-Discord-Webhook/labels/good%20first%20issue)
- [`help wanted`](https://github.com/TamaWish/RWR-Discord-Webhook/labels/help%20wanted)

Comment on the issue if you plan to work on it so others know it is taken.

## Development setup

### Prerequisites

- **JDK 21+** (bytecode targets `--release 21`)
- **Maven** (commands below match [`.github/workflows/ci.yml`](.github/workflows/ci.yml))

No `.env` file is required for building or testing.

### Quick start

```bash
git clone https://github.com/TamaWish/RWR-Discord-Webhook.git
```

```bash
cd RWR-Discord-Webhook
```

```bash
mvn --batch-mode clean verify
```

The shaded jar is written to `target/RWR-Discord-Webhook-<version>.jar`.

### Steps for a contribution fork

1. Fork the repository on GitHub.
2. Clone your fork and create a branch from `main`.
3. Make your changes.
4. Run the full verify command (format check, compile, tests) before opening a PR.
5. Apply formatting if Spotless reports diffs:

```bash
mvn spotless:apply
```

Compile-time dependencies (`rwr-api`, Spigot API) are resolved by Maven as `provided` scope. Gson and bStats are shaded into the jar. See [README.md](README.md#development) for more detail.

## Making changes

- Branch from the default branch: `main`
- Keep pull requests small and focused
- Match the existing package layout under `io.github.tamawish.rwr.discord` and the project’s terminology
- When behavior changes, update tests and docs (README / CHANGELOG) as needed
- Locale work: keep `{placeholder}` names and braces unchanged so keys stay compatible

### Build, test, and format

CI runs:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Locally you can use the same verify goal (with or without `--no-transfer-progress`):

```bash
mvn --batch-mode clean verify
```

That runs Spotless (`check`), compiles with `--release 21`, and executes the JUnit tests.

Format sources:

```bash
mvn spotless:apply
```

## Style guides

- **Language:** Java 21 (`maven.compiler.release` / `--release 21`)
- **Formatter:** Spotless with Google Java Format (GOOGLE style), including import cleanup and annotation formatting — see `pom.xml`
- **Tests:** JUnit 5, AssertJ, and Mockito (Surefire runs them as part of `verify`)
- **Commits:** Conventional Commits style (see below)

## Commit messages

Prefer Conventional Commits: `type: short summary`.

Types already used in this repository include `feat` and `chore`. Other common types such as `fix`, `docs`, `refactor`, and `test` fit the same style when accurate.

Example:

```text
feat: retry Discord webhook delivery on HTTP 5xx
```

## Pull requests

- Target `main`
- Keep the change focused; open separate PRs for unrelated work
- Link related issues (for example `Fixes #123`)
- Include tests when you change behavior that already has coverage, or when new logic needs it
- Update docs when user-facing behavior or setup changes
- Run `mvn --batch-mode clean verify` and ensure CI is green before asking for review

There is no pull request template in this repository yet. A short description of the problem, the approach, and how you tested is enough.

Ready for review means: the PR description is clear, verify passes locally, and the diff is limited to the stated goal.

## Legal

By submitting a contribution, you agree that it is licensed under the same [BSD 3-Clause License](LICENSE) as the rest of the project. This repository does not use a separate CLA or DCO process.

## Getting help

- Project docs: [README.md](README.md)
- Release notes: [CHANGELOG.md](CHANGELOG.md)
- Issues and pull requests: [TamaWish/RWR-Discord-Webhook](https://github.com/TamaWish/RWR-Discord-Webhook)
