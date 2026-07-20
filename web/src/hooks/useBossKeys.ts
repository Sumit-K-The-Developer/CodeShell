import { useEffect } from "react";
import { useTerminalPanel } from "@/context/TerminalPanelContext";

/** Ctrl+Shift+. arms/disarms; Esc toggles panel only when armed. */
export function useBossKeys() {
  const {
    terminalArmed,
    toggleArmed,
    togglePanel,
    panelFullscreen,
    togglePanelFullscreen,
    media,
    closeMedia,
  } = useTerminalPanel();

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const isEscape = event.key === "Escape";
      const isArmShortcut =
        event.ctrlKey && event.shiftKey && (event.key === "." || event.code === "Period");

      if (isArmShortcut) {
        event.preventDefault();
        toggleArmed();
        return;
      }

      if (!isEscape) return;

      if (media) {
        event.preventDefault();
        closeMedia();
        return;
      }

      if (panelFullscreen) {
        event.preventDefault();
        togglePanelFullscreen();
        return;
      }

      if (terminalArmed) {
        event.preventDefault();
        togglePanel();
      }
    };

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [
    terminalArmed,
    toggleArmed,
    togglePanel,
    panelFullscreen,
    togglePanelFullscreen,
    media,
    closeMedia,
  ]);
}
