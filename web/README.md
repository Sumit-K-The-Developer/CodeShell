# CodeShell Web (PC / PWA)

Windows desktop client for CodeShell — a web app that shares the same Firebase backend as the Android app.

## Features

- **Disguise screen** — coding tutorial UI (always visible on the left)
- **Arm/disarm gate** — click title or `Ctrl+Shift+.` to enable terminal; title turns red when armed
- **Side panel terminal** — chat list and sessions slide in on the right (only when armed + `Esc`)
- **Text chat** — send and receive messages (same Firestore schema as Android)
- **View media** — images, video, audio, and file attachments received from mobile
- **PWA install** — install in Chrome/Edge as a standalone Windows app

## Prerequisites

1. **Node.js** 18+ — [https://nodejs.org](https://nodejs.org)
2. **Firebase Web app** registered in your existing CodeShell Firebase project:
   - Firebase Console → Project settings → Your apps → **Add app** → Web
   - Copy the `appId` into `web/.env` as `VITE_FIREBASE_APP_ID`
3. **Authentication** → Sign-in method → Email/Password enabled (already required for Android)

## Setup

```powershell
cd web
npm install
```

Edit `web/.env` if needed (defaults match `app/google-services.json`).

## Run locally (development)

```powershell
cd web
npm run dev
```

Open the URL shown (usually `http://localhost:5173`).

1. You should see the **Python Code Tutorial** disguise screen (full width, title normal).
2. Press **`Ctrl+Shift+.`** or **click the title** → title turns red (terminal armed).
3. Press **`Esc`** → terminal panel slides in on the right; login if signed out.
4. **`Esc`** again → panel closes; lesson content stays visible on the left.
5. **`Ctrl+Shift+.`** or **click the red title** → disarms (title normal) and closes panel if open.

Keep the Android app logged in on the same account to verify both stay in sync.

## Install on Windows (PWA)

### From local dev server

1. Run `npm run dev`
2. In **Chrome** or **Edge**, open `http://localhost:5173`
3. Address bar → **Install app** / **App available** icon
4. The app appears on your taskbar as **PyTutorial**

### From Firebase Hosting (recommended for daily use)

```powershell
cd web
npm run build
cd ..
firebase deploy --only hosting
```

Open your Hosting URL (e.g. `https://codeshell-a3dd6.web.app`) → Install app.

## Build for production

```powershell
cd web
npm run build
```

Output is in `web/dist/`.

## Hotkeys

| Key | Action |
|-----|--------|
| `Ctrl+Shift+.` | Arm/disarm terminal (click title does the same; disarm closes panel) |
| `Esc` | Toggle terminal panel when armed (closes media overlay first if open) |
| `Enter` | Send message (in chat input) |
| `Shift+Enter` | New line in chat input |

On every fresh page load, terminal starts **disarmed** — `Esc` does nothing until you arm.

## What is not included (by design)

- Sending images/voice/files from PC
- Push notifications
- Sign-up (use Android app first, or add later)
- Grammar disguise flavor (coding only)

## Android Studio?

**Not used** for the web app. Android Studio is only for the `app/` Android project. Use Cursor/VS Code and a terminal for `web/`.

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Firebase is not configured` | Add Web app in Firebase Console; fill `VITE_FIREBASE_APP_ID` in `.env` |
| `PERMISSION_DENIED` on login/chat | Republish `firestore.rules` from repo root |
| Media won't load | Check `storage.rules`; ensure Storage is enabled |
| Chat list empty / index error | Create Firestore composite index on `chats` (`participants` + `lastMessageTime`) — Firebase Console will show a link if missing |
| Auth works on Android but not web | Enable Email/Password in Firebase Auth; add `localhost` to authorized domains (added by default) |

## Project structure

```
web/
├── public/lessons/default.json   # Disguise content (from Android coding flavor)
├── src/
│   ├── lib/                      # Firebase + chat logic
│   ├── components/
│   │   ├── LearnContent.tsx      # Disguise (left pane)
│   │   ├── TerminalPanel.tsx     # Chat side panel
│   │   └── terminal/             # Login, home, chat panels
│   └── pages/AppShell.tsx        # Split layout shell
└── package.json
```
