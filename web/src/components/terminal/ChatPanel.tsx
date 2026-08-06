import { FormEvent, useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { useAuth } from "@/context/AuthContext";
import { useTerminalPanel } from "@/context/TerminalPanelContext";
import { usePanelInactivityClose } from "@/hooks/usePanelInactivityClose";
import { getParticipantIds } from "@/lib/chatRepository";
import {
  deleteMessageForEveryone,
  deleteMessageForMe,
  editTextMessage,
  getUsername,
  markSeenBy,
  observeMessages,
  observeTyping,
  resetUnreadCount,
  sendTextMessage,
  setTyping,
} from "@/lib/messagesRepository";
import { formatChatTimestamp } from "@/lib/format";
import { isBelowPolicyWindow } from "@/lib/messageExpiry";
import { displayText, mediaUrl, type ChatMessage, type MessageReply } from "@/types";
import { PanelFullscreenToggle } from "@/components/terminal/PanelFullscreenToggle";

interface ChatPanelProps {
  chatId: string;
}

export function ChatPanel({ chatId }: ChatPanelProps) {
  const { user } = useAuth();
  const { closePanel, openHome, openMedia, media, closeOnInactivity } = useTerminalPanel();
  const userId = user?.uid ?? "";

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [otherUserId, setOtherUserId] = useState<string | null>(null);
  const [otherUsername, setOtherUsername] = useState<string>("");
  const [inputText, setInputText] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [isTyping, setIsTyping] = useState(false);
  const [tagTarget, setTagTarget] = useState<ChatMessage | null>(null);
  const [editingMessageId, setEditingMessageId] = useState<string | null>(null);
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const scrollAnchorRef = useRef<HTMLDivElement>(null);
  const needsInitialScroll = useRef(true);
  const lastMessageCount = useRef(0);
  const typingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const markedSeen = useRef(new Set<string>());
  const markedExpiryBackfill = useRef(new Set<string>());

  const handleInactivityTimeout = useCallback(() => {
    if (userId && chatId) {
      setTyping(userId, chatId, false).catch(() => {});
    }
    closePanel();
  }, [userId, chatId, closePanel]);

  const panelRef = usePanelInactivityClose(handleInactivityTimeout, {
    enabled: closeOnInactivity && !media && !isSending,
    resetKeys: [chatId, inputText, closeOnInactivity],
  });

  useEffect(() => {
    if (!chatId || !userId) return;
    markedSeen.current.clear();
    markedExpiryBackfill.current.clear();
    needsInitialScroll.current = true;
    lastMessageCount.current = 0;
    getParticipantIds(chatId).then((participants) => {
      const other = participants.find((id) => id !== userId) ?? null;
      setOtherUserId(other);
      if (other) {
        getUsername(other).then((name) => setOtherUsername(name ?? other.slice(0, 8)));
      }
    });
    resetUnreadCount(chatId, userId).catch(() => {});
  }, [chatId, userId]);

  useEffect(() => {
    if (!chatId) return;
    return observeMessages(
      chatId,
      userId,
      (list) => setMessages(list),
      () => setMessages([])
    );
  }, [chatId]);

  useEffect(() => {
    if (!otherUserId || !chatId) return;
    return observeTyping(otherUserId, chatId, setIsTyping);
  }, [otherUserId, chatId]);

  useLayoutEffect(() => {
    const container = messagesContainerRef.current;
    if (!container) return;
    if (messages.length === 0 && !isTyping) return;

    if (needsInitialScroll.current && messages.length > 0) {
      container.scrollTop = container.scrollHeight;
      needsInitialScroll.current = false;
      lastMessageCount.current = messages.length;
      return;
    }

    const grew = messages.length > lastMessageCount.current;
    lastMessageCount.current = messages.length;
    if (!grew && !isTyping) return;

    const nearBottom =
      container.scrollHeight - container.scrollTop - container.clientHeight < 80;
    if (!nearBottom && !isTyping) return;

    scrollAnchorRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isTyping]);

  useEffect(() => {
    if (!chatId || !userId) return;

    messages.forEach((msg) => {
      if (msg.senderId === userId) {
        const needsOwnExpiryBackfill =
          msg.read &&
          !markedExpiryBackfill.current.has(msg.id) &&
          (!msg.expiresAt ||
            isBelowPolicyWindow(msg.expiresAt, msg.timestamp));
        if (needsOwnExpiryBackfill) {
          markedExpiryBackfill.current.add(msg.id);
          markSeenBy(chatId, msg.id, userId).catch(() =>
            markedExpiryBackfill.current.delete(msg.id)
          );
        }
        return;
      }

      const needsSeen = !msg.seenBy.has(userId) && !markedSeen.current.has(msg.id);
      const needsExpiryBackfill =
        msg.read &&
        !markedExpiryBackfill.current.has(msg.id) &&
        (!msg.expiresAt ||
          isBelowPolicyWindow(msg.expiresAt, msg.timestamp));
      if (!needsSeen && !needsExpiryBackfill) return;

      if (needsSeen) markedSeen.current.add(msg.id);
      if (needsExpiryBackfill) markedExpiryBackfill.current.add(msg.id);

      markSeenBy(chatId, msg.id, userId).catch(() => {
        if (needsSeen) markedSeen.current.delete(msg.id);
        if (needsExpiryBackfill) markedExpiryBackfill.current.delete(msg.id);
      });
    });
  }, [messages, chatId, userId]);

  function onInputChange(text: string) {
    setInputText(text);
    setError(null);
    if (!userId || !chatId) return;

    setTyping(userId, chatId, text.trim().length > 0).catch(() => {});

    if (typingTimer.current) clearTimeout(typingTimer.current);
    if (text.trim()) {
      typingTimer.current = setTimeout(() => {
        setTyping(userId, chatId, false).catch(() => {});
      }, 2000);
    }
  }

  async function handleSend(event?: FormEvent) {
    event?.preventDefault();
    if (!userId || !chatId || !inputText.trim() || isSending) return;
    if (!editingMessageId && !otherUserId) return;

    setIsSending(true);
    setError(null);
    try {
      if (editingMessageId) {
        await editTextMessage(chatId, editingMessageId, inputText);
      } else {
        const reply: MessageReply | undefined = tagTarget
          ? {
              messageId: tagTarget.id,
              senderId: tagTarget.senderId,
              type: tagTarget.type,
              preview: displayText(tagTarget),
            }
          : undefined;
        await sendTextMessage(chatId, userId, otherUserId!, inputText, reply);
      }
      setInputText("");
      setTagTarget(null);
      setEditingMessageId(null);
      await setTyping(userId, chatId, false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Send failed");
    } finally {
      setIsSending(false);
    }
  }

  function startTagging(message: ChatMessage) {
    if (message.deleted) return;
    setEditingMessageId(null);
    setTagTarget(message);
    setOpenMenuId(null);
    window.setTimeout(() => inputRef.current?.focus(), 0);
  }

  function startEditing(message: ChatMessage) {
    if (!canEditMessage(message, userId)) return;
    setTagTarget(null);
    setEditingMessageId(message.id);
    setInputText(message.content);
    setOpenMenuId(null);
    window.setTimeout(() => {
      inputRef.current?.focus();
      inputRef.current?.select();
    }, 0);
  }

  async function removeForEveryone(message: ChatMessage) {
    if (message.senderId !== userId || message.deleted) return;
    if (!window.confirm("Delete this message for everyone?")) return;
    setOpenMenuId(null);
    try {
      await deleteMessageForEveryone(chatId, message.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete message");
    }
  }

  async function removeForMe(message: ChatMessage) {
    if (!userId) return;
    setOpenMenuId(null);
    try {
      await deleteMessageForMe(chatId, message.id, userId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete message");
    }
  }

  function handleOpenMedia(msg: ChatMessage) {
    const url = mediaUrl(msg);
    if (!url) return;
    openMedia({
      url,
      type: msg.type,
      name: msg.fileName ?? "",
    });
  }

  function receipt(msg: ChatMessage): string {
    if (msg.senderId !== userId || msg.deleted) return "";
    if (msg.read) return " ✓✓";
    return " ✓";
  }

  return (
    <div ref={panelRef} className="chat-page panel-body">
      <header className="chat-top-bar">
        <button type="button" className="icon-btn" onClick={openHome} aria-label="Back">
          ←
        </button>
        <div className="chat-top-bar-leading">
          <span className="title">{otherUsername || "Session"}</span>
        </div>
        <div className="chat-top-bar-actions">
          <PanelFullscreenToggle />
        </div>
      </header>

      <div ref={messagesContainerRef} className="chat-messages panel-scroll">
        {messages.map((msg) => {
          const mine = msg.senderId === userId;
          const url = mediaUrl(msg);

          return (
            <div key={msg.id} className={`message-row${mine ? " mine" : ""}`}>
              <div className={`bubble${mine ? " mine" : " other"}`}>
                {msg.reply && (
                  <div className="muted" style={{ fontSize: "0.8rem", marginBottom: 4 }}>
                    ↳ {msg.reply.preview}
                  </div>
                )}
                {msg.type === "text" || msg.deleted ? (
                  <span>{displayText(msg)}</span>
                ) : msg.type === "image" && url ? (
                  <div className="bubble-media" onClick={() => handleOpenMedia(msg)} role="presentation">
                    <img src={url} alt="Image" loading="lazy" />
                  </div>
                ) : msg.type === "video" && url ? (
                  <div className="bubble-media" onClick={() => handleOpenMedia(msg)} role="presentation">
                    <video src={url} preload="metadata" />
                    <span className="media-label">Tap to play video</span>
                  </div>
                ) : msg.type === "audio" && url ? (
                  <span className="media-label" onClick={() => handleOpenMedia(msg)} role="presentation">
                    🔊 Voice message — tap to play
                  </span>
                ) : msg.type === "file" && url ? (
                  <span className="media-label" onClick={() => handleOpenMedia(msg)} role="presentation">
                    📎 {msg.fileName ?? "File"} — tap to open
                  </span>
                ) : (
                  <span>{displayText(msg)}</span>
                )}
                <MessageActions
                  message={msg}
                  userId={userId}
                  open={openMenuId === msg.id}
                  onToggle={() => setOpenMenuId((current) => current === msg.id ? null : msg.id)}
                  onTag={() => startTagging(msg)}
                  onEdit={() => startEditing(msg)}
                  onDeleteForEveryone={() => void removeForEveryone(msg)}
                  onDeleteForMe={() => void removeForMe(msg)}
                />
                <div className="bubble-meta">
                  {formatChatTimestamp(msg.timestamp)}
                  {msg.editedAt && <span className="message-edited"> · edited</span>}
                  {receipt(msg)}
                </div>
              </div>
            </div>
          );
        })}
        {isTyping && <div className="typing-indicator">typing…</div>}
        <div ref={scrollAnchorRef} />
      </div>

      {error && <p className="error-text" style={{ padding: "0 16px" }}>{error}</p>}

      {(tagTarget || editingMessageId) && (
        <div className="message-compose-context">
          <span>
            {editingMessageId
              ? "Editing message"
              : `Tagging: ${displayText(tagTarget!)}`}
          </span>
          <button
            type="button"
            className="icon-btn"
            onClick={() => {
              setTagTarget(null);
              setEditingMessageId(null);
              setInputText("");
            }}
            aria-label="Cancel"
          >
            ×
          </button>
        </div>
      )}
      <form className="chat-input-bar" onSubmit={handleSend}>
        <textarea
          ref={inputRef}
          rows={1}
          placeholder="Type output…"
          value={inputText}
          onChange={(e) => onInputChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              handleSend();
            }
          }}
        />
        <button className="send-btn" type="submit" disabled={isSending || !inputText.trim()}>
          {editingMessageId ? "Save" : "Send"}
        </button>
      </form>
    </div>
  );
}

const EDIT_WINDOW_MS = 15 * 60 * 1000;

function canEditMessage(message: ChatMessage, userId: string): boolean {
  if (message.senderId !== userId || message.type !== "text" || message.deleted) return false;
  if (!message.timestamp) return false;
  const elapsed = Date.now() - message.timestamp.getTime();
  return elapsed >= 0 && elapsed <= EDIT_WINDOW_MS;
}

interface MessageActionsProps {
  message: ChatMessage;
  userId: string;
  open: boolean;
  onToggle: () => void;
  onTag: () => void;
  onEdit: () => void;
  onDeleteForEveryone: () => void;
  onDeleteForMe: () => void;
}

function MessageActions({
  message,
  userId,
  open,
  onToggle,
  onTag,
  onEdit,
  onDeleteForEveryone,
  onDeleteForMe,
}: MessageActionsProps) {
  const mine = message.senderId === userId;
  const canEdit = canEditMessage(message, userId);
  const canDeleteForEveryone = mine && !message.deleted;

  return (
    <div className={`message-actions${open ? " is-open" : ""}`}>
      <button
        type="button"
        className="message-actions-trigger"
        onClick={(event) => {
          event.stopPropagation();
          onToggle();
        }}
        aria-label="Message actions"
        aria-haspopup="menu"
        aria-expanded={open}
      >
        ⋮
      </button>
      <div className="message-menu" role="menu">
        {!message.deleted && (
          <button type="button" role="menuitem" onClick={onTag}>Tag</button>
        )}
        {canEdit && (
          <button type="button" role="menuitem" onClick={onEdit}>Edit</button>
        )}
        {canDeleteForEveryone && (
          <button type="button" role="menuitem" onClick={onDeleteForEveryone}>
            Delete for everyone
          </button>
        )}
        <button type="button" role="menuitem" onClick={onDeleteForMe}>
          Delete for me
        </button>
      </div>
    </div>
  );
}
