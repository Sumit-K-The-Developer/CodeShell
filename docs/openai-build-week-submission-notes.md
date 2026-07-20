# OpenAI Build Week Hackathon Submission Notes

Last checked: July 18, 2026  
Source pages: https://openai.devpost.com/, https://openai.devpost.com/rules, https://openai.devpost.com/details/faqs

## Short Summary

OpenAI Build Week asks builders to submit a working project built with Codex and GPT-5.6. The project can be an app, agent, website, game, workflow, developer tool, or something new, but it must fit one of four tracks:

- Apps for Your Life
- Work and Productivity
- Developer Tools
- Education

For CodeShell, the best-fit track is likely **Apps for Your Life**, unless we want to emphasize the learning/tutor side heavily enough to enter **Education**. Each project can enter only one track.

## Critical Dates

All official times are Pacific Time.

- Registration period: July 9, 2026 at 10:00 AM PT to July 21, 2026 at 5:00 PM PT.
- Submission period: July 13, 2026 at 9:00 AM PT to July 21, 2026 at 5:00 PM PT.
- Submission deadline: July 21, 2026 at 5:00 PM PT.
- India time equivalent: July 22, 2026 at 5:30 AM IST.
- Judging period: July 22, 2026 at 10:00 AM PT to August 5, 2026 at 5:00 PM PT.
- Winners announced: on or around August 12, 2026 at 2:00 PM PT.

Note: the free Codex credit request deadline was July 17, 2026 at 12:00 PM PT, which has already passed as of July 18, 2026.

## Eligibility Notes

- Entrants must be at least the age of majority in their country or territory of residence.
- Individuals, teams, and organizations can enter.
- Teams can have members in different eligible countries.
- India is listed as an included country on the hackathon page.
- The project must not violate laws, third-party rights, or intellectual property rules.
- Open source libraries and existing frameworks are allowed, as long as their licenses are respected and any pre-existing or third-party work is disclosed.

## What The Project Must Be

The project must:

- Be a working project built with Codex and GPT-5.6.
- Fit one of the four tracks.
- Run consistently on the platform it targets.
- Match what is shown in the demo video and written description.
- Be available for judges to test for free through the judging period.

Existing projects are allowed, but there is an important condition:

- If CodeShell existed before the hackathon submission period, the submission must clearly document what was added during the hackathon.
- Evidence should include dated commit history, timestamped Codex session logs, or equivalent proof.
- Judges should be able to understand which features are pre-existing and which features were meaningfully extended with Codex and/or GPT-5.6 after July 13, 2026 at 9:00 AM PT.

## What To Submit On Devpost

The submission needs:

- A working project.
- One selected category/track.
- A text description explaining the project, features, and how it works.
- A public YouTube demo video under 3 minutes.
- A code repository URL for judging/testing.
- A README with setup instructions and testing guidance.
- A `/feedback` Codex Session ID from the primary thread where most core functionality was built.

For the repository:

- It may be public with relevant licensing.
- Or it may be private, but it must be shared with:
  - testing@devpost.com
  - build-week-event@openai.com

For testing:

- Judges may or may not test the project.
- Make the judging path easy: provide a test build, demo link, sandbox, or test account where possible.
- The project must be available free of charge and without restriction for Sponsor, Administrator, and Judges until judging ends.

## Demo Video Requirements

The demo video must:

- Be 3 minutes or less.
- Be uploaded publicly to YouTube.
- Show a clear working demo of the project.
- Include audio/voiceover.
- Explain what was built.
- Explain how Codex was used.
- Explain how GPT-5.6 was used.
- Avoid third-party trademarks, copyrighted music, or copyrighted content unless permission has been obtained.
- Be in English, or include an English translation.

Important: the FAQ says voiceover is required. A silent screen recording or a video with only background music is not enough.

Suggested CodeShell video structure:

1. 0:00-0:20: State the problem and audience.
2. 0:20-1:30: Show the core CodeShell experience: disguised learning/chat flow, secure sessions, media/call/status features, and AI learning angle.
3. 1:30-2:20: Show the Build Week improvements that were added with Codex.
4. 2:20-2:50: Explain where GPT-5.6/Codex helped: architecture, Android debugging, WebRTC polish, profile/story/media features, rules, compile fixes.
5. 2:50-3:00: Close with why it matters and the selected track.

## README Requirements

The README should include:

- What CodeShell is.
- Target platform and prerequisites.
- Setup instructions.
- Firebase setup instructions, including Firestore/Storage rules deployment if required.
- Build/run instructions for Android Studio and Gradle.
- Any sample/test account instructions if judges need them.
- Known limitations.
- A section documenting what was built during OpenAI Build Week.
- A section explaining how Codex accelerated the workflow.
- A section explaining how GPT-5.6 was used.
- A section distinguishing pre-existing work from new hackathon work.

Suggested README sections:

- Project overview
- Why this matters
- Features
- Build Week additions
- Tech stack
- Architecture notes
- Setup
- Running the Android app
- Firebase configuration
- Demo/testing instructions
- How Codex and GPT-5.6 were used
- Pre-existing work vs Build Week work
- Security/privacy notes
- License

## Judging Criteria

The listed judging criteria are equally weighted:

### Technological Implementation

Judges ask:

- How thoroughly and skillfully does the project use Codex?
- Does the code reflect genuine effort?
- Is the implementation working and non-trivial?

For CodeShell, emphasize:

- Native Android implementation.
- Firebase-backed secure messaging/session model.
- WebRTC audio/video calling and screen sharing.
- Media handling, profile photos, cropper, stories/status.
- Settings, privacy controls, lifecycle handling, notifications, foreground services.
- Evidence of iterative Codex-driven debugging and compile validation.

### Design

Judges ask:

- Is it a working/runnable project?
- Does it feel like a complete, coherent product experience rather than a proof of concept?

For CodeShell, emphasize:

- Polished chat, call, profile, media, and story flows.
- Disguised/learning interface concept.
- Mobile-first UX and Android lifecycle handling.
- The work done to make call UI, banners, mini call view, story rings, and media previews feel integrated.

### Potential Impact

Judges ask:

- Does the project solve a real problem for a real audience?
- Does the demonstrated solution actually address that problem?

For CodeShell, possible positioning:

- A privacy-conscious learning and communication app for users who want discreet, low-friction study/chat spaces.
- A mobile-first app blending private conversation, learning assistance, and modern communication tools.
- A lightweight, no-cost feature approach using platform APIs and Firebase rather than paid SDKs.

### Quality Of The Idea

Judges ask:

- Is the concept creative or novel?
- Does it differ from existing ideas?

For CodeShell, emphasize:

- The disguised learning shell combined with private chat and modern communication patterns.
- The mix of learning mode, privacy-first behavior, ephemeral output, calls, media, profile, and story/status features.
- The product direction is not just another chat app; it is a guarded learning/communication environment.

## Tracks And CodeShell Fit

### Apps for Your Life

This is the strongest general fit if we present CodeShell as a consumer app for private everyday learning and communication.

Pros:

- Matches consumer everyday app language.
- Lets the privacy, chat, calls, profile, status, and learning flows all count as part of one lifestyle app.
- Avoids needing to prove it is primarily an education institution tool.

Risk:

- Need to make the product story specific enough so it is not judged as a generic chat app.

### Education

This could fit if the AI learning/tutor experience is central in the demo and description.

Pros:

- The disguised learning screen and AI tutor elements could stand out.
- Education track welcomes student/teacher/learner tools.

Risk:

- If the demo focuses mostly on chat/calls/status, it may feel less education-first.

Recommended category for current state: **Apps for Your Life**, with a strong learning/privacy angle.

## Compliance Checklist

- Join/register for the hackathon on Devpost.
- Confirm the project owner/team representative is eligible.
- Decide the track.
- Prepare the public YouTube demo video, under 3 minutes, with English voiceover.
- Make repository public or share private repository with the required emails.
- Add or update README.
- Document pre-existing vs Build Week work.
- Include Codex/GPT-5.6 usage evidence.
- Run final build and note build status.
- Provide testing path: APK, test build, or clear Android Studio run instructions.
- Provide `/feedback` Codex Session ID from the main build thread.
- Submit before July 21, 2026 at 5:00 PM PT / July 22, 2026 at 5:30 AM IST.

## Suggested Devpost Project Description Draft

CodeShell is a privacy-conscious Android app that blends a disguised learning interface with secure communication tools. It lets users keep study/chat sessions lightweight and private while still supporting modern expectations like media sharing, profile photos, audio/video calls, screen sharing, link previews, drafts, and status-style story updates.

During OpenAI Build Week, the project was meaningfully extended with Codex and GPT-5.6 across core product polish: call UX improvements, screen sharing, adaptive media previews, profile picture upload/cropping/caching, story/status publishing, selected-audience visibility, viewer tracking, share-panel handling, and Android lifecycle safeguards. Codex was used as the primary coding agent for feature planning, implementation, debugging, Firebase rules, and compile validation.

## What We Still Need Before Submission

- Decide the final track: Apps for Your Life vs Education.
- Capture a short demo on a real Android device.
- Prepare a clean README for judges.
- Make sure Firebase rules are deployed if stories/profile/media features are shown.
- Decide whether to provide an APK/test build or require Android Studio setup.
- Run a final full build close to submission.
- Generate and record the `/feedback` Codex Session ID.
- Prepare screenshots for Devpost if needed.

