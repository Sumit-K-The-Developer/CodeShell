import { useEffect, useRef, useState } from "react";
import { useTerminalPanel } from "@/context/TerminalPanelContext";
import type { LearnManifest } from "@/types";
import "@/styles/learn.css";

/** Max time from first to third tap (works for both mouse and touch). */
const TRIPLE_TAP_WINDOW_MS = 600;
/** Ignore duplicate pointer + click events from one physical tap. */
const TAP_DEDUPE_MS = 80;

export function LearnContent() {
  const { terminalArmed, toggleArmed, panelOpen, closePanel, togglePanel } = useTerminalPanel();
  const [manifest, setManifest] = useState<LearnManifest | null>(null);
  const [topicId, setTopicId] = useState("python");
  const lastTopicTapTimes = useRef<number[]>([]);
  const lastTopicTabRef = useRef<HTMLButtonElement | null>(null);
  const lastHandledAt = useRef(0);
  const terminalArmedRef = useRef(terminalArmed);

  useEffect(() => {
    terminalArmedRef.current = terminalArmed;
    if (!terminalArmed) {
      lastTopicTapTimes.current = [];
    }
  }, [terminalArmed]);

  useEffect(() => {
    fetch("/lessons/default.json")
      .then((res) => res.json())
      .then((data: LearnManifest) => {
        setManifest(data);
        if (data.topics.length > 0) {
          setTopicId(data.topics[0].id);
        }
      })
      .catch(() => setManifest(null));
  }, []);

  useEffect(() => {
    if (!manifest) return;
    lastTopicTabRef.current?.scrollIntoView({ inline: "end", block: "nearest", behavior: "smooth" });
  }, [manifest]);

  const topic = manifest?.lessons[topicId];
  const lastTopicId = manifest?.topics[manifest.topics.length - 1]?.id;

  function revealTerminal() {
    lastTopicTapTimes.current = [];
    togglePanel();
  }

  function registerLastTopicTap(id: string): boolean {
    const now = Date.now();
    const times = lastTopicTapTimes.current;

    if (times.length > 0 && now - times[times.length - 1] > TRIPLE_TAP_WINDOW_MS) {
      times.length = 0;
    }

    times.push(now);
    if (times.length > 3) {
      times.shift();
    }

    if (times.length >= 3 && times[times.length - 1] - times[0] <= TRIPLE_TAP_WINDOW_MS) {
      revealTerminal();
      return true;
    }

    setTopicId(id);
    return false;
  }

  function handleTopicActivate(id: string, isLast: boolean) {
    const now = Date.now();
    if (now - lastHandledAt.current < TAP_DEDUPE_MS) {
      return;
    }
    lastHandledAt.current = now;

    if (!isLast) {
      lastTopicTapTimes.current = [];
      setTopicId(id);
      return;
    }

    if (!terminalArmedRef.current) {
      lastTopicTapTimes.current = [];
      setTopicId(id);
      return;
    }

    registerLastTopicTap(id);
  }

  return (
    <div className="learn-page">
      <header className="top-bar">
        <button
          type="button"
          className={`tutorial-title${terminalArmed ? " tutorial-title--armed" : ""}`}
          onClick={toggleArmed}
          aria-pressed={terminalArmed}
          title={terminalArmed ? "Disable terminal (Ctrl+Shift+.)" : "Enable terminal (Ctrl+Shift+.)"}
        >
          Python Code Tutorial
        </button>
      </header>

      {manifest && (
        <nav className="topic-tabs">
          {manifest.topics.map((t) => {
            const isLast = t.id === lastTopicId;
            return (
              <button
                key={t.id}
                ref={isLast ? lastTopicTabRef : undefined}
                type="button"
                className={`topic-tab${t.id === topicId ? " active" : ""}${isLast ? " topic-tab--secret" : ""}`}
                onPointerUp={(event) => {
                  if (event.pointerType === "mouse" && event.button !== 0) return;
                  handleTopicActivate(t.id, isLast);
                }}
                title={
                  isLast
                    ? terminalArmed
                      ? "Triple-tap to toggle terminal (same as Esc)"
                      : undefined
                    : undefined
                }
              >
                {t.shortLabel}
              </button>
            );
          })}
        </nav>
      )}

      <main
        className="learn-content"
        onClick={() => {
          if (panelOpen) closePanel();
        }}
      >
        {!topic && <p className="muted">Loading lessons…</p>}
        {topic?.sections.map((section) => (
          <article key={section.title} className="learn-section">
            <h2>{section.title}</h2>
            <p>{section.body}</p>
            {section.examples && <pre className="code-block">{section.examples}</pre>}
          </article>
        ))}
        {topic?.resources && topic.resources.length > 0 && (
          <div className="resources">
            <h3>Resources</h3>
            {topic.resources.map((r) => (
              <a
                key={r.url}
                href={r.url}
                target="_blank"
                rel="noreferrer"
                onClick={(event) => event.stopPropagation()}
              >
                {r.title}
              </a>
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
