const EMAIL_DOMAIN = "codeshell.app";
const DIGIT_USERNAME_PATTERN = /^[0-9]{4,10}$/;

export function normalizeUsername(input: string): string {
  return input.replace(/\D/g, "");
}

export function toAuthEmail(username: string): string {
  return `${normalizeUsername(username)}@${EMAIL_DOMAIN}`;
}

export type UsernameValidation =
  | { valid: true }
  | { valid: false; reason: string };

export function validateUsername(username: string): UsernameValidation {
  const digits = normalizeUsername(username);
  if (!digits) return { valid: false, reason: "CodeShell ID is required" };
  if (digits.length < 4) return { valid: false, reason: "Enter at least 4 digits" };
  if (digits.length > 10) return { valid: false, reason: "Maximum 10 digits" };
  if (!DIGIT_USERNAME_PATTERN.test(digits)) {
    return { valid: false, reason: "Digits only, 4–10 characters" };
  }
  return { valid: true };
}

export function mapAuthError(error: unknown): string {
  const message = error instanceof Error ? error.message : String(error);
  if (
    /password is invalid|no user record|invalid-credential|wrong-password/i.test(message)
  ) {
    return "Incorrect CodeShell ID or access key";
  }
  if (/requires-recent-login/i.test(message)) {
    return "Enter your current access key to continue";
  }
  if (/already in use/i.test(message)) {
    return "CodeShell ID is already taken";
  }
  if (/network/i.test(message)) {
    return "Network error. Check your connection.";
  }
  if (/weak password|Password should be at least/i.test(message)) {
    return "Access key must be at least 6 characters";
  }
  if (/API key not valid|CONFIGURATION_NOT_FOUND/i.test(message)) {
    return "Firebase is not configured. Add web app config in Firebase Console.";
  }
  return message || "Authentication failed";
}
