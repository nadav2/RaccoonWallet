# Raccoon Wallet Threshold ECDSA Protocol

## Overview

Raccoon Wallet implements a 2-of-2 threshold ECDSA signature scheme based on Lindell 2017. Two Android devices (Vault and Signer) each hold a piece of the private key. Neither device can sign alone. Both must cooperate via NFC or QR to produce a valid Ethereum transaction signature.

---

## Key Concepts

### Multiplicative Key Shares

A standard ECDSA private key is a single number `x`. In our threshold scheme, we split it multiplicatively:

```
x = x1 * x2  (mod n)
```

Where `n` is the secp256k1 curve order. The Vault holds `x1`, the Signer holds `x2`. Neither knows `x`.

The public key is:
```
Q = x * G = (x1 * x2) * G
```

Both parties can verify `Q` independently using each other's public point.

### Paillier Encryption (The Asymmetry)

The Lindell protocol uses Paillier homomorphic encryption to let the Signer compute a partial signature *on encrypted data* without seeing the Vault's share.

**Paillier properties:**
- `Enc(a) * Enc(b) = Enc(a + b)` (homomorphic addition)
- `Enc(a)^k = Enc(a * k)` (homomorphic scalar multiplication)
- Only the holder of the Paillier secret key can decrypt

**Key distribution:**
- Vault holds: Paillier secret key (lambda, mu)
- Signer holds: Paillier public key (n, g)
- Signer also holds: `ckey = Enc(pk, x1)` --- the Vault's share encrypted under Paillier

This `ckey` is the magic ingredient. The Signer can mathematically manipulate `ckey` (which contains the encrypted `x1`) without ever decrypting it.

---

## Setup (DKG)

The Signer device generates the wallet and distributes shares:

### Step 1: Seed Generation (Signer)
```
Generate 12-word BIP39 mnemonic (128 bits entropy)
Derive 64-byte seed via PBKDF2-HMAC-SHA512
Derive 8 private keys via BIP44: m/44'/60'/0'/0/0..7
```

### Step 2: Paillier Key Generation (Signer)
```
Generate 2048-bit Paillier keypair:
  Public key:  (n, g)         --- shared with Vault
  Secret key:  (lambda, mu)   --- sent to Vault
  g = n + 1                   --- simplified generator
```

### Step 3: Key Splitting (Signer)

For each of the 8 accounts:
```
Full private key: x_i (from BIP44 derivation)

Generate random: x1_i  (Vault's share)
Compute:         x2_i = x_i * x1_i^(-1) mod n  (Signer's share)
Verify:          x1_i * x2_i = x_i mod n

Compute:         ckey_i = PaillierEncrypt(pk, x1_i)
Compute:         Q_i = x_i * G  (joint public key)
Derive:          address_i = keccak256(Q_i.uncompressed[1:])[12:]  (Ethereum address)
```

### Step 4: Distribution (Signer -> Vault via NFC or QR)

The Signer builds a `DkgRound2` message containing:
```
q1Points: [lambda, mu, n, g, compressed(Q_0), ..., compressed(Q_7)]
ckeys:    [x1_0.toByteArray(), ..., x1_7.toByteArray()]
```

Note: despite the field name `ckeys`, the array carries raw `x1` shares (Vault's key portions),
not the Paillier-encrypted ckeys. The actual `ckey_i` values are stored only on the Signer.

After transfer, each device stores:

**Vault stores (encrypted via Android Keystore):**
```
- Paillier secret key: lambda, mu
- Paillier public key: n, g
- For each account: x1_i (its share), Q_i (joint public key)
```

**Signer stores (encrypted via Android Keystore):**
```
- Paillier public key: n, g
- For each account: x2_i (its share), ckey_i, Q_i (joint public key)
```

### Step 5: Cleanup

Signer zeroes the seed bytes and clears the mnemonic reference. Full private keys and split
results are dereferenced for garbage collection. After setup, the full private key `x` exists
nowhere.

---

## Signing Protocol

When the Vault wants to send a transaction:

### Step 1: Transaction Preparation (Vault)
```
Build EIP-1559 transaction (nonce, gas, to, value, chainId)
Compute: hash = keccak256(0x02 || RLP(unsigned_tx))

Generate random: k1  (Vault's ephemeral nonce)
Compute:         R1 = k1 * G

Send SignRequest to Signer via NFC:
  { sessionId, accountIndex, chainId, txHash, r1Point, displayData }
```

### Step 2: User Approval (Signer)

Signer displays transaction details to the user:
```
Chain: Ethereum
To: 0xABC...
Amount: 0.1 ETH
Fee: ~0.001 ETH
```

User approves or rejects.

### Step 3: Partial Signature (Signer)

If approved, the Signer computes the partial signature *homomorphically*:

```
Generate random: k2  (Signer's ephemeral nonce)
Compute:         R2 = k2 * G
Compute:         R  = k2 * R1 = k1 * k2 * G
                 r  = R.x mod n

Validate: R1 is on the secp256k1 curve, r != 0

Compute k2_inv = k2^(-1) mod n

// The key step --- homomorphic computation on ckey:
// We want to compute Enc(pk, k2_inv * (hash + r * x))
// where x = x1 * x2

// Step A: Enc(pk, x1)^(k2_inv * r * x2) = Enc(pk, k2_inv * r * x2 * x1)
exponent = k2_inv * r * x2 mod n
c_step1 = PaillierMulConstant(pk, ckey, exponent)

// Step B: Add k2_inv * hash
addend = k2_inv * hash mod n
c_partial = PaillierAddConstant(pk, c_step1, addend)

// Result: c_partial = Enc(pk, k2_inv * r * x2 * x1 + k2_inv * hash)
//                   = Enc(pk, k2_inv * (hash + r * x))
```

Send SignResponse to Vault: `{ sessionId, r2Point, cPartial, approved: true }`

Note: the Signer returns its computed `r` as part of the `SignerPartialResult` for
verification by the Vault (CRIT-2).

### Step 4: Signature Finalization (Vault)

The Vault decrypts and completes the signature:

```
Compute:   R = k1 * R2 = k1 * k2 * G
           r = R.x mod n

Validate: R2 is on the secp256k1 curve, r != 0

// CRIT-2: Verify Vault's r matches Signer's r --- prevents malicious partial sigs
assert r == signer's r

// Decrypt the partial signature (with RSA blinding for timing side-channel mitigation)
s' = PaillierDecrypt(sk, c_partial) mod n
   = k2_inv * (hash + r * x)

// Apply Vault's nonce inverse
s = k1_inv * s' mod n
  = (k1 * k2)_inv * (hash + r * x)
  = k_inv * (hash + r * x)

// This is a standard ECDSA signature!

// Low-S normalization (EIP-2 / BIP-62)
if s > n/2: s = n - s

// Compute recovery ID (v = 27 or 28) by trying recId 0 and 1
// and checking which recovers to the expected public key Q
v = 27 + recId where ecrecover(r, s, hash, recId) == Q

// HIGH-5: Verify signature before broadcast --- catches implementation bugs
assert ecrecover(hash, r, s, v) == Q
```

### Step 5: Broadcast (Vault)

```
Encode signed EIP-1559 transaction:
  0x02 || RLP([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas,
               gasLimit, to, value, data, accessList,
               yParity, r, s])

where yParity = v - 27  (EIP-1559 uses 0/1, not 27/28)

Send via eth_sendRawTransaction to RPC endpoint
```

---

## NFC Transport Protocol

Communication uses ISO-DEP (ISO 14443-4) with APDU commands. The Vault acts as the NFC
reader (initiates), the Signer runs an HCE (Host Card Emulation) service that responds.

### AID

```
F0 46 4F 58 57 41 4C 4C  (ASCII: 0xF0 + "FOXWALL")
```

### APDU Format

Commands from Vault to Signer:
```
[CLA=0x00] [INS] [P1=0x00] [P2=0x00] [Lc] [Data...]
```

Responses from Signer to Vault:
```
[Data...] [SW1=0x90] [SW2=0x00]   (success)
[SW1=0x69] [SW2=0x85]             (not ready)
[SW1=0x6F] [SW2=0x00]             (error)
```

Max APDU data field: 255 bytes.

### Instruction Codes

| INS  | Name             | Direction       | Purpose                                   |
|------|------------------|-----------------|-------------------------------------------|
| 0x10 | SESSION_INIT     | Vault -> Signer | ECDH handshake (ephemeral public key)     |
| 0x22 | DKG_ROUND2       | Vault -> Signer | Request DKG bundle (empty) or send ACK    |
| 0x30 | SIGN_REQUEST     | Vault -> Signer | Deliver sign request (encrypted)          |
| 0x32 | POLL             | Vault -> Signer | Poll for sign response                    |
| 0xF0 | CHUNK_CONTINUE   | Vault -> Signer | Fetch next chunk of a multi-chunk response|

### Session Handshake

All messages after the handshake are encrypted with AES-256-GCM using the derived session key.

```
1. Vault  -> Signer:  SELECT AID
   Signer -> Vault:   SW_OK

2. Vault  -> Signer:  INS_SESSION_INIT [16-byte salt + 65-byte P-256 ephemeral pubkey]
   Signer -> Vault:   [16-byte salt + 65-byte P-256 ephemeral pubkey] + SW_OK
```

Both sides then:
- XOR the two 16-byte salts to produce a combined salt
- Perform ECDH on P-256 (secp256r1) to derive a shared secret
- Derive session key via HKDF-SHA256(sharedSecret, combinedSalt, info="raccoonwallet-nfc", len=32)

**Nonce construction** (12 bytes): `sessionSalt[0..3] || counter(8 bytes, big-endian)`

Encrypt and decrypt counters are independent and increment per message. CRIT-4: the receiver
validates the nonce matches the expected counter to prevent replay attacks.

### DKG NFC Flow

```
1. Vault  -> Signer:  INS_DKG_ROUND2 (empty data)  --- "give me the bundle"
   Signer -> Vault:   [2-byte totalChunks] [chunk 0 data] + SW_OK

2. For each remaining chunk (i = 1 .. totalChunks-1):
   Vault  -> Signer:  INS_CHUNK_CONTINUE (empty data)
   Signer -> Vault:   [chunk i data] + SW_OK

3. Vault reassembles chunks, decrypts, decodes CBOR -> DkgRound2 message
4. Vault stores shares, then sends encrypted ACK:

   Vault  -> Signer:  INS_DKG_ROUND2 [encrypted DkgFinalize(ack=true)]
   Signer -> Vault:   SW_OK
```

Response-side chunking splits the encrypted DKG bundle into 248-byte chunks (max response data,
leaving 2 bytes for the SW status word).

### Signing NFC Flow

```
Tap 1 (Vault initiates):
1. Handshake (SELECT AID + SESSION_INIT)
2. Vault  -> Signer:  INS_SIGN_REQUEST [encrypted SignRequest]
   Signer -> Vault:   SW_OK
3. Signer emits SignRequest to the app UI for user review

Tap 2 (Vault polls after user approves on Signer):
4. Vault  -> Signer:  INS_POLL (empty)
   Signer -> Vault:   [encrypted SignResponse] + SW_OK
   (or SW_NOT_READY if user hasn't approved yet)
```

### Message Serialization

All transport messages are serialized using CBOR (kotlinx.serialization) for compact binary
encoding. The message types are:

| Message              | Fields                                                          |
|----------------------|-----------------------------------------------------------------|
| SessionInit          | ephemeralPubKey: ByteArray                                      |
| SessionInitResponse  | ephemeralPubKey: ByteArray                                      |
| DkgRound2            | q1Points, ckeys: List\<ByteArray\>                              |
| DkgFinalize          | ack: Boolean                                                    |
| SignRequest          | sessionId, accountIndex, chainId, txHash, r1Point, displayData |
| SignResponse         | sessionId, r2Point, cPartial: ByteArray, approved: Boolean     |
| PollRequest          | sessionId: String                                               |
| Error                | code: Int, message: String                                      |

---

## Why Neither Device Can Sign Alone

### Vault alone (has x1, Paillier sk):
- Can generate k1, R1
- Can decrypt Paillier ciphertexts
- CANNOT compute the homomorphic partial signature --- needs `ckey` and `x2`
- Without the partial signature, there's nothing to decrypt

### Signer alone (has x2, ckey, Paillier pk):
- Can compute the homomorphic partial signature -> `c_partial`
- CANNOT decrypt `c_partial` --- needs Paillier secret key (lambda, mu)
- The result is an encrypted number, useless without decryption

### Attacker with both x1 and x2:
- Can reconstruct `x = x1 * x2 mod n` -> full private key
- This is why the threshold split matters --- neither share alone reveals `x`

---

## Paillier Cryptosystem Details

### Key Generation
```
Choose two large primes p, q (1024 bits each)
Ensure: p != q, n.bitLength() == 2048, gcd(lambda, n) == 1
n = p * q           (2048-bit modulus)
g = n + 1           (simplified generator)
lambda = lcm(p-1, q-1)
mu = L(g^lambda mod n^2)^(-1) mod n
  where L(x) = (x - 1) / n
Verify: L(g^lambda) is invertible mod n (gcd check)
```

### Encryption
```
Enc(pk, m) = g^m * r^n mod n^2
  where r is random in Z*_n (coprime to n)
  m is reduced mod n before encryption
```

### Decryption (with RSA blinding)
```
// Blinding: multiply c by Enc(0) = r^n mod n^2 to randomize modPow input
blindFactor = r^n mod n^2        (r random in Z*_n)
blindedC = c * blindFactor mod n^2

// Decrypt (result unchanged: m + 0 = m)
Dec(sk, blindedC) = L(blindedC^lambda mod n^2) * mu mod n
```

The RSA blinding mitigates timing side-channels on the `BigInteger.modPow` operation.

### Homomorphic Properties
```
Enc(m1) * Enc(m2) mod n^2 = Enc(m1 + m2)       // additive
Enc(m)^k mod n^2 = Enc(m * k)                    // scalar multiplication (k reduced mod n)
Enc(m) * g^k mod n^2 = Enc(m + k)                // add constant (k reduced mod n)
```

These properties are what make threshold ECDSA possible --- the Signer manipulates encrypted values without seeing the plaintext.

---

## Security Properties

1. **Threshold security**: No single device holds the full private key
2. **Encryption at rest**: Key shares encrypted with AES-256-GCM via Android Keystore
3. **Biometric gating** (optional): Keystore key requires fingerprint/face to use (30-sec auth window)
4. **NFC session encryption**: ECDH (P-256) + AES-256-GCM with counter-based nonces
5. **Replay prevention**: Counter-based nonce validation (CRIT-4)
6. **Signature verification**: Vault verifies ecrecover matches expected public key before broadcast (HIGH-5)
7. **r-value verification**: Vault's computed r must match Signer's r (CRIT-2)
8. **Low-S normalization**: EIP-2 / BIP-62 compliance
9. **Timing side-channel mitigation**: RSA blinding in Paillier decryption

---

## Security Weaknesses & Inherent Limitations

### Signer is fully trusted during DKG

The Signer generates the BIP39 seed, derives all private keys, chooses the random x1 splits,
and only then sends Vault its share. This means a compromised Signer at setup time can:

- Generate a weak or predictable seed
- Remember the full private keys after "deleting" them
- Choose x1 values that leak information

This is **inherent to the Lindell 2017 two-party model** when one party generates the key.
A truly interactive DKG (where both devices contribute entropy and neither sees the full key)
would require a fundamentally different protocol with commitment schemes and zero-knowledge
proofs, adding significant complexity and multiple additional NFC round-trips. The current
approach is the standard trade-off: trust the Signer during setup, enforce threshold security
for all subsequent operations.

### Private keys are not explicitly zeroed

After key splitting, the full private keys (`BigInteger` values) are dereferenced and left
to garbage collection. The seed bytes are explicitly zeroed (`seed.fill(0)`), but the JVM
does not guarantee when or whether GC'd memory is overwritten. A memory dump of the Signer
device during or shortly after DKG could recover the full keys.

This is **inherent to JVM-based cryptography**. `BigInteger` is immutable --- there is no way
to zero its internal byte array. The only mitigation would be JNI with native memory
management, which contradicts the pure Kotlin/Java design constraint. The window of exposure
is limited to the DKG session on the Signer device.

### Single-app architecture means both roles share a binary

The Vault and Signer are the same APK in different modes. A supply-chain attack on the APK
compromises both roles simultaneously. Separate apps (or a dedicated hardware signer) would
limit blast radius.

This is a **practical trade-off for development simplicity**. The security model already
assumes the Signer device is air-gapped (no network access), which limits remote exploitation
even if the binary is compromised. A dedicated hardware device for the Signer role would be
the ideal long-term architecture.

### No zero-knowledge proofs on Paillier key validity

The Signer generates the Paillier keypair and sends the public key to the Vault. The Vault
has no proof that the Paillier modulus `n` is a product of two safe primes, or that the
keypair was generated honestly. A malicious Signer could craft a Paillier key that leaks
information during signing.

The full Lindell 2017 paper specifies a ZK proof for Paillier key correctness. This is
**omitted for simplicity** --- the same trust assumption from DKG (Signer is honest during
setup) already covers this. The ZK proof would only matter if you distrust the Signer during
setup but trust it during signing, which is not a coherent threat model for this design.

### NFC relay attacks

An attacker with two NFC-capable devices could relay APDU traffic between a victim's Signer
and the attacker's Vault, potentially tricking the Signer into signing a transaction the
user didn't intend. The ECDH session encryption authenticates the channel but doesn't bind
it to physical proximity beyond NFC range.

This is a **known limitation of NFC-based protocols**. Mitigation is at the UI layer: the
Signer displays transaction details and requires explicit user approval. The user must verify
the transaction on the Signer screen before approving --- the relay cannot forge what is
displayed.

---

## Implementation Files

| File | Purpose |
|------|---------|
| `core/crypto/Secp256k1.kt` | EC point arithmetic, address derivation, ecRecover |
| `core/crypto/PaillierCipher.kt` | Paillier keygen, encrypt, decrypt (with blinding), homomorphic ops |
| `core/crypto/LindellDkg.kt` | Key splitting: `splitFromFullKey()`, `splitAllFromSeed()` |
| `core/crypto/LindellSign.kt` | Signing: `vaultPrepare()`, `signerComputePartial()`, `vaultFinalize()` |
| `core/crypto/Bip39.kt` | BIP39 mnemonic generation/validation (via bitcoinj) |
| `core/crypto/Bip32.kt` | BIP32/BIP44 HD key derivation (via bitcoinj) |
| `core/crypto/EthTransaction.kt` | EIP-1559 transaction building, signing hash, signed encoding |
| `core/crypto/EthSigner.kt` | Transaction builder bridge, signature verification |
| `core/crypto/Rlp.kt` | RLP encoder |
| `core/crypto/Keccak256.kt` | Keccak-256 hash (via BouncyCastle) |
| `core/crypto/Hex.kt` | Hex encoding, BigInteger conversion, wei/ether |
| `core/crypto/ConstantTime.kt` | Timing-safe byte comparisons |
| `core/transport/SessionCrypto.kt` | ECDH (P-256) + HKDF-SHA256 + AES-256-GCM session encryption |
| `core/transport/MessageCodec.kt` | CBOR serialization/deserialization of transport messages |
| `core/transport/TransportMessage.kt` | Sealed class hierarchy of all transport message types |
| `core/transport/nfc/NfcReaderTransport.kt` | Vault-side NFC reader: handshake, send/receive, DKG fetch |
| `core/transport/nfc/RaccoonWalletHceService.kt` | Signer-side HCE service: APDU dispatch, response chunking |
| `core/transport/nfc/ApduChunker.kt` | Payload chunking (6-byte header + up to 244 bytes data) |
| `core/transport/qr/QrTransport.kt` | QR code multi-frame encoding/decoding (fallback transport) |
| `core/storage/SecretStore.kt` | Encrypted store (Paillier keys, key shares) via Android Keystore |
| `core/storage/PublicStore.kt` | Unencrypted store (accounts, balances, tx history) |
| `core/storage/KeystoreCipher.kt` | AES-256-GCM encryption using Android Keystore |

---

## Data Flow Diagram

```
                    SIGNER (air-gapped)              VAULT (online)
                    ==================              ==============

Setup (NFC):
    BIP39 seed --> BIP44 derive --> split
                                      |
                                      +---> x2, ckey, pk ---> [store]
                                      |
                                      +---> DkgRound2 msg -----------> [decrypt + store x1, sk, pk]
                                            (via NFC pull)
                                                            <--------- DkgFinalize(ack)

Signing (NFC):
                                          tx hash, R1 <--- build tx
                  <-----------------------------------------+
    show tx to user                           (INS_SIGN_REQUEST)
    user approves
    compute partial sig ---> R2, c_partial
                  ----------------------------------------->
                                (INS_POLL)    decrypt c_partial
                                              compute final (r, s, v)
                                              verify ecrecover
                                              broadcast --> blockchain
```

---

## References

- Lindell, Y. (2017). "Fast Secure Two-Party ECDSA Signing" --- https://eprint.iacr.org/2017/552
- Paillier, P. (1999). "Public-Key Cryptosystems Based on Composite Degree Residuosity Classes"
- BIP-32: Hierarchical Deterministic Wallets
- BIP-39: Mnemonic Code for Generating Deterministic Keys
- BIP-44: Multi-Account Hierarchy for Deterministic Wallets
- EIP-1559: Fee market change for ETH 1.0 chain
- EIP-2: Homestead Hard-fork Changes (low-S requirement)
- secp256k1: SEC 2 curve specification
