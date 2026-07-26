"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.purgeExpiredMessages = exports.onCallCreated = exports.onMessageCreated = exports.deleteAccount = exports.ensureBotAccount = exports.checkUsernameAvailability = void 0;
const admin = __importStar(require("firebase-admin"));
const firestore_1 = require("firebase-functions/v2/firestore");
const https_1 = require("firebase-functions/v2/https");
const scheduler_1 = require("firebase-functions/v2/scheduler");
const firebase_functions_1 = require("firebase-functions");
admin.initializeApp();
const db = admin.firestore();
const bucket = admin.storage().bucket();
const UNSEEN_HOLD_HOURS = 24;
function unseenExpiresAtFrom(timestamp) {
    if (!timestamp)
        return null;
    const ms = timestamp.toMillis() + UNSEEN_HOLD_HOURS * 60 * 60 * 1000;
    return admin.firestore.Timestamp.fromMillis(ms);
}
function otherParticipant(participants, senderId) {
    var _a;
    return (_a = participants.find((id) => id !== senderId)) !== null && _a !== void 0 ? _a : null;
}
function hasBeenSeenByBoth(participants, senderId, seenBy, read) {
    if (participants.length !== 2)
        return false;
    const recipientId = otherParticipant(participants, senderId);
    if (!recipientId)
        return false;
    const senderSeen = Boolean(seenBy && seenBy[senderId]);
    const recipientSeen = Boolean(seenBy && seenBy[recipientId]) || read === true;
    return senderSeen && recipientSeen;
}
function resolveUnseenExpiresAt(data) {
    const stored = data.unseenExpiresAt;
    if (stored)
        return stored;
    return unseenExpiresAtFrom(data.timestamp);
}
function isPast(timestamp, now) {
    return timestamp.toMillis() <= now.toMillis();
}
function isValidDigitUsername(username) {
    return /^[0-9]+$/.test(username) && username.length >= 4 && username.length <= 10;
}
const RESERVED_USERNAMES = new Set(["8787"]);
function isReservedUsername(username) {
    return RESERVED_USERNAMES.has(username);
}
async function deleteMessageMedia(chatId, messageId) {
    const prefix = `chats/${chatId}/${messageId}/`;
    try {
        const [files] = await bucket.getFiles({ prefix });
        await Promise.all(files.map((file) => file.delete().catch(() => undefined)));
    }
    catch (error) {
        firebase_functions_1.logger.warn("Failed to delete storage for message", { chatId, messageId, error });
    }
}
async function deleteChatStorage(chatId) {
    const prefix = `chats/${chatId}/`;
    try {
        const [files] = await bucket.getFiles({ prefix });
        await Promise.all(files.map((file) => file.delete().catch(() => undefined)));
    }
    catch (error) {
        firebase_functions_1.logger.warn("Failed to delete chat storage", { chatId, error });
    }
}
async function deleteChat(chatId) {
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
exports.checkUsernameAvailability = (0, https_1.onCall)(async (request) => {
    var _a, _b, _c;
    const username = (_c = (_b = (_a = request.data) === null || _a === void 0 ? void 0 : _a.username) === null || _b === void 0 ? void 0 : _b.trim()) !== null && _c !== void 0 ? _c : "";
    if (!isValidDigitUsername(username)) {
        throw new https_1.HttpsError("invalid-argument", "Invalid CodeShell ID");
    }
    if (isReservedUsername(username)) {
        return { available: false };
    }
    const usernameRef = db.collection("usernames").doc(username);
    const usernameSnap = await usernameRef.get();
    if (!usernameSnap.exists) {
        return { available: true };
    }
    const userId = usernameSnap.get("userId");
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
exports.ensureBotAccount = (0, https_1.onCall)(async (request) => {
    var _a;
    if (!((_a = request.auth) === null || _a === void 0 ? void 0 : _a.uid)) {
        throw new https_1.HttpsError("unauthenticated", "Must be signed in");
    }
    const email = `${BOT_USERNAME}@${BOT_EMAIL_DOMAIN}`;
    let userRecord;
    try {
        userRecord = await admin.auth().getUserByEmail(email);
    }
    catch (_b) {
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
exports.deleteAccount = (0, https_1.onCall)(async (request) => {
    var _a;
    const uid = (_a = request.auth) === null || _a === void 0 ? void 0 : _a.uid;
    if (!uid) {
        throw new https_1.HttpsError("unauthenticated", "Must be signed in");
    }
    try {
        const userSnap = await db.collection("users").doc(uid).get();
        const username = userSnap.get("username");
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
    }
    catch (error) {
        firebase_functions_1.logger.error("deleteAccount failed", { uid, error });
        throw new https_1.HttpsError("internal", "Could not delete account");
    }
});
async function sendDisguisedPush(recipientId, chatId, messageId) {
    const userSnap = await db.collection("users").doc(recipientId).get();
    const token = userSnap.get("fcmToken");
    if (!token) {
        firebase_functions_1.logger.debug("No FCM token for recipient", { recipientId });
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
async function sendDisguisedCallPush(recipientId, chatId, callId) {
    const userSnap = await db.collection("users").doc(recipientId).get();
    const token = userSnap.get("fcmToken");
    if (!token) {
        firebase_functions_1.logger.debug("No FCM token for call recipient", { recipientId, callId });
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
exports.onMessageCreated = (0, firestore_1.onDocumentCreated)("chats/{chatId}/messages/{messageId}", async (event) => {
    var _a;
    const chatId = event.params.chatId;
    const messageId = event.params.messageId;
    const data = (_a = event.data) === null || _a === void 0 ? void 0 : _a.data();
    if (!data)
        return;
    const senderId = data.senderId;
    if (!senderId || data.deleted === true)
        return;
    const chatSnap = await db.collection("chats").doc(chatId).get();
    const participants = chatSnap.get("participants");
    if (!participants || participants.length !== 2)
        return;
    const recipientId = otherParticipant(participants, senderId);
    if (!recipientId)
        return;
    try {
        await sendDisguisedPush(recipientId, chatId, messageId);
    }
    catch (error) {
        firebase_functions_1.logger.error("FCM send failed", { chatId, messageId, error });
    }
});
exports.onCallCreated = (0, firestore_1.onDocumentCreated)("calls/{callId}", async (event) => {
    var _a;
    const callId = event.params.callId;
    const data = (_a = event.data) === null || _a === void 0 ? void 0 : _a.data();
    if (!data)
        return;
    const state = data.state;
    const calleeId = data.calleeId;
    const chatId = data.chatId;
    if (state !== "ringing" || !calleeId || !chatId)
        return;
    try {
        await sendDisguisedCallPush(calleeId, chatId, callId);
    }
    catch (error) {
        firebase_functions_1.logger.error("Call FCM send failed", { callId, chatId, error });
    }
});
exports.purgeExpiredMessages = (0, scheduler_1.onSchedule)({
    schedule: "0 1 * * *",
    timeZone: "Asia/Kolkata",
}, async () => {
    const now = admin.firestore.Timestamp.now();
    const chatsSnap = await db.collection("chats").get();
    let deletedCount = 0;
    let resetCount = 0;
    let unseenDeletedCount = 0;
    let callsDeletedCount = 0;
    for (const chatDoc of chatsSnap.docs) {
        const chatId = chatDoc.id;
        const participants = chatDoc.get("participants");
        if (!participants || participants.length !== 2)
            continue;
        const batch = db.batch();
        let batchOps = 0;
        const seenExpiredSnap = await chatDoc.ref
            .collection("messages")
            .where("expiresAt", "<=", now)
            .get();
        for (const messageDoc of seenExpiredSnap.docs) {
            const data = messageDoc.data();
            const senderId = data.senderId;
            if (!senderId)
                continue;
            const seenBy = data.seenBy;
            const read = data.read;
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
            const senderId = data.senderId;
            if (!senderId)
                continue;
            const seenBy = data.seenBy;
            const read = data.read;
            if (hasBeenSeenByBoth(participants, senderId, seenBy, read)) {
                const expiresAt = data.expiresAt;
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
        const participants = chatDoc.get("participants");
        if (!participants || participants.length !== 2)
            continue;
        const legacyCutoff = admin.firestore.Timestamp.fromMillis(now.toMillis() - UNSEEN_HOLD_HOURS * 60 * 60 * 1000);
        const legacySnap = await chatDoc.ref
            .collection("messages")
            .where("timestamp", "<=", legacyCutoff)
            .get();
        if (legacySnap.empty)
            continue;
        const batch = db.batch();
        let batchOps = 0;
        for (const messageDoc of legacySnap.docs) {
            const data = messageDoc.data();
            if (data.unseenExpiresAt)
                continue;
            const senderId = data.senderId;
            if (!senderId)
                continue;
            const seenBy = data.seenBy;
            const read = data.read;
            if (hasBeenSeenByBoth(participants, senderId, seenBy, read))
                continue;
            const unseenDeadline = resolveUnseenExpiresAt(data);
            if (!unseenDeadline || !isPast(unseenDeadline, now))
                continue;
            batch.delete(messageDoc.ref);
            batchOps += 1;
            unseenDeletedCount += 1;
            await deleteMessageMedia(chatId, messageDoc.id);
        }
        if (batchOps > 0) {
            await batch.commit();
        }
    }
    const callCutoff = admin.firestore.Timestamp.fromMillis(now.toMillis() - UNSEEN_HOLD_HOURS * 60 * 60 * 1000);
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
    firebase_functions_1.logger.info("Purged expired messages", {
        deletedCount,
        resetCount,
        unseenDeletedCount,
        callsDeletedCount,
    });
});
//# sourceMappingURL=index.js.map