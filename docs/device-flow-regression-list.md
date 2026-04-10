# Device Flow Regression Checklist

Run this on two real devices before merging transport, storage, setup, signing, or biometric changes.

## Setup

1. DKG over NFC with biometric disabled on both devices.
2. DKG over NFC with biometric enabled on both devices.
3. DKG over QR with biometric disabled on both devices.
4. DKG over QR with biometric enabled on both devices.
5. During DKG over NFC, verify cancel, back, retry, and app restart from:
    - transport selected
    - waiting for tap
    - transfer in progress
    - biometric prompt
    - storing keys
6. During DKG over QR, verify cancel, back, retry, and app restart from:
    - QR display
    - QR scan partial progress
    - ACK scan
    - biometric prompt
    - storing keys

## Signing

1. Signing over NFC with biometric disabled on both devices.
2. Signing over NFC with biometric enabled on both devices.
3. Signing over QR with biometric disabled on both devices.
4. Signing over QR with biometric enabled on both devices.
5. During signing over NFC, verify cancel, back, retry, and app restart from:
    - request preparation
    - tap 1
    - waiting for signer approval
    - tap 2
    - finalizing and broadcast
6. During signing over QR, verify cancel, back, retry, and app restart from:
    - request QR display
    - signer scan
    - response scan partial progress
    - finalizing and broadcast

## Recovery

1. Reset app from Vault settings and confirm both public and secret data are gone.
2. Reset app from Signer settings and confirm both public and secret data are gone.
3. Reinstall one device after completed setup and confirm expected recovery path.
4. Corrupt public store and confirm startup recovery resets local data.
5. Corrupt secret store and confirm startup recovery resets local data.
6. Invalidate biometric/keystore material and confirm startup recovery resets local data.

## Expected Outcomes

1. No transient NFC, QR, or signing/setup session state survives cancel, back, retry, or restart incorrectly.
2. Retry keeps only the minimal state needed for the current step.
3. Start over wipes partial setup data and transport session state.
4. User-visible failures tell the tester whether to re-tap, re-scan, restart pairing, or reset local data.
