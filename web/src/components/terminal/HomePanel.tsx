import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "@/context/AuthContext";
import { useTerminalPanel } from "@/context/TerminalPanelContext";
import { usePanelInactivityClose } from "@/hooks/usePanelInactivityClose";
import { observeChats, getOrCreateChat } from "@/lib/chatRepository";
import { findUserByExactUsername, getUsername, observeUserProfile } from "@/lib/messagesRepository";
import { normalizeUsername, validateUsername } from "@/lib/auth";
import { formatListTime } from "@/lib/format";
import { formatUserId } from "@/lib/usernameFormat";
import type { ChatSummary, UserProfile } from "@/types";
import { PanelFullscreenToggle } from "@/components/terminal/PanelFullscreenToggle";
import { PanelInactivityToggle } from "@/components/terminal/PanelInactivityToggle";

export function HomePanel() {
  const { user, signOut } = useAuth();
  const { openChat, closePanel, media, closeOnInactivity, setCloseOnInactivity } =
    useTerminalPanel();
  const userId = user?.uid ?? "";

  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [chats, setChats] = useState<ChatSummary[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResult, setSearchResult] = useState<UserProfile | null>(null);
  const [searchMessage, setSearchMessage] = useState<string | null>(null);
  const [isSearching, setIsSearching] = useState(false);
  const [opening, setOpening] = useState(false);
  const [usernameMap, setUsernameMap] = useState<Record<string, string>>({});
  const fetchedUsernames = useRef(new Set<string>());

  const handleInactivityTimeout = useCallback(() => {
    closePanel();
  }, [closePanel]);

  const panelRef = usePanelInactivityClose(handleInactivityTimeout, {
    enabled: closeOnInactivity && !opening && !media,
    resetKeys: [searchQuery],
  });

  useEffect(() => {
    if (!userId) return;
    return observeUserProfile(
      userId,
      (p) => setProfile(p),
      () => setProfile(null)
    );
  }, [userId]);

  useEffect(() => {
    if (!userId) return;
    return observeChats(
      userId,
      (list) => setChats(list),
      () => setChats([])
    );
  }, [userId]);

  useEffect(() => {
    if (!userId) return;

    const validation = validateUsername(searchQuery);
    if (!searchQuery || !validation.valid) {
      setSearchResult(null);
      setSearchMessage(null);
      return;
    }

    const timer = setTimeout(async () => {
      setIsSearching(true);
      setSearchMessage(null);
      try {
        const result = await findUserByExactUsername(
          searchQuery,
          userId,
          profile?.blockedUsers ?? []
        );
        setSearchResult(result);
        if (!result) setSearchMessage("No user found with that ID");
      } catch {
        setSearchMessage("Search failed");
      } finally {
        setIsSearching(false);
      }
    }, 400);

    return () => clearTimeout(timer);
  }, [searchQuery, userId, profile?.blockedUsers]);

  useEffect(() => {
    if (!userId) return;
    chats.forEach((chat) => {
      if (fetchedUsernames.current.has(chat.otherUserId)) return;
      fetchedUsernames.current.add(chat.otherUserId);
      getUsername(chat.otherUserId).then((name) => {
        if (!name) return;
        setUsernameMap((prev) => ({ ...prev, [chat.otherUserId]: name }));
      });
    });
  }, [chats, userId]);

  async function openChatWith(otherUserId: string) {
    if (!userId || opening) return;
    setOpening(true);
    try {
      const chatId = await getOrCreateChat(userId, otherUserId);
      openChat(chatId);
    } catch (err) {
      setSearchMessage(err instanceof Error ? err.message : "Could not open session");
    } finally {
      setOpening(false);
    }
  }

  return (
    <div ref={panelRef} className="home-page panel-body">
      <header className="chat-top-bar">
        <div className="chat-top-bar-leading">
          <span className="title">Terminal sessions</span>
          {profile?.username && (
            <span className="account-badge">{formatUserId(profile.username)}</span>
          )}
        </div>
        <div className="chat-top-bar-actions">
          <PanelInactivityToggle
            enabled={closeOnInactivity}
            onChange={setCloseOnInactivity}
          />
          <PanelFullscreenToggle />
          <button
            type="button"
            className="icon-btn"
            onClick={async () => {
              await signOut();
              closePanel();
            }}
            aria-label="Sign out"
            title="Sign out"
          >
            ⏻
          </button>
        </div>
      </header>

      <div className="search-row">
        <input
          className="text-field"
          type="text"
          inputMode="numeric"
          placeholder="Search CodeShell ID…"
          value={searchQuery}
          onChange={(e) => setSearchQuery(normalizeUsername(e.target.value))}
          maxLength={10}
        />
        {isSearching && <p className="muted">Searching…</p>}
        {searchMessage && !searchResult && <p className="muted">{searchMessage}</p>}
      </div>

      {searchResult && (
        <div
          className="search-result"
          onClick={() => openChatWith(searchResult.userId)}
          onKeyDown={(e) => e.key === "Enter" && openChatWith(searchResult.userId)}
          role="button"
          tabIndex={0}
        >
          Start session with <strong>{searchResult.username}</strong>
        </div>
      )}

      {chats.length === 0 && !searchResult && (
        <p className="muted" style={{ padding: 16 }}>
          No sessions yet. Search for a CodeShell ID above.
        </p>
      )}

      <div className="panel-scroll">
        {chats.map((chat) => (
          <div
            key={chat.chatId}
            className="chat-list-item"
            onClick={() => openChat(chat.chatId)}
            onKeyDown={(e) => e.key === "Enter" && openChat(chat.chatId)}
            role="button"
            tabIndex={0}
          >
            <div className="avatar">{(usernameMap[chat.otherUserId] ?? "?").slice(0, 2)}</div>
            <div className="info">
              <div className="name">
                {usernameMap[chat.otherUserId] ?? chat.otherUserId.slice(0, 8)}
              </div>
              <div className="preview">{chat.lastMessage || "No output yet"}</div>
            </div>
            <div className="chat-list-trailing">
              {chat.unreadCount > 0 && (
                <span className="unread-dot" aria-label="New messages" title="New messages" />
              )}
              <div className="time">{formatListTime(chat.lastMessageTime)}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
