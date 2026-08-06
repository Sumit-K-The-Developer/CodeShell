export type MessageType = "text" | "image" | "video" | "audio" | "file";

export interface MessageReply {
  messageId: string;
  senderId: string;
  type: MessageType;
  preview: string;
}

export interface ChatMessage {
  id: string;
  senderId: string;
  type: MessageType;
  content: string;
  fileName?: string;
  fileSize?: number;
  timestamp: Date | null;
  delivered: boolean;
  read: boolean;
  deleted: boolean;
  editedAt: Date | null;
  seenBy: Set<string>;
  hiddenBy: Set<string>;
  expiresAt: Date | null;
  unseenExpiresAt: Date | null;
  reply?: MessageReply;
}

export interface ChatSummary {
  chatId: string;
  otherUserId: string;
  otherUsername?: string;
  lastMessage: string;
  lastMessageTime: Date | null;
  unreadCount: number;
}

export interface UserProfile {
  userId: string;
  username: string;
  blockedUsers: string[];
}

export interface LearnSection {
  title: string;
  body: string;
  examples?: string;
}

export interface LearnResource {
  title: string;
  url: string;
}

export interface LearnTopic {
  sections: LearnSection[];
  resources?: LearnResource[];
}

export interface LearnManifest {
  version: number;
  topics: { id: string; label: string; shortLabel: string }[];
  lessons: Record<string, LearnTopic>;
}

export const DELETED_PLACEHOLDER = "This line was removed";

export function messageTypeFrom(value: string | undefined): MessageType {
  const normalized = (value ?? "text").toLowerCase();
  if (["text", "image", "video", "audio", "file"].includes(normalized)) {
    return normalized as MessageType;
  }
  return "text";
}

export function displayText(msg: ChatMessage): string {
  if (msg.deleted) return DELETED_PLACEHOLDER;
  switch (msg.type) {
    case "text":
      return msg.content;
    case "file":
      return msg.fileName?.trim() || "Attachment";
    case "audio":
      return "Voice output";
    case "image":
      return "Image output";
    case "video":
      return "Video output";
    default:
      return msg.content;
  }
}

export function mediaUrl(msg: ChatMessage): string | null {
  if (msg.deleted || msg.type === "text") return null;
  return msg.content.trim() || null;
}
