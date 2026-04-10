# Contributing to Raccoon Wallet

Thank you for your interest in contributing to Raccoon Wallet. This document explains how to report issues, propose changes, and prepare contributions for review.

## Before You Start

- Read the [README.md](README.md) for project scope, build commands, and supported platforms.
- For protocol or signing-path changes, read [docs/protocol.md](docs/protocol.md) first.

## Reporting Bugs

Open a [bug report issue](../../issues/new?template=bug_report.yml) with:

- Your device model and Android version
- Whether you were using Vault or Signer mode
- Transport method (NFC or QR)
- Steps to reproduce
- Expected vs. actual behavior

## Suggesting Features

Open a [feature request issue](../../issues/new?template=feature_request.yml) describing your use case and any alternatives you considered.

## Security Vulnerabilities

Do **not** open a public issue for security vulnerabilities. See [SECURITY.md](SECURITY.md) for responsible disclosure instructions.

## Development Setup

1. Clone the repository
2. Open in Android Studio (latest stable)
3. Ensure JDK 11+ is configured (`File > Project Structure > SDK Location`)
4. Sync Gradle and build: `./gradlew assembleDebug`
5. Run unit tests: `./gradlew test`

Full signing flows require two physical Android devices with NFC.

## Code Style

- Follow standard [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- No automated formatter is enforced yet -- match the style of surrounding code
- Keep imports organized (no wildcards)
- Keep changes focused. Large refactors should be split from behavior changes when practical.
- Update documentation when behavior, setup steps, or security assumptions change.

## Pull Request Process

1. Fork the repository and create a feature branch from `main`
2. Make your changes with clear, focused commits
3. Ensure `./gradlew lint test assembleDebug` passes
4. Add or update tests when behavior changes
5. Open a pull request against `main` with a clear description of the problem, approach, and any security implications

### Security-critical code

Changes to files in `core/crypto/` affect signing correctness and fund safety. PRs touching this directory require:

- Extra review scrutiny
- Explanation of cryptographic reasoning
- Unit tests covering the change
- No changes to security invariants (CRIT-2, CRIT-5) without explicit discussion

### Dependency policy

This project deliberately minimizes external dependencies to reduce attack surface. Before adding a new dependency:

- Check if the functionality can be implemented in ~300 lines or less
- Prefer using existing deps (BouncyCastle, kotlinx.serialization, Android Keystore)
- No web3j, Hilt/Dagger/KSP, OkHttp/Retrofit, or Tink

## Review Expectations

- Expect extra scrutiny for changes affecting key generation, signing, NFC transport, persistence, or transaction broadcasting.
- Be explicit about threat-model assumptions and backward-compatibility risks.
- If a change is difficult to test end-to-end, describe how you validated it manually.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
