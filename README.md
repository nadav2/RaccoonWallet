# Raccoon Wallet

A 2-of-2 threshold ECDSA wallet for Android using the [Lindell 2017](https://eprint.iacr.org/2017/552) protocol.

> **Warning:** This is experimental software (v0.1.0). It has not been formally audited. Do not use with significant funds.

## What is this?

Raccoon Wallet splits a single private key between two Android devices so that neither device can sign a transaction alone:

- **Vault** (online device) -- holds key share `x1`, builds transactions, finalizes signatures, and broadcasts to the network.
- **Signer** (air-gapped device) -- holds key share `x2`, reviews transactions, and computes partial signatures. Never touches the internet.

The two devices communicate over **NFC** (ISO-DEP APDU) with ECDH + AES-256-GCM session encryption. A QR code fallback is also available.

## Security Model

- Neither device can produce a valid signature alone -- both shares are required.
- The Signer device is designed to be kept air-gapped (no network access).
- All NFC communication is encrypted with a per-session ECDH key exchange.
- Key shares are stored encrypted via Android Keystore (AES-256-GCM), with optional biometric gating.
- The Vault verifies every signature via `ecrecover` before broadcasting.

For full protocol details, see [docs/protocol.md](docs/protocol.md).

## Supported Chains

| Chain     | Network          |
|-----------|------------------|
| Ethereum  | Mainnet          |
| BNB Chain | BSC Mainnet      |
| Polygon   | Mainnet          |
| Arbitrum  | One              |
| Avalanche | C-Chain          |

EVM-only. EIP-1559 transactions are used where supported.

## Architecture

Single `:app` module with a layered architecture:

```
core/
  crypto/       Lindell protocol, Paillier, secp256k1, BIP32/39, EIP-1559, RLP, Keccak-256
  transport/    NFC APDU (reader + HCE), ECDH session crypto, CBOR codec, QR fallback
  storage/      Encrypted key shares (Android Keystore), public data store
  network/      EVM JSON-RPC client
  model/        Domain types (AppMode, Account, Chain, Token, etc.)
feature/        MVVM screens (Jetpack Compose + ViewModels)
nav/            Compose Navigation with typed routes
ui/             Theme (Vault=blue, Signer=orange) and shared components
```

### Design philosophy

- **Minimal dependencies** -- no web3j, no Hilt/Dagger, no OkHttp/Retrofit. Crypto uses BouncyCastle directly. Network uses `HttpURLConnection`. DI is manual.
- **Small attack surface** -- fewer transitive dependencies means fewer supply-chain risks for a wallet app.
- **Pure Kotlin/Java crypto** -- all threshold ECDSA, Paillier encryption, and transaction building is implemented in `core/crypto/` using BouncyCastle primitives.

## Building

### Prerequisites

- Android Studio (latest stable)
- JDK 11+
- Android SDK with API 36 (compileSdk) and API 31+ device/emulator

### Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK (R8 minified)
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (device required)
./gradlew lint                   # Run Android lint
```

### Two-device testing

The full signing flow requires two physical Android devices with NFC. Install the app on both, choose **Vault** mode on one and **Signer** mode on the other, then tap to pair via NFC during DKG setup.

## Documentation

- [Protocol specification](docs/protocol.md) -- full cryptographic protocol, NFC transport, and security analysis
- [Device flow regression list](docs/device-flow-regression-list.md) -- manual QA checklist
- [CLAUDE.md](CLAUDE.md) -- architecture reference and development guide

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style, and contribution guidelines.

## Security

If you discover a security vulnerability, please report it responsibly. See [SECURITY.md](SECURITY.md) for details.

## License

```
Copyright 2024-2026 Raccoon Wallet Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

See [LICENSE](LICENSE) for the full text.
