const INACTIVITY_CLOSE_ENABLED_KEY = "codeshell_panel_inactivity_close_enabled";
const LEGACY_DISABLE_KEY = "codeshell_disable_panel_inactivity_close";

/** When true (default), panels auto-close after inactivity. */
export function isPanelInactivityCloseEnabled(): boolean {
  try {
    const stored = localStorage.getItem(INACTIVITY_CLOSE_ENABLED_KEY);
    if (stored === "1") return true;
    if (stored === "0") return false;
    const legacy = localStorage.getItem(LEGACY_DISABLE_KEY);
    if (legacy === "1") return false;
    if (legacy === "0") return true;
    return true;
  } catch {
    return true;
  }
}

export function setPanelInactivityCloseEnabled(enabled: boolean): void {
  try {
    localStorage.setItem(INACTIVITY_CLOSE_ENABLED_KEY, enabled ? "1" : "0");
    localStorage.removeItem(LEGACY_DISABLE_KEY);
  } catch {
    // ignore private browsing / storage errors
  }
}
