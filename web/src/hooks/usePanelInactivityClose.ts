import { useCallback, useEffect, useRef, useState } from "react";
import { CHAT_PANEL_INACTIVITY_MS, useInactivityTimeout } from "@/hooks/useInactivityTimeout";

/**
 * Tracks pointer/keyboard activity on a panel root and calls onTimeout after idle time.
 */
export function usePanelInactivityClose(
  onTimeout: () => void,
  options: {
    enabled?: boolean;
    timeoutMs?: number;
    resetKeys?: unknown[];
  } = {}
) {
  const { enabled = true, timeoutMs = CHAT_PANEL_INACTIVITY_MS, resetKeys = [] } = options;
  const panelRef = useRef<HTMLDivElement>(null);
  const [activityGeneration, setActivityGeneration] = useState(0);

  const bumpActivity = useCallback(() => {
    setActivityGeneration((n) => n + 1);
  }, []);

  useInactivityTimeout(onTimeout, {
    enabled,
    timeoutMs,
    resetKeys: [...resetKeys, activityGeneration],
  });

  useEffect(() => {
    const panel = panelRef.current;
    if (!panel) return;

    const onActivity = () => bumpActivity();
    const opts: AddEventListenerOptions = { capture: true, passive: true };
    panel.addEventListener("pointerdown", onActivity, opts);
    panel.addEventListener("keydown", onActivity, opts);
    panel.addEventListener("wheel", onActivity, opts);
    panel.addEventListener("touchstart", onActivity, opts);

    return () => {
      panel.removeEventListener("pointerdown", onActivity, opts);
      panel.removeEventListener("keydown", onActivity, opts);
      panel.removeEventListener("wheel", onActivity, opts);
      panel.removeEventListener("touchstart", onActivity, opts);
    };
  }, [bumpActivity]);

  return panelRef;
}
