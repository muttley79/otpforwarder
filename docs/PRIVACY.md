# Privacy policy (draft)

**What the app does.** OTP Forwarder reads one-time-code SMS on the owner's phone, only after the owner has consented and switched forwarding on, and shares them with recipients the owner has invited and who accepted.

**What we store.**
- Identity: a random anonymous ID (Firebase Authentication) and the display name you choose. No email, phone number or password is collected.
- Profile: your public encryption key and push token.
- Pairings: who shares with whom, and whether active or revoked.
- Messages: end-to-end encrypted ciphertext, deleted when the recipient fetches it or after 10 minutes.
- Activity log: time, SMS sender name and recipient for each forward. It never contains the code.

**What we don't store.** Message text, codes in readable form, contacts, location, analytics about message content. Routing rules (sender and message-text matches), time windows, per-person pauses and SMS-relay phone numbers stay on the owner's device.

**Your controls.** Revoke any pairing, pause, set a time window or switch forwarding off at any time. *Account → Delete account and data* revokes your pairings, removes pending messages and your profile, and deletes your sign-in account.

**SMS relay.** If the owner enables it for a recipient, the code is also sent as an ordinary, unencrypted SMS to the number the owner entered.

**Third parties.** Google Firebase (authentication, database, push delivery).
