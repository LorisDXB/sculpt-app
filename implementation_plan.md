# Implementation Plan

## Goal

Redo the step counter backend so it becomes reliable without changing the current frontend layout or widget presentation.

UI constraints to preserve:

- Keep the widget center steps display exactly as it is visually
- Keep the app settings screen structure and step controls conceptually the same
- Keep the countdown/debug affordances only if still useful during the rework
- Do not change calorie, meal, or weight UI behavior

## Current Diagnosis

The current step-counter structure is fragile for structural reasons:

- It relies on `Sensor.TYPE_STEP_COUNTER` as a pull-based source
- Each refresh tries to register a listener, wait briefly, and read one instantaneous cumulative total
- The app then computes deltas from the last stored total
- This works only if the device delivers a fresh sensor callback exactly when we ask

From the logs and code, the weak points are:

- The sensor callback can return the same cached cumulative total many times in a row
- The system may batch or delay step-counter updates depending on device/vendor behavior
- Background alarm timing can be correct while the fetched sensor value is still stale
- The repository logic is not fundamentally wrong, but it depends on a data source that is not behaving like a dependable on-demand query API

Conclusion:

- The scheduling path is now mostly working
- The accumulation math is mostly working
- The unstable part is the data acquisition model itself
- We should redesign the step source structure, not keep patching the current snapshot model

## Recommended Redesign

Recommended direction:

- Replace the current "query step counter on refresh" design with a persistent daily baseline model
- Keep using the step counter sensor if we want the lightest dependency path
- Stop treating each refresh as a fresh sensor session

Core idea:

- Maintain a dedicated step tracking component responsible for:
  - establishing a baseline cumulative total for the day
  - persisting that baseline safely
  - reading the latest available cumulative total
  - deriving today's steps from `latestTotal - baseline`
- The widget and app should consume a stable step state object instead of owning the sensor logic directly

## Target Architecture

### 1. Introduce a dedicated step domain layer

New package:

- `android/app/src/main/java/com/poissoncassant/sculptapp/steps/...`

Responsibilities:

- Own all sensor access
- Own daily baseline persistence
- Own scheduling metadata
- Return a structured step snapshot to callers

Recommended classes:

- `StepTrackingRepository`
- `StepBaselineStore`
- `StepRefreshCoordinator`
- `StepSensorGateway`

This separates:

- sensor concerns
- persisted state concerns
- scheduling concerns
- widget rendering concerns

### 2. Replace "previous sensor total" with "day baseline"

Current model:

- compare current sensor total against previous refresh total

Proposed model:

- store one baseline cumulative total per day
- compute:
  - `todaySteps = max(0, currentTotal - baselineTotal)`

Why this is better:

- no need to trust every intermediate refresh
- no cumulative drift from repeated delta application
- easier to reason about day reset
- easier to debug because there is one source of truth

Persisted fields should become:

- `step_day_date`
- `step_day_baseline_total`
- `step_last_seen_total`
- `step_last_updated_at`
- `step_status`

Optional:

- `step_last_successful_refresh_at`
- `step_last_refresh_error`

### 3. Make reset/day rollover baseline-driven

On day rollover or manual reset:

- create a new day record
- keep displayed steps at `0`
- mark baseline as needing initialization

On next successful sensor read:

- if no baseline exists for the current day:
  - set baseline to current cumulative sensor total
  - set displayed steps to `0`
- otherwise:
  - compute `currentTotal - baseline`

This avoids:

- repeated baseline confusion
- lost state caused by partially cleared keys
- over-dependence on the exact moment reset occurs

### 4. Move all sensor reads behind one gateway

Current issue:

- sensor reading is spread across refresh flows and repository logic assumptions

Plan:

- one `StepSensorGateway` becomes the only place allowed to touch `SensorManager`
- it returns:
  - latest total
  - sensor metadata if needed
  - failure reason if unavailable

This gateway should:

- use a dedicated looper-backed thread
- centralize timeout behavior
- centralize debug logs
- avoid leaking sensor-thread details into widget code

### 5. Add reliability state, not just permission state

Current step status is too simple:

- `READY`
- `PERMISSION_REQUIRED`
- `SENSOR_UNAVAILABLE`

We need richer operational states:

- `READY`
- `PERMISSION_REQUIRED`
- `SENSOR_UNAVAILABLE`
- `BASELINE_PENDING`
- `STALE_READING`
- `READ_FAILED`

Why:

- a sensor can exist and still not provide a useful updated value
- the UI can still remain unchanged while internal state becomes much easier to debug

The frontend can still map these to the same current text if we want no visual change.

### 6. Redo scheduler ownership

Current issue:

- scheduling logic and step logic are still coupled through widget refresh paths

Plan:

- let a `StepRefreshCoordinator` own:
  - next refresh time
  - forced reschedule
  - test-mode intervals
  - debug logging for trigger/failure/success

The widget should not be the place that conceptually owns steps scheduling.

Recommended flow:

1. scheduler fires
2. coordinator runs step refresh
3. coordinator writes refreshed step snapshot
4. widget redraw happens after step state is updated

This keeps the widget as a consumer, not the orchestrator.

### 7. Keep frontend unchanged, but simplify what it reads

The app UI should keep showing:

- current steps
- refresh frequency
- next automatic refresh countdown
- manual refresh button

But the app should read these from a cleaner native shape:

- `todaySteps`
- `stepStatus`
- `stepPollingSeconds`
- `nextStepRefreshAtMillis`
- `lastSuccessfulStepRefreshAtMillis`

This avoids app logic trying to infer backend state from partial fields.

## Implementation Steps

### Step 1. Create a proper step repository

Files:

- new files under `steps/`
- remove step storage logic from `WidgetStateRepository` where possible

Work:

- create a repository that exposes:
  - `getCurrentStepSnapshot()`
  - `refreshCurrentStepSnapshot()`
  - `resetForNewDay()`

### Step 2. Replace delta accumulation with baseline math

Files:

- step repository/store files
- `WidgetStateRepository.kt`

Work:

- remove dependence on `KEY_STEP_LAST_SENSOR_TOTAL` as the main daily counter source
- store a per-day baseline total
- derive today's steps from `currentTotal - baseline`

### Step 3. Refactor widget state to consume step snapshot only

Files:

- `CalorieWidgetState.kt`
- `WidgetStateRepository.kt`

Work:

- make widget state read a stable step snapshot from the new step repository
- stop mixing step-refresh mechanics directly into widget-state persistence

### Step 4. Refactor scheduling around the step coordinator

Files:

- `WidgetRefreshScheduler.kt`
- new step coordinator files
- `CalorieWidgetProvider.kt`

Work:

- keep the same visible timer feature if useful
- move all step refresh trigger handling into the step coordinator
- keep widget redraw as a separate final step

### Step 5. Preserve test mode

Files:

- config repository
- settings bridge
- app settings UI

Work:

- keep `30s` as a debug/testing interval
- keep standard real options:
  - `15m`
  - `30m`
  - `60m`
  - `120m`

### Step 6. Add stronger debugging hooks

Files:

- step repository
- sensor gateway
- scheduler/coordinator

Logs to keep:

- schedule created
- schedule fired
- sensor read started
- sensor read returned total
- baseline initialized
- today steps derived
- widget redraw triggered

Optional debug fields in app settings:

- last raw cumulative total
- current day baseline total
- last successful refresh timestamp

These can remain internal or temporary if we do not want permanent UI additions.

### Step 7. Verification pass

Test cases:

1. Manual refresh twice without movement:
   - raw total same
   - displayed steps unchanged

2. Manual refresh with real walking between refreshes:
   - raw total increases
   - displayed steps increases accordingly

3. Automatic refresh with `30s` interval:
   - timer counts down
   - timer restarts
   - displayed steps update when raw total changes

4. Day reset:
   - steps display resets to `0`
   - new day baseline is initialized correctly

5. Permission missing:
   - status handled gracefully

6. Widget redraw:
   - no frontend layout change

## Files Likely To Change

- `android/app/src/main/java/com/poissoncassant/sculptapp/steps/StepSnapshotReader.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/steps/StepTrackingSupport.kt`
- new step repository/store/coordinator files under `steps/`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/WidgetStateRepository.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetState.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/WidgetRefreshScheduler.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetProvider.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/bridge/SculptSettingsModule.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/config/AppConfigRepository.kt`
- `App.tsx`

## Non-Goals

For this rework, do not:

- change the visual placement of the steps UI
- convert steps into calories
- change the weight panel UI
- redesign the app settings page visually

## Recommended Next Move

Recommended implementation order:

1. baseline-based step repository
2. sensor gateway cleanup
3. scheduler/coordinator cleanup
4. bridge/app countdown hookup
5. verification with `30s` test mode

This gives us the best chance of fixing the real structural issue without disturbing the frontend.
