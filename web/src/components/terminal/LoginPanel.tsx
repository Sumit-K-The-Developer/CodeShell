import { FormEvent, useState } from "react";
import { useAuth } from "@/context/AuthContext";
import { useTerminalPanel } from "@/context/TerminalPanelContext";
import { normalizeUsername } from "@/lib/auth";

export function LoginPanel() {
  const { signIn } = useAuth();
  const { openHome } = useTerminalPanel();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await signIn(username, password);
      openHome();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="panel-login">
      <form className="panel-login-form" onSubmit={handleSubmit}>
        <h2 className="panel-heading">Connect to terminal</h2>
        <label>
          CodeShell ID
          <input
            className="text-field"
            type="text"
            inputMode="numeric"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(normalizeUsername(e.target.value))}
            placeholder="4–10 digits"
            maxLength={10}
          />
        </label>

        <label>
          Access key
          <input
            className="text-field"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Your password"
          />
        </label>

        {error && <p className="error-text">{error}</p>}

        <button className="primary-btn" type="submit" disabled={loading}>
          {loading ? "Connecting…" : "Connect"}
        </button>
      </form>
    </div>
  );
}
