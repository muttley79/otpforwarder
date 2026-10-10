# Firebase and database setup

Everything the app needs on the backend: a Firebase project with Anonymous sign-in, a Native-mode Firestore database with this repo's security rules, and (optionally) two Cloud Functions for instant push and cleanup.

> **Never commit `app/google-services.json`.** It is listed in `.gitignore`. Each developer downloads their own.

## 1. Create or pick a Firebase project

Use an existing project or create one at <https://console.firebase.google.com>. A project can host several apps. The collections here are all prefixed `otp_`, so this app can share a project and its Firestore database with others.

## 2. Enable Anonymous sign-in

**Build, then Authentication, then Sign-in method, then Add new provider.** Choose **Anonymous**, switch it on and save. The app uses an anonymous ID plus a display name, so Email/Password and Google stay off.

## 3. Create the Firestore database (Native mode)

**Build, then Firestore Database, then Create database.** Choose **Native mode** (not Datastore mode), pick a region near your users, and start in **production mode**. You'll replace the rules in step 5.

**If you see "This project is set up to use Cloud Firestore in Datastore mode":** the project's `(default)` database was created in Datastore mode, which does not work with the Firebase app SDK or security rules.
- In the [Google Cloud console](https://console.cloud.google.com/datastore/entities), select the project and open Datastore Studio. If the database is empty, it shows a banner that you can still switch to Native mode. Click it. This is one-way.
- If the database holds data you need, don't switch. Create a second, named Native database with the CLI instead (`gcloud firestore databases create --database=otp --type=firestore-native --location=<region>`), and point the app at it with `FirebaseFirestore.getInstance(app, "otp")` and `"database": "otp"` in `firebase.json`.
- A database's type can only be changed while it is empty.

## 4. Register the Android app

**Project settings, then Your apps, then Add app, then Android.**
- Package name: **`com.otpforwarder`** (must match `applicationId` in `app/build.gradle.kts`)
- SHA-1: not needed for Anonymous sign-in or push
- Download **`google-services.json`** into `app/`

The Gradle build applies the Google Services plugin only if that file exists, so you can still build without it.

## 5. Publish the security rules

The rules in `firestore.rules` are what keep strangers out. Publish them either way:

- **Console:** Firestore, then the **Rules** tab. Replace the contents with `firestore.rules` and click **Publish**.
- **CLI:**
  ```bash
  npm i -g firebase-tools
  firebase login
  firebase use <your-project-id>        # creates the git-ignored .firebaserc
  firebase deploy --only firestore:rules
  ```

No composite indexes are needed.

## 6. Push notifications and cleanup (Cloud Functions)

Without this step everything works but recipients must tap **Check now** to see a new code. With it, a push wakes their phone.

Functions need the **Blaze (pay-as-you-go)** plan. Typical usage of this app is far inside the free quota.

```bash
cd functions
npm install
npm run build
cd ..
firebase deploy --only functions
```

| Function | Trigger | What it does |
|---|---|---|
| `notifyRecipient` | a document is created in `otp_messages` | Sends a high-priority push (data only, no code inside) to the recipient's device. The app then fetches and decrypts the ciphertext. |
| `cleanup` | every 5 minutes | Deletes messages older than 10 minutes and expired invites. |

Cloud Messaging needs no extra setup: `google-services.json` already contains the sender ID.

## 7. Data model

All documents live in the default database. Codes never appear in any of them, only ciphertext.

| Collection / document | Fields | Who can access |
|---|---|---|
| `otp_users/{uid}` | `publicKey` (device public key, base64), `fcmToken`, `displayName` | That user only |
| `otp_invites/{token}` | `ownerUid`, `ownerName`, `redeemedBy` (null until used), `recipientName`, `expiresAt` (at most 11 minutes ahead) | Anyone signed in can **get** by token but never list. Created by the owner. Redeemed once by the recipient. |
| `otp_pairings/{id}` (same id as the invite) | `ownerUid`, `ownerName`, `recipientUid`, `recipientName`, `recipientPublicKey`, `status` (`active` or `revoked`), `createdAt`, `revokedBy`, `revokedAt` | Read by the two parties. Created by the recipient together with redeeming the invite. Either party may change `status` from `active` to `revoked`, and nothing else. |
| `otp_messages/{auto}` | `pairingId`, `ownerUid`, `recipientUid`, `ciphertext`, `createdAt` | Created by the owner for an active pairing. Read and deleted by the recipient only. |
| `otp_audit/{auto}` | `pairingId`, `ownerUid`, `recipientUid`, `ownerName`, `recipientName`, `sender`, `createdAt` | Created by the owner for an active pairing. Read by both parties. Never edited or deleted. |

Things that are intentionally **not** in Firestore: routing rules, sender names to match, message text to match, time windows, per-person pause and SMS-relay numbers. They live only on the owner's phone.

## 8. How pairing works against these rules

1. Owner creates `otp_invites/{random 128-bit token}` with `redeemedBy: null`.
2. Recipient opens the link or scans the QR code, previews it, and taps **Accept**.
3. In **one batch** the recipient sets `redeemedBy` on the invite and creates `otp_pairings/{same id}`. The pairing rule checks that the invite was redeemed by that same user, so nobody can create a pairing without a redeemed invite.
4. From then on the owner may write messages for that pairing and the recipient may read them. Revoking sets `status: revoked`, and the rules stop all further writes.

## 9. Test the rules locally

```bash
cd rules-test
npm install
npx firebase emulators:exec --only firestore "node --test rules.test.mjs"
```

Needs Java and Node. The tests check that a stranger can't redeem or read, that revoked pairings can't send, that messages can only go to the paired recipient, and that the audit log is immutable.

## 10. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| App shows **Not connected** | `app/google-services.json` is missing or for a different package. Re-download it for `com.otpforwarder` and rebuild. |
| Welcome screen fails with an auth error | Anonymous sign-in isn't enabled (step 2). |
| `PERMISSION_DENIED` everywhere | Rules not published (step 5), or the database is in Datastore mode (step 3). |
| "Datastore mode" notice in the console | See step 3. |
| Codes only appear after **Check now** | The push function isn't deployed (step 6). Everything else is fine. |
| A recipient who reinstalled stops receiving | Their device key was lost with the install. Have them **Leave**, then invite them again. |
| Owner sees nothing forwarded | Check the allow rules: with no rule that matches, nothing is shared. Also check the sharing window and the per-person pause. |

## Cost

For a handful of users this stays inside Firebase's free usage: a few document writes per shared code, short-lived and deleted. Cloud Functions require the Blaze plan to deploy, but small volumes cost nothing in practice. Set a budget alert in the Google Cloud console as a safeguard.
