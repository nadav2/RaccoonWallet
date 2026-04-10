# CLAUDE.md

This file serves as both an architecture reference for developers and guidance for AI coding assistants (e.g., [Claude Code](https://claude.ai/code)). It describes the project structure, build system, key design decisions, and security invariants.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK (R8 minified)
./gradlew test                   # Run JUnit unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (device/emulator required)
./gradlew lint                   # Run Android lint
```

Single module (`:app`), so no module prefix needed. AGP 9.0.1, Kotlin 2.3.20, minSdk 31, targetSdk 36, Java 11.

## What This Is

A 2-of-2 threshold ECDSA wallet for Android implementing the Lindell 2017 protocol. Two devices run the same app in different modes:

- **Vault** (online) — holds key share `x1` and Paillier secret key. Builds transactions, finalizes signatures, broadcasts.
- **Signer** (air-gapped) — holds key share `x2` and encrypted `ckey = Enc(pk, x1)`. Computes partial signatures homomorphically. User reviews/approves transactions here.

Neither device can sign alone. Communication happens over NFC (ISO-DEP APDU) with ECDH + AES-256-GCM session encryption.

EVM-only. Supported chains: Ethereum, BSC, Polygon, Arbitrum, Avalanche.

## Architecture

Single `:app` module. Package root: `io.raccoonwallet.app`.

### Layer structure

- **`core/crypto/`** — Lindell protocol (`LindellDkg`, `LindellSign`), Paillier homomorphic encryption, secp256k1 arithmetic, BIP32/BIP39 (via bitcoinj), EIP-1559 transaction building, RLP encoding, Keccak-256. All pure Kotlin/Java + BouncyCastle.
- **`core/transport/`** — NFC APDU transport (`nfc/NfcReaderTransport` for Vault reader, `nfc/RaccoonWalletHceService` for Signer HCE), APDU chunking for messages >248 bytes, ECDH session crypto (`SessionCrypto`), CBOR message codec (`MessageCodec`, `TransportMessage`).
- **`core/storage/`** — Two JSON stores: `PublicStore` (unencrypted — accounts, balances, tx history) and `SecretStore` (AES-256-GCM via Android Keystore — Paillier keys, key shares). Custom `KeystoreCipher` wraps Android Keystore directly (no Tink). Optional biometric gating.
- **`core/network/`** — `EvmRpcClient` for JSON-RPC over `HttpURLConnection`. No OkHttp/Retrofit.
- **`core/model/`** — Domain types: `AppMode`, `Account`, `Chain`, etc.
- **`feature/`** — MVVM screens using Jetpack Compose. Each feature has a ViewModel (`AndroidViewModel` + `viewModelScope`) and Composable screen. State modeled as sealed classes (e.g., `DkgState`, `SignState`).
- **`nav/`** — Compose Navigation with typed routes via kotlinx.serialization (`Routes.kt`).
- **`ui/`** — Theme (dynamic: Vault=blue, Signer=orange), reusable components.

### Dependency injection

Manual DI via `RaccoonWalletApp` application singleton. Access services through `application.deps`. No Hilt/Dagger/KSP.

### Key data flows

**DKG (setup):** Signer generates BIP39 seed -> derives 8 keys via BIP44 -> generates 2048-bit Paillier keypair -> splits each key multiplicatively (`x = x1 * x2 mod n`) -> sends `x1`, Paillier sk to Vault via NFC -> each device stores its shares encrypted.

**Signing:** Vault builds tx + hash -> generates ephemeral `k1`, sends `(hash, R1)` via NFC -> Signer displays tx for approval -> Signer computes homomorphic partial sig using `ckey` -> Vault decrypts, finalizes `(r, s, v)` -> verifies via ecrecover -> broadcasts.

See `docs/protocol.md` for the full cryptographic protocol specification.

### NFC protocol

APDU instruction codes: `0x10` SESSION_INIT, `0x20` DKG_ROUND1, `0x22` DKG_ROUND2, `0x30` SIGN_REQUEST, `0x32` POLL, `0xF0` CHUNK_CONTINUE. All messages after handshake are encrypted with the ECDH-derived session key. Messages >248 bytes are chunked across multiple APDUs.

## Dependency Policy

Prefer implementing small utilities directly (~300 lines) over pulling in heavy libraries. The app deliberately avoids web3j, Hilt/KSP, OkHttp/Retrofit, and Tink in favor of custom implementations using existing deps (BouncyCastle, kotlinx.serialization, HttpURLConnection, Android Keystore). This reduces attack surface and avoids transitive dependency issues with AGP 9.

**Allowed heavy deps:** BouncyCastle (EC math, Keccak, primes), bitcoinj-core (BIP32/BIP39 only, BouncyCastle excluded from transitive).

## Security-Critical Code

Files in `core/crypto/` implement threshold ECDSA. Changes here affect signing correctness and fund safety. Notable invariants:
- `CRIT-2`: Vault must verify its computed `r` matches Signer's `r` (prevents malicious partial sigs)
- `CRIT-5`: Vault must verify `ecrecover(hash, r, s, v) == expectedPubKey` before broadcast
- Low-S normalization (EIP-2) must be applied before computing recovery ID
- Constant-time comparison in `ConstantTime.kt` for nonce/crypto validation
- NFC session nonces must increment monotonically (replay prevention)
