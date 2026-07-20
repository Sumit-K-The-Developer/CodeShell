import { useTerminalPanel } from "@/context/TerminalPanelContext";

function ExpandIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z" />
    </svg>
  );
}

function CollapseIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M5 16h3v3H5v-3zm0-8h3v3H5V8zm8 11h3v3h-3v-3zm0-8h3v3h-3v-3z" />
    </svg>
  );
}

export function PanelFullscreenToggle() {
  const { panelFullscreen, togglePanelFullscreen } = useTerminalPanel();

  const label = panelFullscreen ? "Exit fullscreen" : "Expand chat to fullscreen";

  return (
    <button
      type="button"
      className="icon-btn panel-fullscreen-toggle"
      onClick={togglePanelFullscreen}
      aria-label={label}
      aria-pressed={panelFullscreen}
      title={label}
    >
      {panelFullscreen ? <CollapseIcon /> : <ExpandIcon />}
    </button>
  );
}
