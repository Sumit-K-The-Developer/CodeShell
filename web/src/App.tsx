import { Navigate, Route, Routes } from "react-router-dom";
import { useBossKeys } from "@/hooks/useBossKeys";
import { AppShell } from "@/pages/AppShell";

export function App() {
  useBossKeys();

  return (
    <Routes>
      <Route path="/" element={<AppShell />} />
      <Route path="/learn" element={<AppShell />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
