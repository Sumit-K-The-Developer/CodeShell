# CodeShell

## Overview
CodeShell is a privacy-conscious learning and communication project with two native Android app flavors and a companion web app.

The Android app is built as a disguised learning shell with private chat, media sharing, profile photos, and audio/video calling. It has two learning flavors:

- **Coding flavor**: a coding tutor and coding lesson shell.
- **Grammar flavor**: a grammar tutor and grammar lesson shell.

The web app is a PC/PWA companion that shares the same Firebase backend. It shows a coding tutorial screen by default and reveals a terminal-style chat panel only after the user arms it with a hidden interaction or hotkey.

The problem CodeShell explores is simple: many people want a private, low-friction place to learn and communicate without the app feeling loud, exposed, or obviously social.

## Features
- Two Android flavors: coding and grammar.
- Coding tutor behavior for the coding flavor.
- Grammar tutor behavior for the grammar flavor, including Hinglish explanations with English examples.
- Disguised learning interface.
- Private conversation sessions.
- Firebase-backed user accounts, chat data, and media storage.
- Text messaging.
- Image/video/media sharing.
- Adaptive media preview bubbles.
- Persistent unsent message drafts.
- Clickable web links in chat.
- Android share-panel support for sharing links and videos into CodeShell.
- Optional profile photo upload during sign-up and from settings.
- Profile photo cropper with drag, corner resize, pinch zoom, and full-screen preview.
- Profile photos on conversation tiles and chat top bar.
- Permanent profile photo deletion from Firebase Storage.
- Audio calls.
- Video calls.
- Video-call screen-awake handling.
- Mini call view for continuing chat during a call.
- Tappable ongoing call banner.
- Incoming call banner for calls from another conversation.
- Audio route handling for speaker, earpiece, wired headset, and Bluetooth.
- Screen sharing during video calls.
- Screen-share foreground notification with pause/resume/stop actions.
- Improved call end reasons and reconnect behavior.
- Web app with a Python/coding tutorial disguise screen.
- Web app terminal panel with login, chat list, chat sessions, media viewing, fullscreen panel mode, inactivity close, and hotkeys.

## Built With
- Kotlin
- Jetpack Compose
- Android Studio
- Android SDK
- WebRTC
- Firebase Authentication
- Cloud Firestore
- Firebase Storage
- Firebase Cloud Messaging
- Firebase Functions
- Media3 / ExoPlayer
- Coil
- React
- TypeScript
- Vite
- Progressive Web App tooling
- OpenAI Codex with GPT-5.6

## What I Built During OpenAI Build Week
The project was started before the hackathon.

During OpenAI Build Week I:
- Added and refined the **grammar Android flavor** alongside the coding flavor.
- Improved the learning tutor behavior so each flavor has different teaching instructions.
- Improved audio/video call polish, including video screen-awake behavior, mini call mode, ongoing call banners, and better back-button behavior during calls.
- Integrated video-call screen sharing using Android MediaProjection and WebRTC.
- Added screen-share foreground notification controls for pause, resume, and stop.
- Improved foreground service handling for microphone, camera, and screen sharing.
- Added audio route behavior for speaker, earpiece, wired headset, and Bluetooth.
- Improved reconnect handling for temporary call disconnects.
- Improved call end reason messages and session logs.
- Redesigned the message input into a floating pill-style input bar.
- Added persistent draft text for unsent messages.
- Added adaptive aspect ratios for sent and received image/video preview bubbles.
- Enabled clickable web links in chat.
- Added Android share-panel support for links and videos.
- Added profile picture support across sign-up, settings, conversation tiles, and chat top bar.
- Built a custom profile photo cropper with full-image view, crop overlay, corner resizing, drag movement, and pinch zoom.
- Added full-screen profile photo viewing.
- Improved profile photo cache refresh so updates appear faster.
- Ensured removed profile photos are deleted from Firebase Storage.
- Improved inactivity handling so picker, cropper, capture, and scroll-hold flows do not accidentally close the app.
- Improved settings profile layout and related UI polish.
- Fixed build errors and compile-checked both Android debug flavors.
- Continued polishing the web app companion that shares the Firebase backend and provides a disguised desktop/PWA chat surface.

These changes were implemented using Codex with GPT-5.6.

## How I Used Codex and GPT-5.6
Codex with GPT-5.6 was used as the main development partner for the Build Week work.

Codex helped:
- Inspect the existing Android and web project structure.
- Compare CodeShell's call experience against polished apps and identify gaps.
- Plan feature changes before implementation.
- Implement Kotlin, Jetpack Compose, WebRTC, Firebase, and React/TypeScript changes.
- Debug screen sharing behavior on real Android devices.
- Improve media, profile photo, draft, and share-panel flows.
- Update Firebase rules for media/profile access.
- Fix compile errors after each change.
- Validate both Android flavors with Gradle compile tasks.

Codex `/feedback` Session ID: TODO

## How to Run
1. Clone or download the project.
2. Open the root project in Android Studio.
3. Configure Firebase for the Android app.
4. Add the required Firebase configuration file to the app module.
5. Deploy the Firebase rules from:
   - `firestore.rules`
   - `storage.rules`
6. Select one Android flavor:
   - `codingDebug`
   - `grammarDebug`
7. Build and run on an Android device.

Gradle examples:

```bash
./gradlew :app:assembleCodingDebug
./gradlew :app:assembleGrammarDebug
```

For the web app:

```bash
cd web
npm install
npm run dev
```

Then open the local Vite URL shown in the terminal. The web app can also be built for production:

```bash
cd web
npm run build
```

## Testing Notes
For the full Android experience, use two Android devices with two test accounts so messaging, profile updates, audio/video calls, screen sharing, and call banners can be tested between users.

For the web app, log in with an existing CodeShell account and verify that chats/media sync with the same Firebase backend used by Android.

## Future Improvements
- Add status/story video publishing with selected audience, story rings, full-screen viewer, progress bars, and view tracking.
- Add in-app player support for YouTube Shorts, Instagram Reels, and similar short-video links.
- Bring audio/video calling to the web app.
- Add richer AI learning workflows inside both coding and grammar flavors.
- Add more advanced profile and privacy controls.
- Add a polished onboarding flow for first-time users.
- Provide easier judge/demo access through hosted web demo, APK link, or test accounts.

## Acknowledgements
Built using OpenAI Codex with GPT-5.6.

