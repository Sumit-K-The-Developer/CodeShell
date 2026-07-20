import { useTerminalPanel } from "@/context/TerminalPanelContext";

export function MediaOverlay() {
  const { media, closeMedia } = useTerminalPanel();

  if (!media) return null;

  const { url, type, name } = media;

  return (
    <div className="media-overlay" role="dialog" aria-modal="true" aria-label="Media viewer">
      <header className="media-overlay-bar">
        <button type="button" className="icon-btn" onClick={closeMedia} aria-label="Close">
          ←
        </button>
        <span className="title">{name || "Media"}</span>
        <span className="muted" style={{ fontSize: "0.75rem" }}>Esc to close</span>
      </header>

      <div className="media-viewer">
        {type === "image" && <img src={url} alt={name} />}
        {type === "video" && <video src={url} controls autoPlay />}
        {type === "audio" && <audio src={url} controls autoPlay />}
        {type === "file" && <p className="muted">File attachment</p>}
      </div>

      <div className="media-actions">
        <a href={url} target="_blank" rel="noreferrer" download={name || undefined}>
          Open / download
        </a>
      </div>
    </div>
  );
}
