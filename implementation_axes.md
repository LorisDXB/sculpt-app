# Implementation Axes - Widget-First AI Calorie Tracker

## Purpose

This document turns the raw implementation plan into a build strategy that is easier to execute over time.

The raw plan remains the source document for feature intent. This file is the working roadmap.

Guiding choices for this project:

- greenfield build;
- long-term maintainability over fastest hack;
- Android-only;
- widget-first UX;
- React Native kept minimal;
- native Kotlin owns widget behavior.

## Overall Feasibility

This project is feasible.

The hard parts are concentrated in a few technical areas rather than spread across the whole app:

- Android widget interaction constraints;
- native/widget and React Native boundary design;
- camera capture reliability;
- keeping state transitions correct after edits, resets, and AI results.

What makes it doable:

- the product scope is intentionally narrow;
- there is no account system or sync;
- there is no large in-app UI surface;
- the AI integration is conceptually straightforward if the API response is tightly constrained;
- native Android widgets are a solved platform feature if we accept their limitations.

## Recommended Build Strategy

Build this as a native-widget-first system with a thin app shell, not as a React Native app trying to force widget support.

Recommended default architecture:

- React Native for setup and settings only;
- Kotlin for widget provider, widget actions, capture entry, and state updates;
- a single state repository on Android as the source of truth;
- a narrow bridge between React Native and native storage/config APIs.

This is the best long-term choice because it reduces hidden integration debt in the most platform-sensitive part of the product.

## Axis 1 - Product Boundary

### Goal

Lock the MVP tightly enough that engineering stays focused on the widget-first experience.

### Decisions

- Keep the app shell minimal.
- Do not build history, analytics, search, barcode, or accounts.
- The widget is the primary interface.
- The app only exists for setup, permissions, settings, and fallback flows.

### Success Criteria

- A user can use the product daily without opening a normal meal history screen.
- All high-frequency actions are available from the widget or capture flow.

### Risks

- Scope creep into a standard calorie tracker.
- Over-investing in RN screens that are not core to the product.

### Execution Notes

- Use this axis as a guardrail whenever a new feature idea appears.
- If a feature does not improve widget-first logging or correction, it probably does not belong in MVP.

## Axis 2 - Architecture and Ownership Boundaries

### Goal

Separate responsibilities cleanly between React Native and Android native code.

### Recommended Ownership

React Native owns:

- onboarding/setup screen;
- settings screen;
- user-editable configuration;
- optional API key entry;
- lightweight help/instructions.

Native Kotlin owns:

- widget provider;
- widget rendering;
- widget action dispatch;
- camera entry flow;
- image processing pipeline;
- local state repository;
- widget refresh logic;
- daily reset logic;
- background workers if used.

### Success Criteria

- Widget behavior does not depend on a running React Native runtime.
- State updates work when the app UI is closed.
- The native layer can fully update widget state on its own.

### Risks

- Too much business logic split across RN and Kotlin.
- Duplicate state models on each side.

### Execution Notes

- Design one canonical state schema early.
- Keep the RN-to-native bridge intentionally small and boring.

## Axis 3 - Widget UX and Interaction Model

### Goal

Deliver a widget interaction model that works reliably across Android launchers.

### Recommended Direction

Treat swipe gestures as experimental, not foundational.

Practical default:

- use tap-based interaction zones first;
- validate whether vertical gesture support is truly worth the complexity;
- only add swipe if testing proves it reliable enough.

### MVP Interaction Set

- tap "Log meal";
- tap calorie remaining top/bottom zones to increase/decrease;
- tap last meal calorie top/bottom zones to increase/decrease;
- tap value to cycle step size.

### Success Criteria

- Interactions are consistent on at least one stock-like launcher.
- Users can correct AI estimates quickly without opening the app.

### Risks

- RemoteViews interaction limitations;
- launcher-specific differences;
- hidden controls becoming unclear without visual affordance.

### Execution Notes

- Prototype this axis before deep API work.
- If usability is weak, add minimal visible affordances instead of forcing fancy gestures.

## Axis 4 - Data Model and State Transitions

### Goal

Make all calorie, macro, and reset logic deterministic and easy to trust.

### Recommended Direction

Create a single Android-native state repository that owns:

- current day metadata;
- calorie target;
- calories consumed;
- calories remaining;
- last meal summary;
- adjustment step;
- setup/config status.

### Required State Rules

- Every read path checks whether a daily reset is needed.
- Widget rendering always uses normalized current-day state.
- Last meal edits update both meal values and day totals atomically.
- Macro normalization happens after AI parsing, before persistence.

### Success Criteria

- No contradictory values appear between totals and meal data.
- Date rollover is correct even if background jobs fail.
- Rebooting the phone does not lose the current state.

### Risks

- Drift between total calories and last meal adjustments;
- reset logic duplicated in multiple places;
- time-zone/date edge cases.

### Execution Notes

- Write this logic before UI polish.
- This axis deserves unit tests earlier than most others.

## Axis 5 - Capture Pipeline

### Goal

Open the camera quickly and reliably from widget or launcher shortcut, then hand off a usable image for analysis.

### Recommended Direction

Use an Activity-based capture flow instead of background camera tricks.

Pipeline:

1. Widget action launches capture entry.
2. Camera opens through a native Android flow.
3. Captured image is written to a temporary file/URI.
4. Image is compressed locally.
5. Analysis starts.
6. State updates and widget refreshes.

### Success Criteria

- The capture flow works from the widget without requiring the RN UI to be open.
- Temporary image handling is stable and recoverable.

### Risks

- permission handling;
- Activity launch restrictions from widget context;
- camera integration edge cases across devices.

### Execution Notes

- Keep the first version intentionally simple.
- Do not queue offline photos in MVP.

## Axis 6 - AI Provider, Prompting, and Validation

### Goal

Use a multimodal API that returns structured nutrition estimates reliably enough for personal use.

### Recommended Direction

Choose a provider based on:

- image support;
- strict JSON output support;
- low per-call cost;
- simple REST integration;
- decent food estimation quality.

### Required Safeguards

- strict schema parsing;
- malformed-response handling;
- macro-to-calorie consistency normalization;
- non-food fallback handling;
- timeout/error states surfaced clearly.

### Success Criteria

- A valid meal photo usually results in a parsable estimate.
- A bad model response does not corrupt local state.

### Risks

- model inconsistency;
- drifting provider behavior;
- hidden cost surprises if the prompt or image size is poorly controlled.

### Execution Notes

- Keep the provider swappable behind a small client interface.
- Persist confidence only as support metadata, not as a primary UX element.

## Axis 7 - App Shell, Setup, and Settings

### Goal

Provide just enough normal UI to make the widget installable and configurable.

### Required Surfaces

- first-launch setup;
- settings screen;
- widget setup guidance;
- permission prompts;
- reset and clear-data actions.

### Success Criteria

- A first-time user can configure target calories and get to a working widget without confusion.
- The app shell remains intentionally small.

### Risks

- onboarding becoming too clever or too broad;
- putting operational logic in RN that belongs in native code.

### Execution Notes

- Think of RN as the control panel, not the product surface.

## Axis 8 - Reliability, Failure Modes, and Recovery

### Goal

Make the product stable enough for personal daily use even when the AI call or connectivity fails.

### Required Failure States

- offline;
- API error;
- malformed AI response;
- non-food image;
- duplicate tap / duplicate submission;
- stale widget state after reboot or date change.

### Success Criteria

- Failures degrade gracefully without breaking totals.
- The widget always shows a sane state, even after errors.

### Risks

- duplicate logs from repeated taps;
- widget showing stale analysis state;
- inconsistent recovery after interrupted capture or upload.

### Execution Notes

- Add loading and error widget states explicitly.
- Prefer blocking bad flows over introducing queues too early.

## Axis 9 - Delivery Sequence

### Goal

Build in an order that reduces architectural risk early.

### Recommended Sequence

1. Scaffold project and native widget shell.
2. Prove widget rendering and updates from local state.
3. Implement state repository and daily reset.
4. Implement widget interactions with tap zones.
5. Implement capture flow.
6. Implement AI analysis and validation.
7. Implement setup/settings shell.
8. Harden failure handling and polish.

### Why This Order

- It proves the hardest platform assumptions early.
- It avoids building app UI before widget constraints are known.
- It prevents AI integration from masking state-management flaws.

## Suggested Milestones

### Milestone A - Platform Proof

Deliverables:

- Android project scaffolded;
- widget visible on home screen;
- widget renders static mock state;
- widget can refresh from local storage.

Exit condition:

- we know the widget shell is viable.

### Milestone B - Stateful Widget

Deliverables:

- canonical local state repository;
- date-aware reset logic;
- render-ready widget model;
- persistent adjustment step.

Exit condition:

- widget numbers are trustworthy and persistent.

### Milestone C - Interactive Widget

Deliverables:

- tap-based adjustments;
- step-size cycling;
- widget-triggered actions wired cleanly.

Exit condition:

- user can correct values directly from the widget.

### Milestone D - Capture and Analysis

Deliverables:

- camera capture flow;
- image compression;
- AI API client;
- strict parsing and normalization;
- widget refresh after successful analysis.

Exit condition:

- a meal photo produces usable widget state.

### Milestone E - Operational MVP

Deliverables:

- setup/settings screens;
- error/loading states;
- reset/clear actions;
- duplicate-submission protection;
- reboot/date-change validation.

Exit condition:

- daily personal use is realistic.

## Practical Execution Tracker

Use this checklist as the working implementation tracker.

### Foundation

- Choose React Native version and Android minimum SDK.
- Scaffold the Android/RN project.
- Create the Kotlin package structure for widget, storage, capture, AI, and workers.
- Define the canonical state schema.
- Define the RN/native boundary.

### Widget Core

- Create `AppWidgetProvider`.
- Create widget XML layout for 4x2 MVP.
- Implement renderer from static data.
- Implement widget update trigger path.
- Validate add-to-home-screen and resize behavior.

### State Core

- Implement local state store.
- Implement `WidgetStateRepository`.
- Implement date rollover logic.
- Implement calorie remaining calculations.
- Implement last meal update logic.
- Implement adjustment-step persistence.

### Widget Interaction

- Add tap zones for increase/decrease on remaining calories.
- Add tap zones for increase/decrease on last meal calories.
- Add step-size cycle action.
- Refresh widget after each action.
- Validate persistence across app close and reboot.

### Capture

- Add widget "Log meal" action.
- Launch capture Activity from widget context.
- Request and handle camera permission.
- Save image to temporary storage.
- Compress image before upload.

### AI

- Pick provider.
- Build image upload request.
- Implement strict response parsing.
- Implement normalization for macro/calorie mismatch.
- Handle non-food and invalid responses.
- Persist successful result and refresh widget.

### App Shell

- Build setup screen.
- Build settings screen.
- Add calorie target editing.
- Add API key entry if required.
- Add reset today action.
- Add clear local data action.
- Add widget install/help instructions.

### Reliability

- Add widget loading state.
- Add widget error state.
- Prevent duplicate submissions.
- Handle offline capture block.
- Validate reboot behavior.
- Validate date change behavior.
- Validate multiple widget instances if supported.

## Questions Resolved for This Plan

Assumptions currently locked in:

- This is a greenfield project.
- The planning should optimize for long-term cleanliness over the fastest possible shortcut.
- The second markdown file should be a practical strategic roadmap, not just a note dump.

## Recommended Next Step

The next useful step is not more planning. It is a technical bootstrap:

1. scaffold the project;
2. implement the native widget proof of concept;
3. validate interaction constraints before deeper feature work.
