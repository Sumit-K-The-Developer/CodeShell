import * as admin from "firebase-admin";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions";
import { defineSecret } from "firebase-functions/params";

admin.initializeApp();

const db = admin.firestore();
const bucket = admin.storage().bucket();
const UNSEEN_HOLD_HOURS = 24;
const groqApiKey = defineSecret("GROQ_API_KEY");

function unseenExpiresAtFrom(
  timestamp: admin.firestore.Timestamp | undefined
): admin.firestore.Timestamp | null {
  if (!timestamp) return null;
  const ms = timestamp.toMillis() + UNSEEN_HOLD_HOURS * 60 * 60 * 1000;
  return admin.firestore.Timestamp.fromMillis(ms);
}

function otherParticipant(
  participants: string[],
  senderId: string
): string | null {
  return participants.find((id) => id !== senderId) ?? null;
}

function hasBeenSeenByBoth(
  participants: string[],
  senderId: string,
  seenBy: Record<string, unknown> | undefined,
  read: boolean | undefined
): boolean {
  if (participants.length !== 2) return false;
  const recipientId = otherParticipant(participants, senderId);
  if (!recipientId) return false;

  const senderSeen = Boolean(seenBy && seenBy[senderId]);
  const recipientSeen = Boolean(seenBy && seenBy[recipientId]) || read === true;
  return senderSeen && recipientSeen;
}

function resolveUnseenExpiresAt(data: Record<string, unknown>): admin.firestore.Timestamp | null {
  const stored = data.unseenExpiresAt as admin.firestore.Timestamp | undefined;
  if (stored) return stored;
  return unseenExpiresAtFrom(data.timestamp as admin.firestore.Timestamp | undefined);
}

function isPast(timestamp: admin.firestore.Timestamp, now: admin.firestore.Timestamp): boolean {
  return timestamp.toMillis() <= now.toMillis();
}

function isValidDigitUsername(username: string): boolean {
  return /^[0-9]+$/.test(username) && username.length >= 4 && username.length <= 10;
}

const RESERVED_USERNAMES = new Set(["8787"]);

function isReservedUsername(username: string): boolean {
  return RESERVED_USERNAMES.has(username);
}

async function deleteMessageMedia(chatId: string, messageId: string): Promise<void> {
  const prefix = `chats/${chatId}/${messageId}/`;
  try {
    const [files] = await bucket.getFiles({ prefix });
    await Promise.all(files.map((file) => file.delete().catch(() => undefined)));
  } catch (error) {
    logger.warn("Failed to delete storage for message", { chatId, messageId, error });
  }
}

async function deleteChatStorage(chatId: string): Promise<void> {
  const prefix = `chats/${chatId}/`;
  try {
    const [files] = await bucket.getFiles({ prefix });
    await Promise.all(files.map((file) => file.delete().catch(() => undefined)));
  } catch (error) {
    logger.warn("Failed to delete chat storage", { chatId, error });
  }
}

async function deleteChat(chatId: string): Promise<void> {
  const chatRef = db.collection("chats").doc(chatId);
  const messagesSnap = await chatRef.collection("messages").get();
  const batchSize = 500;
  for (let i = 0; i < messagesSnap.docs.length; i += batchSize) {
    const batch = db.batch();
    const chunk = messagesSnap.docs.slice(i, i + batchSize);
    for (const doc of chunk) {
      batch.delete(doc.ref);
    }
    await batch.commit();
  }
  await deleteChatStorage(chatId);
  await chatRef.delete();
}

export const checkUsernameAvailability = onCall(async (request) => {
  const username = (request.data?.username as string | undefined)?.trim() ?? "";
  if (!isValidDigitUsername(username)) {
    throw new HttpsError("invalid-argument", "Invalid CodeShell ID");
  }
  if (isReservedUsername(username)) {
    return { available: false };
  }

  const usernameRef = db.collection("usernames").doc(username);
  const usernameSnap = await usernameRef.get();
  if (!usernameSnap.exists) {
    return { available: true };
  }

  const userId = usernameSnap.get("userId") as string | undefined;
  if (!userId) {
    await usernameRef.delete();
    return { available: true };
  }

  const userSnap = await db.collection("users").doc(userId).get();
  if (!userSnap.exists) {
    await usernameRef.delete();
    return { available: true };
  }

  return { available: false };
});

const BOT_USERNAME = "8787";
const BOT_EMAIL_DOMAIN = "codeshell.app";
const BOT_PASSWORD = "Qwerty";

export const ensureBotAccount = onCall(async (request) => {
  if (!request.auth?.uid) {
    throw new HttpsError("unauthenticated", "Must be signed in");
  }

  const email = `${BOT_USERNAME}@${BOT_EMAIL_DOMAIN}`;
  let userRecord: admin.auth.UserRecord;
  try {
    userRecord = await admin.auth().getUserByEmail(email);
  } catch {
    userRecord = await admin.auth().createUser({
      email,
      password: BOT_PASSWORD,
      emailVerified: true,
    });
  }

  const uid = userRecord.uid;
  const userRef = db.collection("users").doc(uid);
  const usernameRef = db.collection("usernames").doc(BOT_USERNAME);

  await db.runTransaction(async (transaction) => {
    const userSnap = await transaction.get(userRef);
    if (!userSnap.exists) {
      transaction.set(userRef, {
        username: BOT_USERNAME,
        avatarUrl: null,
        fcmToken: "",
        blockedUsers: [],
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }
    const usernameSnap = await transaction.get(usernameRef);
    if (!usernameSnap.exists) {
      transaction.set(usernameRef, { userId: uid });
    }
  });

  return { userId: uid, username: BOT_USERNAME };
});

export const aiTutorChat = onCall(
  { secrets: [groqApiKey], timeoutSeconds: 90 },
  async (request) => {
    if (!request.auth?.uid) {
      throw new HttpsError("unauthenticated", "Must be signed in");
    }

    const messages = request.data?.messages;
    if (!Array.isArray(messages) || messages.length === 0 || messages.length > 40) {
      throw new HttpsError("invalid-argument", "Invalid AI conversation");
    }

    const normalizedMessages = messages.map((message: unknown) => {
      const value = message as { role?: unknown; content?: unknown };
      const role = value.role;
      const content = value.content;
      if (
        (role !== "system" && role !== "user" && role !== "assistant") ||
        typeof content !== "string" ||
        content.trim().length === 0 ||
        content.length > 20_000
      ) {
        throw new HttpsError("invalid-argument", "Invalid AI message");
      }
      return { role, content };
    });

    try {
      const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${groqApiKey.value()}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          model: "openai/gpt-oss-20b",
          temperature: 0.3,
          max_tokens: 1600,
          messages: normalizedMessages,
        }),
      });
      const body = await response.json() as {
        choices?: Array<{ message?: { content?: string } }>;
        error?: { message?: string };
      };

      if (response.status === 429) {
        throw new HttpsError("resource-exhausted", "AI usage limit reached. Try again shortly.");
      }
      if (!response.ok) {
        logger.warn("Groq AI request failed", { status: response.status, uid: request.auth.uid });
        throw new HttpsError("internal", "AI service request failed");
      }

      const content = body.choices?.[0]?.message?.content?.trim();
      if (!content) {
        throw new HttpsError("internal", "AI returned an empty response");
      }
      return { content };
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      logger.error("AI tutor function failed", { uid: request.auth.uid, error });
      throw new HttpsError("internal", "AI service is temporarily unavailable");
    }
  },
);

export const deleteAccount = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Must be signed in");
  }

  try {
    const userSnap = await db.collection("users").doc(uid).get();
    const username = userSnap.get("username") as string | undefined;

    const chatsSnap = await db
      .collection("chats")
      .where("participants", "array-contains", uid)
      .get();

    for (const chatDoc of chatsSnap.docs) {
      await deleteChat(chatDoc.id);
    }

    await db.collection("presence").doc(uid).delete().catch(() => undefined);
    await db.collection("users").doc(uid).delete().catch(() => undefined);
    if (username) {
      await db.collection("usernames").doc(username).delete().catch(() => undefined);
    }
    await admin.auth().deleteUser(uid);

    return { success: true };
  } catch (error) {
    logger.error("deleteAccount failed", { uid, error });
    throw new HttpsError("internal", "Could not delete account");
  }
});

async function sendDisguisedPush(
  recipientId: string,
  chatId: string,
  messageId: string
): Promise<void> {
  const userSnap = await db.collection("users").doc(recipientId).get();
  const token = userSnap.get("fcmToken") as string | undefined;
  if (!token) {
    logger.debug("No FCM token for recipient", { recipientId });
    return;
  }

  await admin.messaging().send({
    token,
    data: {
      chatId,
      messageId,
      type: "session_update",
    },
    android: {
      priority: "high",
    },
  });
}

async function sendDisguisedCallPush(
  recipientId: string,
  chatId: string,
  callId: string
): Promise<void> {
  const userSnap = await db.collection("users").doc(recipientId).get();
  const token = userSnap.get("fcmToken") as string | undefined;
  if (!token) {
    logger.debug("No FCM token for call recipient", { recipientId, callId });
    return;
  }

  await admin.messaging().send({
    token,
    data: {
      chatId,
      callId,
      type: "incoming_call",
    },
    android: {
      priority: "high",
    },
  });
}

export const onMessageCreated = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const chatId = event.params.chatId;
    const messageId = event.params.messageId;
    const data = event.data?.data();
    if (!data) return;

    const senderId = data.senderId as string | undefined;
    if (!senderId || data.deleted === true) return;

    const chatSnap = await db.collection("chats").doc(chatId).get();
    const participants = chatSnap.get("participants") as string[] | undefined;
    if (!participants || participants.length !== 2) return;

    const recipientId = otherParticipant(participants, senderId);
    if (!recipientId) return;

    try {
      await sendDisguisedPush(recipientId, chatId, messageId);
    } catch (error) {
      logger.error("FCM send failed", { chatId, messageId, error });
    }
  }
);

export const onCallCreated = onDocumentCreated("calls/{callId}", async (event) => {
  const callId = event.params.callId;
  const data = event.data?.data();
  if (!data) return;

  const state = data.state as string | undefined;
  const calleeId = data.calleeId as string | undefined;
  const chatId = data.chatId as string | undefined;
  if (state !== "ringing" || !calleeId || !chatId) return;

  try {
    await sendDisguisedCallPush(calleeId, chatId, callId);
  } catch (error) {
    logger.error("Call FCM send failed", { callId, chatId, error });
  }
});

export const purgeExpiredMessages = onSchedule(
  {
    schedule: "0 1 * * *",
    timeZone: "Asia/Kolkata",
  },
  async () => {
  const now = admin.firestore.Timestamp.now();
  const chatsSnap = await db.collection("chats").get();
  let deletedCount = 0;
  let resetCount = 0;
  let unseenDeletedCount = 0;
  let callsDeletedCount = 0;

  for (const chatDoc of chatsSnap.docs) {
    const chatId = chatDoc.id;
    const participants = chatDoc.get("participants") as string[] | undefined;
    if (!participants || participants.length !== 2) continue;

    const batch = db.batch();
    let batchOps = 0;

    const seenExpiredSnap = await chatDoc.ref
      .collection("messages")
      .where("expiresAt", "<=", now)
      .get();

    for (const messageDoc of seenExpiredSnap.docs) {
      const data = messageDoc.data();
      const senderId = data.senderId as string | undefined;
      if (!senderId) continue;

      const seenBy = data.seenBy as Record<string, unknown> | undefined;
      const read = data.read as boolean | undefined;
      if (!hasBeenSeenByBoth(participants, senderId, seenBy, read)) {
        batch.update(messageDoc.ref, {
          expiresAt: admin.firestore.FieldValue.delete(),
        });
        batchOps += 1;
        resetCount += 1;
        continue;
      }

      batch.delete(messageDoc.ref);
      batchOps += 1;
      deletedCount += 1;
      await deleteMessageMedia(chatId, messageDoc.id);
    }

    const unseenExpiredSnap = await chatDoc.ref
      .collection("messages")
      .where("unseenExpiresAt", "<=", now)
      .get();

    for (const messageDoc of unseenExpiredSnap.docs) {
      const data = messageDoc.data();
      const senderId = data.senderId as string | undefined;
      if (!senderId) continue;

      const seenBy = data.seenBy as Record<string, unknown> | undefined;
      const read = data.read as boolean | undefined;
      if (hasBeenSeenByBoth(participants, senderId, seenBy, read)) {
        const expiresAt = data.expiresAt as admin.firestore.Timestamp | undefined;
        if (expiresAt) {
          continue;
        }
        // Both parties saw it but expiresAt was never written (e.g. session/call logs).
        batch.delete(messageDoc.ref);
        batchOps += 1;
        unseenDeletedCount += 1;
        await deleteMessageMedia(chatId, messageDoc.id);
        continue;
      }

      batch.delete(messageDoc.ref);
      batchOps += 1;
      unseenDeletedCount += 1;
      await deleteMessageMedia(chatId, messageDoc.id);
    }

    if (batchOps > 0) {
      await batch.commit();
    }
  }

  // Legacy messages without unseenExpiresAt field: scan by timestamp age
  for (const chatDoc of chatsSnap.docs) {
    const chatId = chatDoc.id;
    const participants = chatDoc.get("participants") as string[] | undefined;
    if (!participants || participants.length !== 2) continue;

    const legacyCutoff = admin.firestore.Timestamp.fromMillis(
      now.toMillis() - UNSEEN_HOLD_HOURS * 60 * 60 * 1000
    );
    const legacySnap = await chatDoc.ref
      .collection("messages")
      .where("timestamp", "<=", legacyCutoff)
      .get();

    if (legacySnap.empty) continue;

    const batch = db.batch();
    let batchOps = 0;

    for (const messageDoc of legacySnap.docs) {
      const data = messageDoc.data();
      if (data.unseenExpiresAt) continue;

      const senderId = data.senderId as string | undefined;
      if (!senderId) continue;

      const seenBy = data.seenBy as Record<string, unknown> | undefined;
      const read = data.read as boolean | undefined;
      if (hasBeenSeenByBoth(participants, senderId, seenBy, read)) continue;

      const unseenDeadline = resolveUnseenExpiresAt(data);
      if (!unseenDeadline || !isPast(unseenDeadline, now)) continue;

      batch.delete(messageDoc.ref);
      batchOps += 1;
      unseenDeletedCount += 1;
      await deleteMessageMedia(chatId, messageDoc.id);
    }

    if (batchOps > 0) {
      await batch.commit();
    }
  }

  const callCutoff = admin.firestore.Timestamp.fromMillis(
    now.toMillis() - UNSEEN_HOLD_HOURS * 60 * 60 * 1000
  );
  const staleCallsSnap = await db
    .collection("calls")
    .where("updatedAt", "<=", callCutoff)
    .get();

  for (const callDoc of staleCallsSnap.docs) {
    const candidatesSnap = await callDoc.ref.collection("candidates").get();
    if (!candidatesSnap.empty) {
      const candidateBatch = db.batch();
      for (const candidateDoc of candidatesSnap.docs) {
        candidateBatch.delete(candidateDoc.ref);
      }
      await candidateBatch.commit();
    }
    await callDoc.ref.delete();
    callsDeletedCount += 1;
  }

  logger.info("Purged expired messages", {
    deletedCount,
    resetCount,
    unseenDeletedCount,
    callsDeletedCount,
  });
});
