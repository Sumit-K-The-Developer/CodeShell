import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { AuthProvider } from "@/context/AuthContext";
import { TerminalPanelProvider } from "@/context/TerminalPanelContext";
import { App } from "@/App";
import "@/styles/global.css";
import "@/styles/chat.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <TerminalPanelProvider>
          <App />
        </TerminalPanelProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>
);
