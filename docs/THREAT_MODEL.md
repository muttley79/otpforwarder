# Threat model

Goal: let a phone owner **knowingly** share one-time codes with people they choose. The main risk is misuse of this capability against someone who has not agreed.

| Threat | Mitigation |
|---|---|
| Someone installs the app on a victim's phone to silently intercept codes | Forwarding is off until the owner ticks the consent box and turns it on; a persistent, non-dismissable notification is shown whenever it is active; no hidden icon or stealth mode; the owner is notified of every forward. |
| A recipient is added without agreeing | Pairing needs an owner-created single-use invite (10 min) **and** an explicit Accept tap by the recipient. Firestore rules tie the pairing to the redeemed invite. |
| Forwarding everything the phone receives | With no routing rules nothing is shared. Only messages that match a rule (sender and/or text) and parse as OTPs are forwarded, and only to the people that rule names. |
| Recipient keeps access after trust is lost | Either party can revoke at any time. The owner app checks the server (not cache) for active pairings before every forward; rules refuse writes to revoked pairings. Owner can also pause per person, set a time window, or use the global kill switch. |
| Server operator or database breach reads codes | Codes are encrypted on the owner's phone to the recipient's device key (HPKE X25519 / AES-256-GCM, bound to the pairing id). The server only holds ciphertext, deleted on receipt or after 10 minutes. The audit log stores no code. |
| Stolen or guessed invite | 128-bit random token used as the document id; clients can `get` but not list; single use; expires in 10 min. |
| Code lingers on the recipient device | Memory-only inbox, 5-minute expiry; lock-screen-private notification that times out. |
| Log tampering | Audit documents are create-only; both parties can read them. The owner cannot disable logging. |
| SMS relay leaks the code | Opt-in per recipient, unencrypted by nature (warned in the UI), capped at 5 per hour per recipient, only for recipients who already accepted in-app. |
| Someone impersonates a trusted person via the display name | Names are self-chosen and unverified, so invites must be handed over in person or in a trusted channel; the recipient always has to tap Accept. |
| Provider terms forbid sharing codes | First-run notice puts responsibility on the owner and recommends provider-native delegation where it exists. |

Residual risks: a compromised owner or recipient device; the owner's coerced consent; Play Store policy (SMS permission is sideload-only for now).
