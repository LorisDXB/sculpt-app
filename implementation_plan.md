# Implementation Plan

## Scope

This plan covers:

- Weight tracking in the widget right panel
- Digit-based weight editing before any meals are logged for the day
- Fallback display using yesterday's weight or the app default weight
- Weight comparisons versus yesterday and last week
- App settings support for a default weight

This plan assumes:

- The right panel shows weight tracking only when no meals have been entered today
- Once a meal exists for the day, the right panel returns to the normal last-meal display and weight editing is no longer accessible
- Only numeric digits are editable
- Editable digits wrap from `9 -> 0` and `0 -> 9`

## Current Findings

- Weight tracking is not implemented yet.
- The widget state currently persists calorie, macro, analysis, and last-meal data in `WidgetStateRepository`.
- The app config currently stores API-key-related settings only in `AppConfigRepository`.
- The widget right panel currently renders:
  - analysis state
  - error state
  - empty no-meal state
  - last meal details
- The current empty no-meal state is the place where weight tracking should appear.

## Widget Behavior Rules

### Right Panel Mode

- If **no meals were entered today**:
  - the right panel shows weight tracking
  - the user can select a digit and edit the weight
- If **at least one meal was entered today**:
  - the right panel shows the normal last meal view
  - weight tracking is not accessible

### Weight Source Fallback

When no meals were entered today, the displayed weight should resolve in this order:

1. today's saved weight, if one exists
2. yesterday's weight, if one exists
3. the default weight from app settings

## Weight Tracking Structure Proposal

HTML-style structure for the right panel in no-meal mode:

```html
<right-section style="width:40%; display:flex; flex-direction:column;">
  <right-label>Weight</right-label>

  <weight-row>
    <digit-button>7</digit-button>
    <digit-button>2</digit-button>
    <separator>.</separator>
    <digit-button>4</digit-button>
  </weight-row>

  <comparison-line>vs yesterday: -0.3</comparison-line>
  <comparison-line>vs last week: +0.8</comparison-line>
</right-section>
```

Interaction model:

- Tapping a digit selects it
- The selected digit becomes the active edit target
- The existing right-side increase/decrease tap zones modify only the selected digit
- Digits wrap between `0` and `9`
- The decimal separator is visual only and not editable

## Implementation Steps

### 1. Add default weight to app settings

Files:

- `android/app/src/main/java/com/poissoncassant/sculptapp/config/AppConfigRepository.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/bridge/SculptSettingsModule.kt`
- `App.tsx`

Work:

- Add default weight persistence to app config storage.
- Expose default weight through the native settings bridge.
- Add a settings input in the app so the user can define the default weight.
- Keep this setting independent from calorie target and API-key settings.

### 2. Add weight history to widget state

Files:

- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetState.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/WidgetStateRepository.kt`

Work:

- Extend widget state with weight-related data:
  - today's weight
  - selected digit index
  - yesterday comparison
  - last-week comparison
- Persist daily weight history keyed by date.
- Preserve weight history across day rollover.
- Reset only meal/calorie daily values on date rollover, not historical weight entries.

### 3. Define a stable weight format

Files:

- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/WidgetStateRepository.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt`

Work:

- Choose and implement one fixed display format for the widget weight.
- Recommended internal structure:
  - store a numeric value
  - render it into fixed editable digits plus a fixed decimal separator
- Only digits are editable.
- The separator remains static and non-selectable.

Note:

- The exact digit format still needs final confirmation during implementation if not already implied by design.

### 4. Replace the current no-meal right-panel state with weight tracking

Files:

- `android/app/src/main/res/layout/calorie_widget.xml`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/values-fr/strings.xml`

Work:

- Remove the current “no meal entered” right-panel empty state.
- Render a weight panel instead when `lastMeal == null`.
- Show:
  - weight label
  - per-digit editable row
  - comparison versus yesterday
  - comparison versus last week
- Keep the existing last-meal UI untouched for days where a meal has been logged.

### 5. Add digit selection in the widget

Files:

- `android/app/src/main/res/layout/calorie_widget.xml`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetProvider.kt`

Work:

- Add clickable targets for each editable digit.
- Add provider actions for selecting each digit.
- Add visual feedback for the selected digit.
- Default to a sensible active digit if none is selected yet.

### 6. Reuse the current right-side increase/decrease zones for weight editing

Files:

- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/WidgetStateRepository.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetProvider.kt`

Work:

- Keep the existing right-side plus/minus zones.
- Change their behavior conditionally:
  - if a meal exists today, they still adjust last meal calories
  - if no meal exists today, they edit the selected weight digit instead
- Wrap digit changes across `0..9`.

### 7. Add weight comparison logic

Files:

- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/WidgetStateRepository.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt`

Work:

- Compute:
  - change versus yesterday
  - change versus last week
- Render directional copy clearly:
  - increase
  - decrease
  - unchanged
- Define fallback rendering when comparison history is missing.

### 8. Preserve correct mode transitions

Files:

- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/WidgetStateRepository.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt`

Work:

- Ensure the panel switches automatically:
  - no meal today -> weight mode
  - meal logged today -> last-meal mode
- Ensure any saved weight for the day remains persisted even after a meal is logged.
- Ensure the weight panel becomes available again on the next day before meals are entered.

## Verification Checklist

- No-meal day shows weight tracking instead of no-meal text.
- Meal-logged day shows the normal last meal panel.
- Tapping a digit selects it visually.
- Right-side plus/minus changes only the selected digit.
- Digits wrap correctly across `0` and `9`.
- Yesterday fallback works when today has no saved weight.
- Default-weight fallback works when neither today nor yesterday has a saved weight.
- Comparison values versus yesterday and last week display correctly.
- Day rollover preserves weight history while resetting daily meal/calorie state.
- Layout remains readable across compact, medium, and large widget sizes.

## Open Note

The remaining implementation detail to lock during coding is the exact visible weight format, because it determines the number of digit slots:

- `72.4`
- `072.4`
- `72,4`
- another fixed pattern

Everything else is structurally clear from the current requirements.
