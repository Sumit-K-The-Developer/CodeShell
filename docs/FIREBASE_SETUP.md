# Firebase Setup Guide — CodeShell

You can add Firebase **at any time** after core app development. The project ships with a placeholder `google-services.json` so it compiles offline. Auth, Firestore, and sign-up **require** the real file before they will work on a device.

## When to set up Firebase

| Feature | Needs real `google-services.json`? |
|---------|-------------------------------------|
| Coding learning screen (disguise) | No |
| Login / Sign up / session | **Yes** |
| Chat list, search (Phase 3) | **Yes** |
| Live messaging (Phase 4) | **Yes** |

## Prerequisites

- A Google account
- Android Studio with the CodeShell project open
- Package name: `com.codeshell.app`

## 1. Create a Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/).
2. Click **Add project** → name it **CodeShell**.
3. Click **Create project**.

## 2. Register the Android App

1. Click the **Android** icon in the project overview.
2. Package name: `com.codeshell.app`
3. Click **Register app**.
4. Download **`google-services.json`**.
5. Replace the placeholder at `app/google-services.json`.
6. Sync Gradle in Android Studio.

## 3. Enable Firebase Authentication

1. **Build → Authentication → Get started**
2. **Sign-in method** → enable **Email/Password**
3. The app never shows email — it uses `username@codeshell.app` internally.

## 4. Create Cloud Firestore

1. **Build → Firestore Database → Create database**
2. Start in **production mode**
3. Pick a region near your users

### Deploy security rules (required)

Copy rules from [`firestore.rules`](../firestore.rules) in this repo:

1. Firebase Console → **Firestore → Rules**
2. Paste the contents of `firestore.rules`
3. Click **Publish**

**Important after Phase 3 update:** rules now include a public `usernames` collection (for sign-up checks) and `chats` collection. You must re-publish rules if you only deployed the Phase 2 version.

**Important after Phase 4 update:** rules now allow `chats/{chatId}/messages` (read/write for chat participants) and `presence/{userId}` (typing indicators). Re-publish [`firestore.rules`](../firestore.rules) if messaging returns `PERMISSION_DENIED`.

**Important after digit-ID update:** rules require usernames to be **4–10 digits only** (`^[0-9]+$`). Re-publish [`firestore.rules`](../firestore.rules) if you previously deployed letter-based rules. Existing alphabetic accounts (e.g. `sumitk1020`) will not pass the new app validator — register new digit IDs (shown as `CodeShell#2345`).

Or with Firebase CLI:

```bash
firebase deploy --only firestore:rules
```

### Collections created by the app

| Collection | Purpose |
|------------|---------|
| `users/{userId}` | User profile |
| `usernames/{username}` | Username registry (public read for availability check) |
| `chats/{chatId}` | Chat metadata |
| `chats/{chatId}/messages/{messageId}` | Session output / messages (Phase 4) |
| `presence/{userId}` | Typing indicator (Phase 4) |

### Username index

The sign-up availability check queries `users` by `username`. Firebase may prompt you to create a single-field index the first time the query runs — follow the link in Logcat or the console.

### Chat list index (Phase 3)

Composite index on `chats`:

- `participants` — **Arrays**
- `lastMessageTime` — **Descending**

Create via the link in Logcat when Home first loads, or in **Firestore → Indexes**.

### Messages index (Phase 4)

Opening a session orders messages by `timestamp` ascending. Firestore usually auto-indexes single-field queries; if you see `FAILED_PRECONDITION`, create an index on `messages.timestamp` for the subcollection.

## 5. Enable Firebase Storage (Phase 5)

1. **Build → Storage → Get started**
2. Choose the same region as Firestore when possible
3. Deploy storage rules from [`storage.rules`](../storage.rules):

```bash
firebase deploy --only storage
```

Or paste the contents of `storage.rules` in **Firebase Console → Storage → Rules → Publish**.

Storage path used by the app: `chats/{chatId}/{messageId}/{fileName}`

**Important:** Storage rules reference Firestore chat participants. You must have published [`firestore.rules`](../firestore.rules) first.

## 6. Enable Cloud Messaging (Phase 6)

1. Upgrade to the **Blaze** plan (required for Cloud Functions + Storage egress).
2. In Firebase Console → **Build → Cloud Messaging** — no extra Android console setup beyond registering the app.
3. Deploy Cloud Functions from this repo (see **Phase 6 — Cloud Functions** below).
4. On Android 13+, the app requests **POST_NOTIFICATIONS** at runtime on Home.

## 7. Phase 6 — Cloud Functions (15-min purge + disguised FCM)

From the project root (after `npm install` in `functions/`):

```bash
firebase deploy --only functions,firestore:rules,storage
```

Functions in [`functions/src/index.ts`](../functions/src/index.ts):

| Function | Trigger | Purpose |
|----------|---------|---------|
| `onMessageCreated` | New message doc | Sets `expiresAt` if missing; sends disguised FCM to recipient |
| `purgeExpiredMessages` | Every 5 minutes | Deletes expired messages + Storage under `chats/{chatId}/{messageId}/` |

**Optional:** Firestore Console → **TTL policies** on `messages.expiresAt` as a backup to the scheduled purge.

**Important after Phase 6 update:** re-publish [`firestore.rules`](../firestore.rules) — rules now require `expiresAt` on message create, allow participant chat/message delete, and block sends when the recipient has blocked the sender.

## 8. Verify

1. **Build → Make Project**
2. Run the app → Coding Learning Screen opens (no login).
3. Tap the **CLI** FAB → Login screen.
4. Sign up with a username and password → Home screen with welcome message.

### Common errors

| Error in app | Fix |
|--------------|-----|
| "Firebase is not configured yet" | Replace placeholder `google-services.json` |
| "Firestore rules not configured" | Deploy `firestore.rules` |
| "PERMISSION_DENIED" on sign-up | Publish Firestore rules; ensure Auth is enabled |
| "PERMISSION_DENIED" in chat | Re-publish Phase 4 rules (`messages` + `presence`) |
| Upload fails / media won't send | Enable Storage; publish `storage.rules` |
| Empty chat list / index error | Create composite index on `chats` (see above) |

## Phase 4 — implemented in app

- **ChatScreen** — real-time text messages via Firestore listener
- Send output, delivered/read receipts (✓ / ✓✓ on your bubbles)
- Typing indicator with 2s debounce (`presence/{userId}`)
- Long-press your bubble to unsend → *"This line was removed"*
- Back from session returns to **Coding Learning** disguise screen
- Long-press a terminal history row on Home opens the session

## Phase 5 — implemented in app

- **Images** — gallery picker + camera capture
- **Voice** — hold mic button to record, release to send
- **Files** — system file picker with filename + size in bubble
- Upload progress shown in bubble while sending
- Tap image/voice → **MediaViewer** (pinch-zoom image, audio play/pause/seek)
- File bubble → tap opens viewer; explicit **Save to device** writes to Downloads

## Phase 6 — implemented in app

- **Settings** — shows your CodeShell ID, change access key, hide-on-Home, block/unblock, sign out, 15-minute expiry notice
- **Home** — no last-message preview; **Edit** → multiselect → **Delete** removes sessions (+ Storage); long-press still opens session
- **Chat** — long-press your message → multiselect → **Remove** bulk-unsends (including Storage media)
- **Media** — no remote thumbnails or auto-download; viewer streams only; **Save to device** is the only local persist path
- **Privacy** — `FLAG_SECURE` on chat and media viewer (blocks screenshots / recents preview)
- **FCM** — disguised push: *"New learning snippet available"* (via Cloud Function)
- **Auto-delete** — messages carry `expiresAt` (+ server purge every 5 min)
