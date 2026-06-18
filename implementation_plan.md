# Implementation Plan

## Scope

This plan covers:

- Showing today's steps in the top middle section of the widget
- Fetching step count automatically from the device
- Refreshing step data every 30 minutes when possible
- Letting the user choose the step refresh interval
- Keeping battery impact limited
- Preserving the current calorie, meal, and weight widget behavior

This plan assumes:

- Steps are display-only for now
- The widget should show today's live step count, not a manually entered step value
- The default refresh cadence can be 30 minutes instead of real-time updates

## Current Findings

- The widget already has a real center visual column in `calorie_widget.xml`.
- That center area is currently structurally present but visually unused.
- The middle touch zone is currently inert, which is compatible with a steps display.
- Widget data is currently driven by `WidgetStateRepository` and rendered by `CalorieWidgetRenderer`.
- The app does not currently integrate with Health Connect, Google Fit, or direct step sensors.

## Recommended Data Source

Recommended source:

- Android `Health Connect`

Why:

- It is the cleanest modern path for reading daily step totals
- It is better suited than trying to keep a raw sensor listener alive for widget-only refreshes
- It supports querying today's aggregate steps rather than maintaining our own pedometer session state

Important note:

- This requires user permission and device support
- Some devices may not have Health Connect installed or available
- We need fallback behavior when permission is missing or the source is unavailable

## Widget Structure Proposal

HTML-style structure with steps added:

```html
<widget-root style="display:flex; flex-direction:column;">
  <content-section style="display:flex; flex-direction:row; flex:1;">
    <left-section style="width:40%; display:flex; flex-direction:column;">
      <left-label>Remaining</left-label>
      <left-primary-stat>#### kcal</left-primary-stat>
      <left-secondary-stat>#### eaten / #### target</left-secondary-stat>
      <left-supporting-stat>P ##g  C ##g  F ##g</left-supporting-stat>
    </left-section>

    <middle-section style="width:20%; display:flex; flex-direction:column; align-items:center;">
      <middle-label>Steps</middle-label>
      <middle-primary-stat>####</middle-primary-stat>
    </middle-section>

    <right-section style="width:40%; display:flex; flex-direction:column;">
      <right-label>Weight or Last meal</right-label>
      <right-content>Existing right panel behavior</right-content>
    </right-section>
  </content-section>

  <navigation-section style="display:flex; flex-direction:row;">
    <nav-button>Add meal</nav-button>
    <nav-button>Step ## or hidden in weight mode</nav-button>
  </navigation-section>
</widget-root>
```

Behavior:

- The middle section becomes visual steps display only
- The middle tap zone remains inert unless a later feature adds interactions
- Left and right panel behaviors stay unchanged

## Refresh Strategy

Target behavior:

- Refresh steps on a user-configurable interval
- Default to 30 minutes

Recommended implementation:

- Use `WorkManager` or `AlarmManager`-driven scheduled refresh
- On each refresh:
  - query today's steps
  - persist them into widget state
  - refresh the widget UI

Configurable polling:

- Add a user setting for step refresh frequency
- Store the selected interval in app config
- Rebuild or reschedule background refresh when the interval changes
- Use safe predefined options rather than arbitrary free text

Constraints:

- Exact 30-minute timing may not be guaranteed by Android in all battery modes
- `WorkManager` periodic work has platform constraints and can be deferred
- If strict 30-minute cadence is not guaranteed, we should aim for “approximately every 30 minutes”
- Some user-selected intervals may need to be clamped to what Android allows efficiently

Best practical interpretation:

- schedule periodic refresh at the closest battery-safe cadence Android allows
- trigger opportunistic refresh when the widget is updated manually or the app opens

## Implementation Steps

### 1. Add step data to widget state

Files:

- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetState.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/WidgetStateRepository.kt`

Work:

- Extend widget state with step-related fields:
  - today's step count
  - last step refresh timestamp
  - step source availability state
- Persist the latest fetched steps in widget storage.
- Preserve step data across widget redraws and day rollover.
- Reset the daily step total only when the date changes and a new fetch occurs.

### 2. Add a step data provider abstraction

Files:

- new Android Kotlin files under a dedicated package, for example:
  - `android/app/src/main/java/com/poissoncassant/sculptapp/steps/...`

Work:

- Create a small abstraction layer for reading today's steps.
- Keep widget code decoupled from the specific Android provider.
- Return a structured result:
  - success with step count
  - unavailable
  - permission missing
  - error

Why:

- This keeps the renderer and repository simple
- It allows switching implementation later if Health Connect is replaced or augmented

### 3. Integrate Health Connect for today's steps

Files:

- new Health Connect integration files under `android/app/src/main/java/com/poissoncassant/sculptapp/steps/...`
- `android/app/src/main/AndroidManifest.xml`

Work:

- Add Health Connect dependency and integration code.
- Query aggregate step count for the current local day.
- Handle:
  - Health Connect availability
  - permission checks
  - read permission flow
- Return today's step total to the widget state layer.

### 4. Add refresh scheduling every 30 minutes

Files:

- new scheduler/worker files under a package like:
  - `android/app/src/main/java/com/poissoncassant/sculptapp/steps/...`
- possibly existing widget scheduling files if reused

Work:

- Schedule recurring refresh work using the user-selected polling interval.
- On each scheduled execution:
  - fetch today's steps
  - store them in widget state
  - refresh the widget
- Reschedule appropriately after reboot and when the widget is added.

Implementation note:

- If exact periodic execution is not possible under the selected scheduler, use the nearest battery-safe allowed periodic model and document that behavior.

### 5. Add user-configurable polling settings

Files:

- `android/app/src/main/java/com/poissoncassant/sculptapp/config/AppConfigRepository.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/bridge/SculptSettingsModule.kt`
- `App.tsx`
- step scheduler files

Work:

- Add a persisted step refresh interval setting.
- Expose it through the native settings bridge.
- Add a small app setting UI for choosing the refresh cadence.
- Recommended options:
  - 15 minutes
  - 30 minutes
  - 60 minutes
  - maybe 120 minutes
- When the user changes the interval:
  - persist the value
  - reschedule future step refresh work
  - optionally trigger one immediate refresh

### 6. Add fallback behavior when steps cannot be fetched

Files:

- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt`
- step provider files
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/values-fr/strings.xml`

Work:

- Define display states for:
  - steps available
  - permission missing
  - source unavailable
  - fetch failed
- Keep fallback copy short and widget-friendly.
- Avoid cluttering the widget if step data is unavailable.

### 7. Render steps in the top middle widget section

Files:

- `android/app/src/main/res/layout/calorie_widget.xml`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt`

Work:

- Add a visible steps block in the center column:
  - label: `Steps`
  - primary value: today's step count
- Align it visually to the top middle.
- Keep the surrounding inert center tap area intact.
- Make sure the steps display scales cleanly on compact widget sizes.

### 8. Trigger step refresh from useful lifecycle points

Files:

- widget provider and any app entry points as needed

Work:

- Refresh steps when:
  - scheduled 30-minute refresh fires
  - widget updates normally
  - app opens, if appropriate
- This gives better freshness without needing aggressive background polling.

### 9. Add onboarding or permission entry point in the app

Files:

- `App.tsx`
- native bridge files if needed

Work:

- Add a lightweight status/entry point in the app so the user can:
  - see whether steps integration is connected
  - grant permission if needed
- Keep this simple unless a larger health settings area is desired later.

## Verification Checklist

- The widget shows today's steps in the top middle section.
- Left and right panels still behave correctly.
- Weight mode still hides the step button and expands `Add meal`.
- Step data persists across widget redraws.
- Step refresh runs at the user-selected cadence.
- Changing the polling interval reschedules future refreshes correctly.
- Battery impact remains limited because no continuous sensor listener is kept alive.
- Missing permission is handled gracefully.
- Unavailable Health Connect state is handled gracefully.
- Day rollover resets the daily step interpretation correctly.

## Risks and Constraints

- Exact every-30-minute execution may not always be guaranteed by Android background scheduling rules.
- Health Connect availability varies across devices.
- Permissions add UX work outside the widget itself.
- Widget UI space is tight, especially on compact sizes, so the steps block must stay concise.

## Recommended First Cut

For the first implementation:

- use Health Connect
- show steps as display-only
- default refresh to 30 minutes
- allow the user to choose the polling interval from a small list of safe options
- add a minimal app-side permission/status entry point
- keep the center zone non-interactive

This gives the most value with the least widget complexity.
