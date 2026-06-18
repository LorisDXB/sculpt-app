# D1 Implementation Plan

## Scope

Implement a fully custom native capture flow for meal analysis:

1. open an in-app camera instead of the phone's default camera app;
2. show a live preview with a capture action and a way to back out to the home screen;
3. after capture, show the shot preview;
4. place a send button and a microphone button on that captured-photo screen;
5. let the user hold the microphone button to speak context;
6. when the user releases, store the spoken text locally for that submission;
7. send the image plus optional spoken context to the AI;
8. immediately return control to the launcher/widget flow while analysis continues in background.

## UI Target

The capture screen should use a simple two-state layout:

### Live camera state

- full preview area
- bottom row with two horizontally aligned icon buttons:
  - left: back/return arrow
  - right: camera shutter icon

### Captured-photo state

- the preview area shows the shot that was just taken
- the bottom row changes:
  - the camera button is replaced by a validate button with a checkmark icon
  - a microphone button appears to the right of the validate button
  - the back/return arrow remains available on the left

This means the action row is stateful:

- before capture: `back + camera`
- after capture: `back + validate + microphone`

The microphone is only visible once a photo exists, since it is context for that specific shot.

## Why This Changes The Architecture

This is a different shape than the earlier plan.

The previous approach assumed:

- system camera app;
- result handed back into `MealCaptureActivity`;
- optional speech added as a lightweight review step.

What you want now is:

- a fully in-app camera surface;
- a two-state native capture UI:
  - live camera preview state;
  - captured-photo review/send state;
- push-to-talk interaction tied directly to the send screen.

That makes `D1` a larger but still plausible native Android feature. It should be implemented as a custom capture activity backed by CameraX, then connected to the existing `WorkManager` analysis path.

## Current Baseline

- [AddMealEntryActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/AddMealEntryActivity.kt:11) already gates entry on API key and connectivity, then launches [MealCaptureActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt:22).
- [MealCaptureActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt:22) currently delegates to `ACTION_IMAGE_CAPTURE`, so it is not structured for in-app preview/rendering yet.
- [MealAnalysisWorker.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealAnalysisWorker.kt:17) already provides the background-analysis backbone we want to keep.
- [NutritionApiClient.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/ai/NutritionApiClient.kt:39) still needs an API shape upgrade to accept optional speech context.

## Recommended Build Order

1. Introduce CameraX and convert `MealCaptureActivity` into a native in-app camera screen.
2. Add the captured-photo review/send state inside the same activity.
3. Add press-and-hold microphone capture on the review/send state.
4. Thread stored transcript text into the background worker.
5. Extend the AI request to include transcript context.
6. Polish lifecycle, permissions, and cleanup behavior.

This order avoids dependency issues:

- the custom camera must exist before the capture/review UI can behave correctly;
- push-to-talk should be added only after there is a stable post-capture screen to attach it to;
- worker/API changes should come after the UI can already produce transcript text;
- cleanup should be finalized after all interaction branches exist.

## Plan

### 1. Replace The System Camera With A Native CameraX Screen

Goal: stop launching the default camera app and show the camera feed directly inside `MealCaptureActivity`.

Changes:

- Refactor [MealCaptureActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt:22) away from `ACTION_IMAGE_CAPTURE`.
- Add CameraX dependencies and bind preview plus image capture use cases in the activity.
- Add camera permission handling if it is not already covered by manifest/runtime flow.
- Add a layout with:
  - full camera preview;
  - left-aligned back/return arrow button;
  - right-aligned camera capture button.

Likely files:

- [MealCaptureActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt:22)
- [AndroidManifest.xml](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/AndroidManifest.xml:1)
- `android/app/build.gradle`
- New camera layout resource
- [strings.xml](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/res/values/strings.xml:1)

Notes:

- This should stay native Android, not React Native, because the current widget-driven entry flow is already native.
- CameraX is the ergonomic choice here; trying to build this around camera intents would fight the requested UX.

### 2. Add A Captured-Photo Review/Send State In The Same Activity

Goal: after taking a photo, swap from live preview mode into a review mode.

Changes:

- Keep this inside `MealCaptureActivity` as a stateful screen rather than introducing a second activity unless the code becomes too brittle.
- After capture:
  - stop showing the live camera preview;
  - show the captured image preview;
  - replace the camera button with a validate/checkmark button;
  - show a microphone button to the right of the validate button;
  - keep the back/return arrow available on the left;
  - allow the user to discard/retake if needed.
- Store the raw image path in activity state so it survives rotation/process recreation if feasible.

Likely files:

- [MealCaptureActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt:22)
- New or expanded camera layout resource

Why same activity first:

- it keeps camera setup, capture result, and preview state in one place;
- it avoids extra handoff complexity while we are still stabilizing the flow.

UI note:

- The cleanest implementation is likely one shared bottom action bar whose visible buttons change with the activity state, rather than two separate screens with duplicated controls.

### 3. Add Press-And-Hold Microphone Context Capture

Goal: allow the user to hold the microphone button, speak, and on release persist the recognized text for the upcoming submission.

Changes:

- Add a microphone button beside the send button on the captured-photo screen.
- On press-and-hold:
  - begin voice capture;
  - visually indicate recording;
  - collect spoken input.
- On release:
  - stop capture;
  - store the transcript in memory for the pending submission;
  - show the recognized text somewhere above or near the controls.
- Support re-recording so a second hold replaces or updates the previous transcript.

Implementation note:

- The exact capture API should be chosen for the desired interaction model.
- Because you specifically want hold-to-talk and release-to-store, a pure `RecognizerIntent` handoff may feel too indirect. We should plan for a custom speech capture flow if needed, or verify whether the device speech stack can be wrapped cleanly enough for press-and-hold semantics.

Likely files:

- [MealCaptureActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt:22)
- [strings.xml](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/res/values/strings.xml:1)

Risk note:

- This is the most implementation-sensitive part of `D1`. The UX is clear, but Android speech APIs vary in how naturally they support push-to-talk behavior.

### 4. Submit Path And Background Analysis Contract

Goal: preserve the current fast widget behavior once the user taps send.

Changes:

- Keep the existing pattern where submission:
  - marks widget state as `ANALYZING`;
  - refreshes the widget;
  - enqueues background work;
  - closes the UI immediately.
- Extend [MealAnalysisWorker.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealAnalysisWorker.kt:17) to accept:
  - raw photo path;
  - optional transcript text.
- Make send available with or without a transcript so voice remains optional.

Likely files:

- [MealCaptureActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt:22)
- [MealAnalysisWorker.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealAnalysisWorker.kt:17)
- [WidgetStateRepository.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/widget/WidgetStateRepository.kt:123)
- [CalorieWidgetRenderer.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt:21)

### 5. Extend The AI Request To Accept Spoken Context

Goal: let the analysis request combine image evidence with the user’s spoken context.

Changes:

- Extend `NutritionApiClient.analyzeMealImage(...)` to accept optional transcript text.
- Update `buildAnalysisRequestBody(...)` so transcript text is added as extra user input when present.
- Keep the response schema unchanged for `D1`.
- Leave the larger prompt-behavior changes from `D2` to `D4` for the next step, but make the request shape ready now.

Likely files:

- [NutritionApiClient.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/ai/NutritionApiClient.kt:39)
- [MealAnalysisWorker.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealAnalysisWorker.kt:17)

### 6. Lifecycle, Permission, And Cleanup Hardening

Goal: make the new native flow robust.

Changes:

- Add or confirm camera permission handling.
- If speech capture requires microphone permission, request it only when the user uses the microphone path.
- Define cleanup rules for:
  - backing out from live preview;
  - taking a photo then canceling;
  - retaking;
  - submitting successfully;
  - submission failure.
- Keep file ownership simple: current raw file should be replaced on retake and only preserved when needed for the saved analyzed meal.

Likely files:

- [MealCaptureActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt:22)
- [AndroidManifest.xml](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/AndroidManifest.xml:1)
- [MealAnalysisWorker.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealAnalysisWorker.kt:17)

## Validation Strategy

### Camera flow

- Open add-meal from the widget and confirm the app shows an in-app camera, not the system camera app.
- Tap back/close from the live camera screen and confirm the flow exits cleanly to home/widget.
- Take a photo and confirm the UI changes to a captured-image review/send state.

### Voice flow

- Hold the microphone button, speak, release, and confirm the transcript appears.
- Hold again and confirm the transcript can be replaced or updated cleanly.
- Skip the microphone entirely and confirm send still works.

### Submission flow

- Tap send and confirm the widget enters analyzing state immediately while the UI exits.
- Confirm the final meal result still lands in the widget after background analysis.
- Confirm failure cases still map to the current widget failure behavior.

### Regression checks

- Existing midnight refresh behavior remains intact.
- Existing last-meal edit controls still behave the same after a successful scan.
- The known widget flicker issue does not worsen with the new flow.

## Risks

- CameraX integration is a larger native change than the earlier system-camera approach.
- Push-to-talk speech UX may require a more custom implementation than standard Android speech intents.
- A single-activity state machine can get messy if not organized clearly around `live preview` vs `captured review`.
- Large image preview handling must be memory-conscious.

## Recommended First Slice

Build `D1` in these internal slices:

1. CameraX live preview with capture and close.
2. Captured-image review state with send but no microphone yet.
3. Background submission through the existing worker path.
4. Press-and-hold microphone capture.
5. Transcript-aware API request.

That gives us a usable flow early, then adds the more delicate voice interaction once the camera UX is already stable.
