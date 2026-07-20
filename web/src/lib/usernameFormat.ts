/** Display stored username as #1234 (matches Android ChatUserIdBadge). */
export function formatUserId(username: string): string {
  const digits = username.replace(/\D/g, "");
  return digits ? `#${digits}` : "";
}
