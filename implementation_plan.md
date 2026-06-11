# Implementation Plan

## Scope

This plan covers:

- Widget structure relayout and normalization
- Always-visible stats across widget sizes
- Reset color refresh fix
- French support for speech recognition and capture copy
- Camera reopen behavior after leaving an unsent photo preview

This plan does **not** include weight-related changes.

## Current Findings

- The current widget already uses a vertical root with a content area and a bottom actions row.
- The current visible content columns are effectively split evenly, not as `40 / 20 / 40`.
- The center no-tap area exists today as part of a separate overlayed interaction layer, not as a clearly mirrored structural content section.
- Small widget presentations currently hide some stats, especially total macros.
- Reset already updates state and calls widget refresh, but the widget background color is only regenerated during a full update, which likely explains the stale color after reset.
- The `Step` button is already implemented and should remain unchanged.
- French resources do not exist yet.
- The meal capture screen restores preview state from `savedInstanceState`, which is likely the reason an unsent preview can come back instead of reopening the live camera flow.

## Widget Relayout Proposal

HTML-style structure for approval:

```html
<widget-root style="display:flex; flex-direction:column;">
  <content-section style="display:flex; flex-direction:row; flex:1;">
    <left-section style="width:40%; display:flex; flex-direction:column;">
      <left-label>Remaining</left-label>
      <left-primary-stat>#### kcal</left-primary-stat>
      <left-secondary-stat>#### eaten / #### target</left-secondary-stat>
      <left-supporting-stat>P ##g  C ##g  F ##g</left-supporting-stat>
    </left-section>

    <middle-spacer style="width:20%;"></middle-spacer>

    <right-section style="width:40%; display:flex; flex-direction:column;">
      <right-label>Last meal</right-label>
      <right-primary-stat>Meal name</right-primary-stat>
      <right-secondary-stat>#### kcal</right-secondary-stat>
      <right-supporting-stat>P ##g  C ##g  F ##g</right-supporting-stat>
    </right-section>
  </content-section>

  <navigation-section style="display:flex; flex-direction:row;">
    <nav-button>Add meal</nav-button>
    <nav-button>Step ##</nav-button>
  </navigation-section>
</widget-root>
```

Interaction model that will sit on top of this structure:

- Left section tap zones keep calorie target adjustment behavior.
- Middle spacer stays intentionally inert to reduce accidental taps.
- Right section tap zones keep last meal adjustment behavior.
- Bottom navigation keeps `Add meal` and `Step`.

## Implementation Steps

### 1. Rebuild the widget layout around the approved structure

Files:

- `android/app/src/main/res/layout/calorie_widget.xml`

Work:

- Refactor the layout so the visual structure clearly matches:
  - root column
  - top content row
  - bottom navigation row
- Change the content row weights to `40 / 20 / 40`.
- Keep the center section empty and intentionally non-interactive.
- Preserve the bottom row buttons and the existing `Step` control.
- Keep adjustment zones, but align them with the normalized left and right sections instead of the current looser overlay arrangement.

### 2. Normalize section layout so all content remains visible

Files:

- `android/app/src/main/res/layout/calorie_widget.xml`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt`

Work:

- Normalize both left and right sections so each has:
  - label
  - primary stat
  - secondary stat
  - supporting stat
- Use autosizing and tighter presentation rules rather than hiding stats.
- Review vertical spacing, max lines, and text sizes for compact, medium, and large widget buckets.
- Remove current logic that hides total macros for smaller widget sizes.

### 3. Ensure stats always appear at every supported size

Files:

- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt`

Work:

- Remove the current `showTotalMacros = false` compact-mode behavior.
- Keep both left-side and right-side supporting stats visible in all widget presentations.
- Adjust presentation presets so compact widgets shrink text and spacing before dropping information.
- Recheck last meal label, meal name, calories, and macro text for truncation risk.

### 4. Fix stale widget colors after reset

Files:

- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/bridge/SculptSettingsModule.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetProvider.kt`

Work:

- Force a full widget redraw after reset so the background bitmap is regenerated.
- Verify that the reset path from the React Native settings screen and the reset path from widget actions both trigger the same full visual refresh behavior.
- Confirm that the progress-derived accent color updates immediately when `caloriesConsumedToday` returns to `0`.

### 6. Add French support for meal capture speech flow

Files:

- `android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/values-fr/strings.xml`

Work:

- Update the speech recognizer configuration to support French instead of english.

## Verification Checklist

- Widget layout visually matches the approved `40 / 20 / 40` structure.
- Left and right sections feel normalized and balanced.
- All stats remain visible on compact, medium, and large widget sizes.
- Reset updates widget colors immediately.
- French voice capture works as expected.

## Open Approval Point

Please confirm the HTML-style widget structure above.

If approved, implementation should follow that structure exactly, with tap zones layered onto the left and right sections and an inert middle spacer.
