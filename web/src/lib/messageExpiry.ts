const EXPIRY_HOURS = 12;
const UNSEEN_HOLD_HOURS = 24;

export function expiresAt(from: Date = new Date()): Date {
  return new Date(from.getTime() + EXPIRY_HOURS * 60 * 60 * 1000);
}

export function unseenExpiresAt(from: Date = new Date()): Date {
  return new Date(from.getTime() + UNSEEN_HOLD_HOURS * 60 * 60 * 1000);
}

export function isExpired(at: Date | null): boolean {
  if (!at) return false;
  return at.getTime() <= Date.now();
}

export function isBelowPolicyWindow(
  expiresAtVal: Date,
  messageTimestamp: Date | null
): boolean {
  if (!messageTimestamp) return false;
  const policySpanMs = EXPIRY_HOURS * 60 * 60 * 1000;
  return expiresAtVal.getTime() - messageTimestamp.getTime() < policySpanMs;
}

export function shouldHideFromClient(
  expiresAtVal: Date | null,
  unseenExpiresAtVal: Date | null,
  read = false
): boolean {
  if (expiresAtVal) return isExpired(expiresAtVal);
  if (read) return false;
  return isExpired(unseenExpiresAtVal);
}
