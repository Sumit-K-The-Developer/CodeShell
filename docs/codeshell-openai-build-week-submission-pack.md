# CodeShell - OpenAI Build Week Submission Pack

Last prepared: July 18, 2026  
Hackathon: OpenAI Build Week  
Suggested track: Apps for Your Life  
Alternate track: Education  
Official rules reviewed: https://openai.devpost.com/rules

## Devpost Project Title
CodeShell

## Short Tagline
A disguised learning shell with private chat, media, calls, and AI-assisted study across Android and web.

## Short Description
CodeShell is a privacy-conscious learning and communication project. It includes two native Android flavors, **coding** and **grammar**, plus a companion React/Vite web app. The Android app combines a disguised learning interface with private conversations, media sharing, profile photos, audio/video calls, and screen sharing. The web app shares the same Firebase backend and provides a PC/PWA-style disguised coding tutorial surface with a hidden terminal chat panel.

During OpenAI Build Week, CodeShell was meaningfully extended using Codex with GPT-5.6. The work focused on call polish, screen sharing, profile photos, media handling, message drafts, share-panel support, Android lifecycle handling, flavor-specific tutor behavior, and project-wide submission readiness.

## Recommended Category
### Primary: Apps for Your Life
CodeShell is best positioned as a consumer app for private everyday learning and communication. The core audience is a user who wants a quiet, discreet place to study, chat, share media, and call without the app feeling like a public social platform.

### Alternate: Education
Education is also reasonable because CodeShell has coding and grammar learning shells with tutor behavior. Choose this only if the demo and pitch focus mostly on learning outcomes rather than the combined private communication experience.

## Rule-Mapped Requirements
The OpenAI Build Week rules require a working project built with Codex and GPT-5.6. The project must fit one track, run consistently on the intended platform, include a text description, include a public YouTube demo video under 3 minutes with audio, provide a code repository URL, include setup/testing guidance in the README, and provide a `/feedback` Codex Session ID from the main build thread.

Because CodeShell existed before the hackathon submission period, the submission should clearly separate prior work from new Build Week work. The README and Devpost description should state that the project was started before the hackathon and then list what was meaningfully extended during Build Week using Codex and GPT-5.6.

Submission TODOs:
- Repository URL: TODO
- Demo video URL: TODO
- Codex `/feedback` Session ID: TODO
- Judge testing path: TODO
- Track selected on Devpost: Apps for Your Life, unless you decide Education.

## Devpost Project Description
CodeShell is a private learning and communication app built around a disguised shell experience. On Android, it comes in two flavors: a coding flavor for programming lessons and a grammar flavor for language learning. Behind the learning interface, users can access private conversation sessions with messaging, media, profile photos, calls, and screen sharing.

The project also includes a web companion app for PC/PWA use. The web app shows a Python/coding tutorial screen by default and reveals a terminal-style chat panel only after the user arms it through a hidden interaction or hotkey. It shares the same Firebase backend as the Android app, so chats and media can stay connected across platforms.

For OpenAI Build Week, I used Codex with GPT-5.6 to turn CodeShell from a working private learning/chat project into a more polished cross-platform product. Build Week work included call UX improvements, video-call screen sharing, mini call views, call banners, audio route behavior, reconnect handling, adaptive media previews, persistent drafts, profile photo upload/crop/cache/delete flows, share-panel integration, Android lifecycle fixes, Firebase rules updates, and compile validation for both Android flavors.

The result is a working native Android app plus a web companion that demonstrate how Codex can support a real product sprint: inspecting an existing codebase, planning changes, implementing Kotlin/Compose/WebRTC/Firebase/React updates, debugging real-device behavior, and keeping the project aligned with no-cost platform APIs where possible.

## Problem
Many people want a private place to learn and communicate, but most tools split that into separate apps: one for study, one for chat, one for calls, and another for desktop access. CodeShell explores a more discreet pattern: a learning surface first, with private communication tools available when needed.

The product is aimed at users who want:
- A quiet learning-first interface.
- Private chat sessions.
- Modern media and calling features.
- Cross-device access through Android and web.
- Less exposed interaction patterns than conventional social apps.

## Solution
CodeShell combines learning screens, AI-assisted tutor behavior, private chat, media sharing, profile identity, and real-time calls into one product. The two Android flavors tailor the learning experience:

- **Coding flavor**: coding lessons and coding tutor prompts.
- **Grammar flavor**: grammar lessons and grammar tutor prompts, including Hinglish explanations with English examples.

The web app extends the concept to desktop/PWA use with a disguised coding tutorial page and a hidden terminal panel for chat.

## Current Feature Set
### Android App
- Coding and grammar product flavors.
- Disguised learning interface.
- Flavor-specific AI tutor prompts.
- Firebase Authentication login/sign-up.
- Private conversation list and chat sessions.
- Text messaging.
- Image/video/media sharing.
- Adaptive media preview sizing.
- Message draft persistence.
- Clickable links.
- Share-to-CodeShell support from Android's share panel.
- Profile photo upload during sign-up and settings.
- Custom cropper with drag, corner resize, pinch zoom, preview, and storage cleanup.
- Profile photos on conversation tiles and chat top bar.
- Audio calls and video calls.
- Screen sharing in video calls.
- Mini call view and ongoing call banner.
- Incoming-call banner for other conversations.
- Audio route handling for speaker, earpiece, headset, and Bluetooth.
- Reconnect grace handling and clearer call-end messages.
- Lifecycle safeguards for picker, cropper, capture, scroll, calls, and screen timeout.

### Web App
- React/Vite/TypeScript companion app.
- Python/coding tutorial disguise screen.
- Hidden terminal panel controlled by arm/disarm behavior and hotkeys.
- Firebase-backed login.
- Chat list and chat sessions using the same backend as Android.
- Received media viewing.
- Panel fullscreen mode.
- Inactivity-close behavior.
- PWA build/install path.

## What Was Built During OpenAI Build Week
The project was started before the hackathon.

During OpenAI Build Week, I:
- Added and refined the grammar Android flavor alongside the coding flavor.
- Improved flavor-specific AI tutor prompts.
- Improved audio/video call polish.
- Added video screen-awake handling.
- Added mini call mode and tappable ongoing call banners.
- Changed active-call back behavior so it minimizes instead of disconnecting.
- Added incoming call indication for another conversation.
- Integrated video-call screen sharing using Android MediaProjection and WebRTC.
- Added screen-share foreground notification controls.
- Improved foreground service support for microphone, camera, and screen sharing.
- Added speaker/earpiece/headset/Bluetooth audio route behavior.
- Added reconnect grace behavior and clearer call end reasons.
- Improved media preview aspect ratios.
- Redesigned the input bar as a floating pill.
- Added unsent draft persistence.
- Enabled clickable web links in chat.
- Added Android share-panel support for shared text/link/video.
- Added profile photo upload/crop/edit/remove behavior.
- Improved profile photo crop UX with full-image crop overlay, drag, resize, pinch zoom, and full-screen viewing.
- Improved profile photo loading and cache refresh.
- Ensured removed profile photos are deleted from Firebase Storage.
- Improved inactivity handling around external pickers, crop windows, camera/video capture, and scroll-hold.
- Updated Firebase rules for media/profile access.
- Fixed build errors and verified both Android debug flavors.
- Continued shaping the web app as a Firebase-backed PC/PWA companion.

Stories/status and short-video in-app playback were explored but are **not included in the current submission scope**. They are listed as future improvements.

## How Codex and GPT-5.6 Were Used
Codex with GPT-5.6 was used as the main collaborator for Build Week.

Codex helped with:
- Understanding the existing Android and web architecture.
- Comparing call UX against polished apps and turning gaps into an implementation plan.
- Implementing Kotlin and Jetpack Compose changes.
- Implementing WebRTC screen sharing and debugging real-device behavior.
- Updating Android foreground service and notification behavior.
- Refining profile photo upload, crop, cache, and deletion flows.
- Improving media previews, message drafts, share-panel handling, and lifecycle behavior.
- Reviewing Firebase access patterns and updating rules.
- Fixing build errors as they appeared.
- Compile-checking `compileCodingDebugKotlin` and `compileGrammarDebugKotlin`.
- Writing submission-ready README and project notes.

In the Devpost submission and demo video, mention that GPT-5.6 powered the Codex reasoning and implementation workflow: planning product choices, making engineering tradeoffs, writing code changes, debugging, and validating the result.

Codex `/feedback` Session ID: TODO

## Technical Stack
### Android
- Kotlin
- Jetpack Compose
- Android SDK
- Android product flavors
- WebRTC
- MediaProjection
- Foreground services
- Firebase Authentication
- Cloud Firestore
- Firebase Storage
- Firebase Cloud Messaging
- Firebase Functions
- Media3 / ExoPlayer
- Coil

### Web
- React
- TypeScript
- Vite
- Firebase Web SDK
- PWA tooling

## Demo Video Plan
Target length: under 3 minutes.

### 0:00-0:20 - Intro
Show Android launch and explain:
"CodeShell is a private learning and communication project with two Android flavors, coding and grammar, plus a web companion app."

### 0:20-0:55 - Android Learning Flavors
Show:
- Coding flavor lesson/tutor.
- Grammar flavor lesson/tutor if available on device/build.
- Explain that each flavor has its own learning identity.

### 0:55-1:35 - Chat, Media, Profile
Show:
- Conversation list.
- Chat screen.
- Floating input bar and draft.
- Media preview.
- Profile photo on tile/top bar.
- Profile cropper or full-screen profile view.

### 1:35-2:15 - Calls
Show:
- Audio/video call UI.
- Mini call mode.
- Ongoing call banner.
- Audio route control.
- Screen sharing.

### 2:15-2:35 - Web App
Show:
- Web tutorial disguise screen.
- Arm/disarm or hotkey behavior.
- Terminal chat panel.
- Shared Firebase chat/media connection.

### 2:35-2:55 - Codex/GPT-5.6
Narrate:
"I used Codex with GPT-5.6 as the main development partner. It helped inspect the codebase, plan product polish, implement Android/WebRTC/Firebase and web changes, debug real devices, and compile-check both Android flavors."

### 2:55-3:00 - Close
"CodeShell is submitted as Apps for Your Life: a private learning and communication app across Android and web."

## Demo Voiceover Draft
CodeShell is a privacy-conscious learning and communication project. It includes two Android flavors, coding and grammar, plus a companion web app that shares the same Firebase backend.

On Android, CodeShell starts as a disguised learning shell. The coding flavor behaves like a coding tutor, while the grammar flavor behaves like a grammar tutor. Behind that learning surface, users can access private chat sessions with text, media, drafts, links, profile photos, and secure conversation controls.

During Build Week I focused on making the app feel more complete. I polished audio and video calls, added mini call mode, ongoing call banners, better call end reasons, reconnect handling, audio route controls, and video-call screen sharing using Android MediaProjection and WebRTC.

I also improved the everyday chat experience: adaptive media previews, a floating pill input bar, persistent drafts, share-panel support, profile photo upload and cropping, full-screen profile viewing, faster profile refresh, and proper storage deletion.

The web companion app brings the disguised idea to desktop. It shows a coding tutorial page by default, then reveals a terminal-style chat panel only after the user arms it with a hidden interaction or hotkey.

I used Codex with GPT-5.6 as the main development partner. Codex helped inspect the project, plan changes, write Kotlin, Compose, WebRTC, Firebase, React, and TypeScript updates, debug real-device behavior, and compile-check both Android flavors. CodeShell is submitted as an Apps for Your Life project: a private learning and communication app across Android and web.

## README Notes
The root README has been rewritten to include:
- Project overview.
- Android coding and grammar flavors.
- Web app companion.
- Current features only.
- Build Week changes.
- Codex/GPT-5.6 collaboration explanation.
- Android and web run instructions.
- Future improvements, including stories/status and short-video in-app player.

## Future Improvements To Mention
- Status/story video publishing with selected audience, story rings, full-screen viewer, progress bars, and viewer tracking.
- In-app player for YouTube Shorts, Instagram Reels, and similar short-video links.
- Audio/video calling in the web app.
- More advanced AI learning workflows for coding and grammar.
- Hosted demo or judge-friendly APK/test account setup.

