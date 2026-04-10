# Security Policy

Raccoon Wallet is experimental software. Please use responsible disclosure so maintainers can investigate and ship fixes before vulnerabilities are publicly discussed.

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.1.x   | Yes       |

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Please report vulnerabilities by opening a [private security advisory](../../security/advisories/new) on GitHub.

Include:

- Description of the vulnerability
- Steps to reproduce or proof of concept
- Affected component (e.g., `core/crypto/`, `core/transport/`, `core/storage/`)
- Potential impact
- Any suggested mitigation or patch, if available

## Response Timeline

- **Acknowledgment:** within 48 hours
- **Initial assessment:** within 7 days
- **Fix or mitigation:** depends on severity, but we aim for 30 days for critical issues

## Scope

The following are in scope for security reports:

- Threshold ECDSA protocol flaws (key share leakage, signature forgery)
- NFC transport vulnerabilities (session hijacking, replay attacks, MITM)
- Android Keystore bypass or key material extraction
- Paillier encryption weaknesses in the implementation
- Any bug that could lead to loss of funds or private key compromise

Out of scope for this policy:

- Requests for general security hardening without a specific vulnerability
- Denial-of-service findings that require local physical access only
- Issues in third-party services or networks outside this repository

## Security Invariants

The following invariants are critical to the security of Raccoon Wallet:

- **CRIT-2:** Vault must verify its computed `r` matches the Signer's `r` (prevents malicious partial signatures)
- **CRIT-5:** Vault must verify `ecrecover(hash, r, s, v) == expectedPubKey` before broadcasting any transaction
- Low-S normalization (EIP-2) must be applied before computing the recovery ID
- Constant-time comparison must be used for nonce and cryptographic validation
- NFC session nonces must increment monotonically to prevent replay attacks

## Responsible Disclosure

We ask reporters not to disclose a vulnerability publicly until we have investigated it and released a fix or mitigation.

We credit reporters in release notes (unless you prefer anonymity). We do not pursue legal action against good-faith security researchers acting within this policy.
