import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useAuth } from "@/context/AuthContext";
import {
  isPanelInactivityCloseEnabled,
  setPanelInactivityCloseEnabled,
} from "@/lib/panelPreferences";

export type PanelView = "login" | "home" | "chat";

export interface MediaState {
  url: string;
  type: string;
  name: string;
}

interface TerminalPanelContextValue {
  panelOpen: boolean;
  panelFullscreen: boolean;
  panelView: PanelView;
  activeChatId: string | null;
  media: MediaState | null;
  terminalArmed: boolean;
  closeOnInactivity: boolean;
  setCloseOnInactivity: (enabled: boolean) => void;
  toggleArmed: () => void;
  ensureArmed: () => void;
  togglePanel: () => void;
  togglePanelFullscreen: () => void;
  closePanel: () => void;
  openHome: () => void;
  openTerminal: () => void;
  openChat: (chatId: string) => void;
  openMedia: (media: MediaState) => void;
  closeMedia: () => void;
}

const TerminalPanelContext = createContext<TerminalPanelContextValue | null>(null);

export function TerminalPanelProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [panelOpen, setPanelOpen] = useState(false);
  const [panelFullscreen, setPanelFullscreen] = useState(false);
  const [panelView, setPanelView] = useState<PanelView>("login");
  const [activeChatId, setActiveChatId] = useState<string | null>(null);
  const [media, setMedia] = useState<MediaState | null>(null);
  const [terminalArmed, setTerminalArmed] = useState(false);
  const [closeOnInactivity, setCloseOnInactivityState] = useState(isPanelInactivityCloseEnabled);

  const setCloseOnInactivity = useCallback((enabled: boolean) => {
    setCloseOnInactivityState(enabled);
    setPanelInactivityCloseEnabled(enabled);
  }, []);

  const openPanel = useCallback(() => {
    setPanelOpen(true);
    if (!user) {
      setPanelView("login");
    } else if (activeChatId) {
      setPanelView("chat");
    } else {
      setPanelView("home");
    }
  }, [user, activeChatId]);

  const closePanel = useCallback(() => {
    setPanelOpen(false);
    setPanelFullscreen(false);
    setMedia(null);
  }, []);

  const togglePanelFullscreen = useCallback(() => {
    setPanelFullscreen((fullscreen) => !fullscreen);
  }, []);

  const toggleArmed = useCallback(() => {
    setTerminalArmed((armed) => {
      if (armed) {
        closePanel();
        return false;
      }
      return true;
    });
  }, [closePanel]);

  const ensureArmed = useCallback(() => {
    setTerminalArmed(true);
  }, []);

  const togglePanel = useCallback(() => {
    if (media) {
      setMedia(null);
      return;
    }
    if (panelOpen) {
      closePanel();
    } else {
      openPanel();
    }
  }, [media, panelOpen, closePanel, openPanel]);

  const openHome = useCallback(() => {
    setPanelOpen(true);
    setActiveChatId(null);
    if (!user) {
      setPanelView("login");
    } else {
      setPanelView("home");
    }
  }, [user]);

  const openTerminal = useCallback(() => {
    setTerminalArmed(true);
    setPanelOpen(true);
    setActiveChatId(null);
    if (!user) {
      setPanelView("login");
    } else {
      setPanelView("home");
    }
  }, [user]);

  const openChat = useCallback((chatId: string) => {
    setPanelOpen(true);
    setPanelView("chat");
    setActiveChatId(chatId);
  }, []);

  const openMedia = useCallback((next: MediaState) => {
    setMedia(next);
  }, []);

  const closeMedia = useCallback(() => {
    setMedia(null);
  }, []);

  const value = useMemo(
    () => ({
      panelOpen,
      panelFullscreen,
      panelView,
      activeChatId,
      media,
      terminalArmed,
      closeOnInactivity,
      setCloseOnInactivity,
      toggleArmed,
      ensureArmed,
      togglePanel,
      togglePanelFullscreen,
      closePanel,
      openHome,
      openTerminal,
      openChat,
      openMedia,
      closeMedia,
    }),
    [
      panelOpen,
      panelFullscreen,
      panelView,
      activeChatId,
      media,
      terminalArmed,
      closeOnInactivity,
      setCloseOnInactivity,
      toggleArmed,
      ensureArmed,
      togglePanel,
      togglePanelFullscreen,
      closePanel,
      openHome,
      openTerminal,
      openChat,
      openMedia,
      closeMedia,
    ]
  );

  return (
    <TerminalPanelContext.Provider value={value}>{children}</TerminalPanelContext.Provider>
  );
}

export function useTerminalPanel(): TerminalPanelContextValue {
  const ctx = useContext(TerminalPanelContext);
  if (!ctx) {
    throw new Error("useTerminalPanel must be used within TerminalPanelProvider");
  }
  return ctx;
}
