import {
  collection,
  doc,
  getDoc,
  onSnapshot,
  orderBy,
  query,
  runTransaction,
  Timestamp,
  where,
} from "firebase/firestore";
import { COLLECTION_CHATS, db } from "./firebase";
import type { ChatSummary } from "@/types";

const FIELD_PARTICIPANTS = "participants";
const FIELD_LAST_MESSAGE = "lastMessage";
const FIELD_LAST_MESSAGE_TIME = "lastMessageTime";

export function observeChats(
  userId: string,
  onData: (chats: ChatSummary[]) => void,
  onError: (error: Error) => void
): () => void {
  const q = query(
    collection(db, COLLECTION_CHATS),
    where(FIELD_PARTICIPANTS, "array-contains", userId),
    orderBy(FIELD_LAST_MESSAGE_TIME, "desc")
  );

  return onSnapshot(
    q,
    (snapshot) => {
      const chats = snapshot.docs
        .map((docSnap) => {
          const data = docSnap.data();
          const participants = (data[FIELD_PARTICIPANTS] as string[]) ?? [];
          const otherUserId = participants.find((id) => id !== userId);
          if (!otherUserId) return null;
          const unreadField = `unreadCount_${userId}`;
          return {
            chatId: docSnap.id,
            otherUserId,
            lastMessage: (data[FIELD_LAST_MESSAGE] as string) ?? "",
            lastMessageTime: timestampToDate(data[FIELD_LAST_MESSAGE_TIME]),
            unreadCount: Number(data[unreadField] ?? 0),
          } satisfies ChatSummary;
        })
        .filter((c): c is ChatSummary => c !== null);
      onData(chats);
    },
    (error) => onError(error)
  );
}

export async function getOrCreateChat(
  currentUserId: string,
  otherUserId: string
): Promise<string> {
  if (currentUserId === otherUserId) {
    throw new Error("Cannot open session with yourself");
  }

  const chatId = [currentUserId, otherUserId].sort().join("_");
  const docRef = doc(db, COLLECTION_CHATS, chatId);
  const participants = [currentUserId, otherUserId].sort();

  await runTransaction(db, async (transaction) => {
    const snapshot = await transaction.get(docRef);
    if (!snapshot.exists()) {
      transaction.set(docRef, {
        [FIELD_PARTICIPANTS]: participants,
        [FIELD_LAST_MESSAGE]: "",
        [FIELD_LAST_MESSAGE_TIME]: Timestamp.now(),
        [`unreadCount_${participants[0]}`]: 0,
        [`unreadCount_${participants[1]}`]: 0,
      });
    }
  });

  return chatId;
}

export async function getParticipantIds(chatId: string): Promise<string[]> {
  const snapshot = await getDoc(doc(db, COLLECTION_CHATS, chatId));
  if (!snapshot.exists()) return [];
  return (snapshot.data()[FIELD_PARTICIPANTS] as string[]) ?? [];
}

function timestampToDate(value: unknown): Date | null {
  if (value instanceof Timestamp) return value.toDate();
  return null;
}
