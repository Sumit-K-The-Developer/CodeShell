import { useAuth } from "@/context/AuthContext";
import { useTerminalPanel } from "@/context/TerminalPanelContext";
import { LoginPanel } from "@/components/terminal/LoginPanel";
import { HomePanel } from "@/components/terminal/HomePanel";
import { ChatPanel } from "@/components/terminal/ChatPanel";

export function TerminalPanel() {
  const { panelOpen, panelView, activeChatId, panelFullscreen } = useTerminalPanel();
  const { user } = useAuth();

  if (!panelOpen) return null;

  const panelClassName = [
    "terminal-panel",
    panelFullscreen && "terminal-panel--fullscreen",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <aside className={panelClassName} aria-label="Terminal panel">
      {!user ? (
        <LoginPanel />
      ) : panelView === "chat" && activeChatId ? (
        <ChatPanel key={activeChatId} chatId={activeChatId} />
      ) : (
        <HomePanel />
      )}
    </aside>
  );
}
