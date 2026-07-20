import { useEffect } from "react";

export const CHAT_PANEL_INACTIVITY_MS = 15_000;

/**
 * Fires onTimeout after timeoutMs when enabled. Any change in resetKeys restarts the timer.
 */
export function useInactivityTimeout(
  onTimeout: () => void,
  options: {
    enabled?: boolean;
    timeoutMs?: number;
    resetKeys?: unknown[];
  } = {}
) {
  const { enabled = true, timeoutMs = CHAT_PANEL_INACTIVITY_MS, resetKeys = [] } = options;

  useEffect(() => {
    if (!enabled) return;
    const id = window.setTimeout(onTimeout, timeoutMs);
    return () => window.clearTimeout(id);
  }, [enabled, timeoutMs, onTimeout, ...resetKeys]);
}
