// Run: cd rules-test && npm i && npx firebase emulators:exec --only firestore "node --test rules.test.mjs"
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { readFileSync } from "node:fs";
import { before, after, test } from "node:test";
import { doc, getDoc, setDoc, updateDoc, serverTimestamp, writeBatch, Timestamp } from "firebase/firestore";

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: "otp-rules",
    firestore: { rules: readFileSync("../firestore.rules", "utf8") },
  });
});
after(() => env.cleanup());

const soon = () => Timestamp.fromMillis(Date.now() + 9 * 60_000);
async function pair(id = "inv1") {
  await env.clearFirestore();
  const owner = env.authenticatedContext("owner").firestore();
  const rcpt = env.authenticatedContext("rcpt").firestore();
  await assertSucceeds(setDoc(doc(owner, "otp_invites", id), { ownerUid: "owner", redeemedBy: null, expiresAt: soon() }));
  const b = writeBatch(rcpt);
  b.update(doc(rcpt, "otp_invites", id), { redeemedBy: "rcpt", recipientName: "r" });
  b.set(doc(rcpt, "otp_pairings", id), { ownerUid: "owner", recipientUid: "rcpt", status: "active" });
  await assertSucceeds(b.commit());
  return { owner, rcpt, id };
}

test("recipient can redeem an invite once; a third user cannot", async () => {
  const { id } = await pair();
  const other = env.authenticatedContext("eve").firestore();
  await assertFails(updateDoc(doc(other, "otp_invites", id), { redeemedBy: "eve", recipientName: "e" }));
});

test("a user cannot create a pairing without redeeming the invite", async () => {
  await env.clearFirestore();
  const owner = env.authenticatedContext("owner").firestore();
  const eve = env.authenticatedContext("eve").firestore();
  await setDoc(doc(owner, "otp_invites", "i2"), { ownerUid: "owner", redeemedBy: null, expiresAt: soon() });
  await assertFails(setDoc(doc(eve, "otp_pairings", "i2"), { ownerUid: "owner", recipientUid: "eve", status: "active" }));
});

test("owner sends to recipient; strangers cannot read; recipient can", async () => {
  const { owner, rcpt, id } = await pair();
  await assertSucceeds(setDoc(doc(owner, "otp_messages", "m1"), {
    pairingId: id, ownerUid: "owner", recipientUid: "rcpt", ciphertext: "x", createdAt: serverTimestamp(),
  }));
  await assertSucceeds(getDoc(doc(rcpt, "otp_messages", "m1")));
  await assertFails(getDoc(doc(env.authenticatedContext("eve").firestore(), "otp_messages", "m1")));
  await assertFails(getDoc(doc(owner, "otp_messages", "m1")));
});

test("revoked pairing blocks sending (either party can revoke)", async () => {
  const { owner, rcpt, id } = await pair();
  await assertSucceeds(updateDoc(doc(rcpt, "otp_pairings", id), { status: "revoked", revokedBy: "rcpt", revokedAt: serverTimestamp() }));
  await assertFails(setDoc(doc(owner, "otp_messages", "m2"), {
    pairingId: id, ownerUid: "owner", recipientUid: "rcpt", ciphertext: "x", createdAt: serverTimestamp(),
  }));
});

test("cannot send to a recipient other than the paired one", async () => {
  const { owner, id } = await pair();
  await assertFails(setDoc(doc(owner, "otp_messages", "m3"), {
    pairingId: id, ownerUid: "owner", recipientUid: "eve", ciphertext: "x", createdAt: serverTimestamp(),
  }));
});

test("audit log is immutable", async () => {
  const { owner, id } = await pair();
  await assertSucceeds(setDoc(doc(owner, "otp_audit", "a1"), {
    pairingId: id, ownerUid: "owner", recipientUid: "rcpt", sender: "Clinic", createdAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(doc(owner, "otp_audit", "a1"), { sender: "x" }));
});
