interface PanelInactivityToggleProps {
  enabled: boolean;
  onChange: (enabled: boolean) => void;
}

/** When enabled, the panel auto-closes after inactivity. */
export function PanelInactivityToggle({ enabled, onChange }: PanelInactivityToggleProps) {
  const label = enabled
    ? "Disable auto-close on inactivity"
    : "Enable auto-close on inactivity";

  return (
    <button
      type="button"
      className={`icon-btn panel-inactivity-toggle${enabled ? "" : " is-active"}`}
      onClick={() => onChange(!enabled)}
      aria-label={label}
      aria-pressed={!enabled}
      title={label}
    >
      {enabled ? (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67V7z" />
        </svg>
      ) : (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M16 12V4h1l-1-2H8L7 4h1v8l-2 2v2h5.2v6l1.8.8 1.8-.8v-6H18v-2l-2-2z" />
        </svg>
      )}
    </button>
  );
}
