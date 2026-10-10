import { initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";

initializeApp();
const TTL_MS = 10 * 60 * 1000;

// Wake the recipient's device. The push carries no code: the app fetches the ciphertext and decrypts locally.
export const notifyRecipient = onDocumentCreated("otp_messages/{id}", async (event) => {
  const data = event.data?.data();
  if (!data) return;
  const user = await getFirestore().collection("otp_users").doc(data.recipientUid).get();
  const token = user.get("fcmToken");
  if (!token) return;
  await getMessaging().send({
    token,
    data: { type: "otp" },
    android: { priority: "high", ttl: 60_000 },
  });
});

// Ciphertext is short-lived: drop anything older than TTL, and expired or used invites.
export const cleanup = onSchedule("every 5 minutes", async () => {
  const db = getFirestore();
  const old = Timestamp.fromMillis(Date.now() - TTL_MS);
  for (const col of ["otp_messages"]) {
    const snap = await db.collection(col).where("createdAt", "<", old).limit(400).get();
    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
  }
  const inv = await db.collection("otp_invites").where("expiresAt", "<", Timestamp.now()).limit(400).get();
  const batch = db.batch();
  inv.docs.forEach((d) => batch.delete(d.ref));
  await batch.commit();
});
