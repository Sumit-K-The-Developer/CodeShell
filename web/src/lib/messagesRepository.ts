import {
  collection,
  doc,
  getDoc,
  getDocs,
  increment,
  limit,
  onSnapshot,
  orderBy,
  query,
  runTransaction,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
  writeBatch,
  type DocumentData,
} from "firebase/firestore";
import { COLLECTION_CHATS, COLLECTION_MESSAGES, COLLECTION_USERS, db } from "./firebase";
import { getParticipantIds } from "./chatRepository";
import {
  messageTypeFrom,
  type ChatMessage,
  type MessageReply,
  type UserProfile,
} from "@/types";
import { expiresAt, unseenExpiresAt, shouldHideFromClient, isExpired, isBelowPolicyWindow } from "./messageExpiry";

const FIELD_USERNAME = "username";
const FIELD_BLOCKED_USERS = "blockedUsers";

export function observeUserProfile(
  userId: string,
  onData: (profile: UserProfile | null) => void,
  onError: (error: Error) => void
): () => void {
  return onSnapshot(
    doc(db, COLLECTION_USERS, userId),
    (snapshot) => {
      if (!snapshot.exists()) {
        onData(null);
        return;
      }
      const data = snapshot.data();
      onData({
        userId: snapshot.id,
        username: (data[FIELD_USERNAME] as string) ?? "",
        blockedUsers: (data[FIELD_BLOCKED_USERS] as string[]) ?? [],
      });
    },
    (error) => onError(error)
  );
}

export async function getUsername(userId: string): Promise<string | null> {
  const docSnap = await getDoc(doc(db, COLLECTION_USERS, userId));
  if (!docSnap.exists()) return null;
  return docSnap.data()[FIELD_USERNAME] as string;
}

export async function findUserByExactUsername(
  normalizedUsername: string,
  currentUserId: string,
  blockedUserIds: string[]
): Promise<UserProfile | null> {
  if (!normalizedUsername) return null;

  const snapshot = await getDocs(
    query(
      collection(db, COLLECTION_USERS),
      where(FIELD_USERNAME, "==", normalizedUsername),
      limit(1)
    )
  );

  if (snapshot.empty) return null;

  const docSnap = snapshot.docs[0];
  const data = docSnap.data();
  const profile: UserProfile = {
    userId: docSnap.id,
    username: (data[FIELD_USERNAME] as string) ?? "",
    blockedUsers: (data[FIELD_BLOCKED_USERS] as string[]) ?? [],
  };

  if (profile.userId === currentUserId) return null;
  if (blockedUserIds.includes(profile.userId)) return null;
  return profile;
}

function parseSeenBy(raw: unknown): Set<string> {
  if (!raw || typeof raw !== "object") return new Set();
  return new Set(Object.keys(raw as Record<string, unknown>));
}

function parseUserIds(raw: unknown): Set<string> {
  if (!raw || typeof raw !== "object") return new Set();
  return new Set(Object.keys(raw as Record<string, unknown>));
}

function parseReply(data: DocumentData): MessageReply | undefined {
  const messageId = data.replyMessageId as string | undefined;
  if (!messageId) return undefined;
  return {
    messageId,
    senderId: (data.replySenderId as string) ?? "",
    type: messageTypeFrom(data.replyType as string),
    preview: (data.replyPreview as string) ?? "",
  };
}

function toChatMessage(id: string, data: DocumentData): ChatMessage {
  const timestamp = (data.timestamp as Timestamp | undefined)?.toDate() ?? null;
  const expiresAt = (data.expiresAt as Timestamp | undefined)?.toDate() ?? null;
  const editedAt = (data.editedAt as Timestamp | undefined)?.toDate() ?? null;
  const unseen =
    (data.unseenExpiresAt as Timestamp | undefined)?.toDate() ??
    (timestamp ? unseenExpiresAt(timestamp) : null);

  return {
    id,
    senderId: (data.senderId as string) ?? "",
    type: messageTypeFrom(data.type as string),
    content: (data.content as string) ?? "",
    fileName: data.fileName as string | undefined,
    fileSize: data.fileSize as number | undefined,
    timestamp,
    delivered: Boolean(data.delivered),
    read: Boolean(data.read),
    deleted: Boolean(data.deleted),
    editedAt,
    seenBy: parseSeenBy(data.seenBy),
    hiddenBy: parseUserIds(data.hiddenBy),
    expiresAt,
    unseenExpiresAt: unseen,
    reply: parseReply(data),
  };
}

export function observeMessages(
  chatId: string,
  currentUserId: string,
  onData: (messages: ChatMessage[]) => void,
  onError: (error: Error) => void
): () => void {
  const q = query(
    collection(db, COLLECTION_CHATS, chatId, COLLECTION_MESSAGES),
    orderBy("timestamp", "asc")
  );

  return onSnapshot(
    q,
    (snapshot) => {
      const messages = snapshot.docs
        .map((docSnap) => toChatMessage(docSnap.id, docSnap.data()))
        .filter((msg) => !msg.hiddenBy.has(currentUserId))
        .filter((msg) => !shouldHideFromClient(msg.expiresAt, msg.unseenExpiresAt, msg.read));
      onData(messages);
    },
    (error) => onError(error)
  );
}

export async function sendTextMessage(
  chatId: string,
  senderId: string,
  recipientId: string,
  text: string,
  reply?: MessageReply
): Promise<void> {
  const trimmed = text.trim();
  if (!trimmed) throw new Error("Empty output");

  const messageRef = doc(collection(db, COLLECTION_CHATS, chatId, COLLECTION_MESSAGES));
  const sentAt = Timestamp.now();
  const payload = {
    senderId,
    type: "text",
    content: trimmed,
    timestamp: sentAt,
    unseenExpiresAt: Timestamp.fromDate(unseenExpiresAt(sentAt.toDate())),
    seenBy: { [senderId]: sentAt },
    delivered: false,
    read: false,
    deleted: false,
    ...(reply
      ? {
          replyMessageId: reply.messageId,
          replySenderId: reply.senderId,
          replyType: reply.type,
          replyPreview: reply.preview,
        }
      : {}),
  };

  const batch = writeBatch(db);
  batch.set(messageRef, payload);
  batch.update(doc(db, COLLECTION_CHATS, chatId), {
    lastMessage: trimmed,
    lastMessageTime: sentAt,
    [`unreadCount_${recipientId}`]: increment(1),
  });
  await batch.commit();
}

export async function editTextMessage(
  chatId: string,
  messageId: string,
  text: string
): Promise<void> {
  const trimmed = text.trim();
  if (!trimmed) throw new Error("Empty output");

  await updateDoc(doc(db, COLLECTION_CHATS, chatId, COLLECTION_MESSAGES, messageId), {
    content: trimmed,
    editedAt: Timestamp.now(),
  });
}

export async function deleteMessageForEveryone(
  chatId: string,
  messageId: string
): Promise<void> {
  await updateDoc(doc(db, COLLECTION_CHATS, chatId, COLLECTION_MESSAGES, messageId), {
    deleted: true,
    content: "",
  });
}

export async function deleteMessageForMe(
  chatId: string,
  messageId: string,
  userId: string
): Promise<void> {
  await updateDoc(doc(db, COLLECTION_CHATS, chatId, COLLECTION_MESSAGES, messageId), {
    [`hiddenBy.${userId}`]: Timestamp.now(),
  });
}

export async function resetUnreadCount(chatId: string, userId: string): Promise<void> {
  await updateDoc(doc(db, COLLECTION_CHATS, chatId), {
    [`unreadCount_${userId}`]: 0,
  });
}

export async function markDelivered(chatId: string, messageId: string): Promise<void> {
  await updateDoc(doc(db, COLLECTION_CHATS, chatId, COLLECTION_MESSAGES, messageId), {
    delivered: true,
  });
}

export async function markRead(chatId: string, messageId: string): Promise<void> {
  await updateDoc(doc(db, COLLECTION_CHATS, chatId, COLLECTION_MESSAGES, messageId), {
    delivered: true,
    read: true,
  });
}

export async function markSeenBy(
  chatId: string,
  messageId: string,
  userId: string
): Promise<void> {
  const participants = await getParticipantIds(chatId);
  if (participants.length < 2) return;

  const messageRef = doc(db, COLLECTION_CHATS, chatId, COLLECTION_MESSAGES, messageId);

  await runTransaction(db, async (transaction) => {
    const snapshot = await transaction.get(messageRef);
    if (!snapshot.exists()) return;

    const data = snapshot.data();
    const senderId = (data.senderId as string) ?? "";
    const seenBy = parseSeenBy(data.seenBy);
    const mergedSeen = seenBy.has(userId) ? seenBy : new Set([...seenBy, userId]);
    const recipientId = participants.find((id) => id !== senderId);
    const recipientWillRead = userId !== senderId && !seenBy.has(userId);
    const recipientSeen =
      (recipientId && mergedSeen.has(recipientId)) ||
      Boolean(data.read) ||
      recipientWillRead;
    const senderSeen = mergedSeen.has(senderId);
    const bothSeen = senderSeen && recipientSeen;
    const currentExpiresAt = data.expiresAt as Timestamp | undefined;
    const messageTimestamp = data.timestamp as Timestamp | undefined;
    const completingBothSeen = bothSeen && !seenBy.has(userId);
    const needsExpiryBackfill =
      bothSeen &&
      seenBy.has(userId) &&
      (!currentExpiresAt ||
        isExpired(currentExpiresAt.toDate()) ||
        isBelowPolicyWindow(
          currentExpiresAt.toDate(),
          messageTimestamp?.toDate() ?? null
        ));

    const updates: Record<string, unknown> = {};
    if (!seenBy.has(userId)) {
      updates[`seenBy.${userId}`] = Timestamp.now();
      if (userId !== senderId) {
        updates.delivered = true;
        updates.read = true;
      }
    }
    if (completingBothSeen || needsExpiryBackfill) {
      updates.expiresAt = Timestamp.fromDate(expiresAt());
    }
    if (Object.keys(updates).length > 0) {
      transaction.update(messageRef, updates);
    }
  });
}

export async function setTyping(
  userId: string,
  chatId: string,
  isTyping: boolean
): Promise<void> {
  await setDoc(
    doc(db, "presence", userId),
    {
      typingInChat: isTyping ? chatId : null,
      updatedAt: serverTimestamp(),
    },
    { merge: true }
  );
}

export function observeTyping(
  otherUserId: string,
  chatId: string,
  onTyping: (typing: boolean) => void
): () => void {
  return onSnapshot(doc(db, "presence", otherUserId), (snapshot) => {
    if (!snapshot.exists()) {
      onTyping(false);
      return;
    }
    const typingInChat = snapshot.data().typingInChat as string | null;
    onTyping(typingInChat === chatId);
  });
}
