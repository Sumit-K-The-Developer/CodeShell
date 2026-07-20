import { LearnContent } from "@/components/LearnContent";
import { TerminalPanel } from "@/components/TerminalPanel";
import { MediaOverlay } from "@/components/MediaOverlay";
import { useTerminalPanel } from "@/context/TerminalPanelContext";

export function AppShell() {
  const { panelOpen, panelFullscreen, terminalArmed } = useTerminalPanel();

  const hotkeyHint = terminalArmed
    ? "Esc to toggle terminal · Ctrl+Shift+. to disable"
    : "Ctrl+Shift+. to enable terminal";

  const splitClassName = [
    "app-split",
    panelOpen && "app-split--open",
    panelFullscreen && "app-split--fullscreen",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div className={splitClassName}>
      <div className="learn-main">
        <LearnContent />
      </div>
      <TerminalPanel />
      <MediaOverlay />
      {!panelOpen && <div className="hotkey-hint">{hotkeyHint}</div>}
    </div>
  );
}
