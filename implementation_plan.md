# Implementation Plan

## Verdict

The raw plan is plausible overall, but it needs a few adjustments to match the current codebase:

- `C1` is partly already implemented. The widget already has `ANALYZING` and `ERROR` states in [WidgetStateRepository.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/widget/WidgetStateRepository.kt:141) and [CalorieWidgetRenderer.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt:71). The real gap is removing the blocking in-app capture flow so the user returns immediately after taking a photo.
- `C2` to `C4` are straightforward widget layout and interaction refinements in the existing native Android widget.
- `C5` is plausible, but the repository already performs date-based reset on read in [WidgetStateRepository.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/widget/WidgetStateRepository.kt:14). The missing piece is a reliable scheduled refresh around midnight, not the reset logic itself.
- `D1` is the only materially larger feature. The current camera flow is one-shot in [MealCaptureActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt:22) and has no confirmation UI, no speech recognition, and no transcript storage. This should be treated as a small capture-flow refactor, not a prompt tweak.
- `D2` to `D4` are plausible prompt/request upgrades inside [NutritionApiClient.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/ai/NutritionApiClient.kt:39), but they depend on `D1` if we want speech context available.

## Current Baseline

- The React Native app is mainly a settings/control panel in [App.tsx](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/App.tsx:1).
- The user enters meal capture from the widget via [AddMealEntryActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/AddMealEntryActivity.kt:11), which immediately forwards into [MealCaptureActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt:22).
- After the photo returns, `MealCaptureActivity` starts analysis, updates widget state to `ANALYZING`, and keeps the activity alive until success or failure in [MealCaptureActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt:113).
- The widget layout is fully native and fixed in [calorie_widget.xml](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/res/layout/calorie_widget.xml:1), with equal 50/50 tap zones for increase and decrease.

## Milestone C

### C1. Immediate return after capture with visible widget loading state

Goal: keep the existing widget `ANALYZING` state, but stop making the user wait inside `MealCaptureActivity`.

Planned changes:

- Split capture from analysis so that once a valid photo is captured and the widget state is marked `ANALYZING`, the UI flow can close immediately.
- Move analysis work out of the visible activity lifecycle.
- Keep success and failure writing through `WidgetStateRepository` and `CalorieWidgetRenderer.refreshAll(...)`.
- Improve the widget loading presentation so it feels intentional, not like a half-empty last-meal panel.

Likely files:

- [MealCaptureActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt:22)
- [WidgetStateRepository.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/widget/WidgetStateRepository.kt:141)
- [CalorieWidgetRenderer.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt:71)
- [strings.xml](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/res/values/strings.xml:1)

Notes:

- The raw plan says "redirect back to the home screen immediately." In this codebase, that effectively means "finish the native capture activity immediately and let the user land back on launcher/home while the widget updates."
- A true spinner inside an Android home-screen widget is limited. We should plan for the best RemoteViews-friendly version: progress indicator if supported by target API, otherwise animated-feeling static loading state copy and layout.

### C2. Safer tap zones for calorie adjustment

Goal: change the 50/50 split to 40/20/40, with a dead zone in the middle.

Planned changes:

- Refactor the left and right overlay columns in [calorie_widget.xml](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/res/layout/calorie_widget.xml:124) into three vertical regions instead of two.
- Keep the root no-op tap handler so the dead zone does not launch the app accidentally.
- Verify increase and decrease actions still map to the correct `PendingIntent`s.

Likely files:

- [calorie_widget.xml](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/res/layout/calorie_widget.xml:124)
- [CalorieWidgetRenderer.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt:119)

### C3. Prevent macro/button overlap

Goal: keep large calorie and macro text readable without colliding with the bottom controls.

Planned changes:

- Reduce coupling between content height and bottom controls by giving the top content area stricter bounds.
- Tune text sizes, margins, and bottom padding in the right-side last-meal column.
- Add truncation or alternate text treatment for very long meal names and large values where needed.

Likely files:

- [calorie_widget.xml](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/res/layout/calorie_widget.xml:76)
- [CalorieWidgetRenderer.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt:90)

### C4. Make the widget responsive across supported sizes

Goal: improve rendering for `4x2`, `4x3`, and larger sizes without clipping or awkward spacing.

Planned changes:

- Use widget size options from `AppWidgetManager` inside the renderer to derive a compact vs regular vs expanded presentation.
- Adjust typography, paddings, visibility of secondary text, and bottom controls per size tier.
- Keep the existing single layout if possible, but allow size-specific presentation logic from the renderer.
- If one layout becomes too brittle, introduce a second compact or expanded layout resource.

Likely files:

- [CalorieWidgetRenderer.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt:21)
- [calorie_widget.xml](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/res/layout/calorie_widget.xml:1)
- [calorie_widget_info.xml](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/res/xml/calorie_widget_info.xml:1)

### C5. Reliable refresh after midnight

Goal: preserve the existing reset-on-read behavior, but ensure the widget visibly refreshes soon after date rollover.

Planned changes:

- Keep the current date check in `readState()` as the source of truth.
- Add a scheduled alarm for the next local midnight plus a small buffer.
- Re-schedule that alarm after boot and after each widget update or state mutation.
- Route the alarm receiver through the existing refresh path.

Likely files:

- [WidgetStateRepository.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/widget/WidgetStateRepository.kt:14)
- [CalorieWidgetProvider.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetProvider.kt:8)
- [AndroidManifest.xml](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/AndroidManifest.xml:1)

Notes:

- `DATE_CHANGED` is already listened for, but on modern Android we should not rely on broadcast timing alone for widget freshness.

## Milestone D

### D1. Add optional speech context before analysis

Goal: allow the user to capture a photo, review it, optionally dictate extra context, then submit for analysis.

Planned changes:

- Refactor the capture flow from "camera result immediately triggers analysis" into "camera result opens a confirmation/review step."
- Add a native confirmation screen or activity with:
  - captured image preview;
  - confirm/send action;
  - optional microphone action;
  - transcript preview text.
- Use Android speech recognition APIs for a lightweight first pass.
- Persist the optional transcript only long enough to build the request.
- On send, mark the widget as `ANALYZING`, hand work off to background processing, and close the UI immediately.

Likely files:

- [MealCaptureActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt:22)
- New native capture-review UI files under `android/app/src/main/java/com/poissoncassant/sculptapp/camera/`
- New layout resources under `android/app/src/main/res/layout/`
- [AndroidManifest.xml](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/AndroidManifest.xml:1)
- [NutritionApiClient.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/ai/NutritionApiClient.kt:39)

Notes:

- This is the largest task because the current flow has no review screen at all.
- We should use an Android-native review step rather than trying to route this through React Native, because the existing meal flow is already native.

### D2. Update prompt to assume all visible foods are consumed

Goal: make multi-item meals estimate the full visible meal instead of a probable subset.

Planned changes:

- Expand the prompt instructions in `buildAnalysisRequestBody(...)`.
- Add examples that explicitly cover multiple foods, drinks, sides, and separate containers on the same image.

Likely files:

- [NutritionApiClient.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/ai/NutritionApiClient.kt:60)

### D3. Update prompt for boxed or packaged meals

Goal: treat visible containers as whole servings unless the user says otherwise.

Planned changes:

- Extend prompt instructions so containers, meal-prep trays, sushi boxes, and takeout packaging are interpreted as intended full contents.
- Add an override rule: if transcript context says half, one serving, shared meal, or leftovers, that user context wins.

Likely files:

- [NutritionApiClient.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/ai/NutritionApiClient.kt:60)

### D4. Use packaging text and visible quantity clues

Goal: leverage any visible weights, volumes, or serving statements to tighten estimates.

Planned changes:

- Strengthen the prompt to extract package sizes and visible nutrition cues when legible.
- Pass transcript context alongside the image so spoken details and packaging text can combine.
- Keep the current JSON schema unless we decide later to expose an explanation/debug field.

Likely files:

- [NutritionApiClient.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/ai/NutritionApiClient.kt:60)

## Recommended Build Order

1. `C2`, `C3`
2. `C4`
3. `C5`
4. `C1`
5. `D1`
6. `D2`, `D3`, `D4`
7. Final prompt polish after `D1` lands so transcript-aware rules are fully aligned

Rationale:

- `C2` and `C3` are the quickest layout corrections and reduce noise before responsive work.
- `C4` should follow the base layout fixes so responsiveness is built on the corrected widget structure.
- Midnight refresh should be done before we rely even more on widget state.
- `C1` becomes cleaner once widget presentation is improved and should establish the background-analysis model before capture-flow expansion.
- `D1` is the architectural feature and should land before final prompt tuning so transcript-aware behavior is designed once instead of patched in later.

## Validation Strategy

- Manual widget checks on at least `4x2` and `4x3` sizes.
- Tap-zone testing on left and right halves, especially center dead zones.
- Large-value test cases: high calorie totals, long meal names, long macro lines.
- Midnight simulation by forcing stored date and triggering refresh paths.
- Capture-flow testing for:
  - success;
  - no food;
  - network failure;
  - invalid API key;
  - activity closed immediately after submission.
- Speech-context testing with phrases such as:
  - `I only ate half`
  - `Two servings`
  - `160 grams of chicken`
  - `This includes olive oil`

## Risks To Watch

- Android home-screen widgets have `RemoteViews` limitations, so loading-state visuals must stay simple.
- Background analysis after the UI closes needs a reliable execution model; if a plain thread tied to an activity proves fragile, we should promote the work into a service or `WorkManager`.
- Speech recognition availability varies by device and locale, so `D1` needs a graceful fallback where the image-only flow still works.
