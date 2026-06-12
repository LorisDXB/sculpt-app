# Widget Weight Entry Implementation Plan

## Goal

Add a weight-entry mode to the home screen widget for days where no meal has been logged yet.

When there is no meal for the current day:

- the right side of the widget should show today's weight instead of meal macros
- the user can select a digit in the displayed weight
- the `+` and `-` touch zones adjust only the selected digit
- digit updates always move by exactly `1`
- digit updates wrap on overflow:
  - `9 -> 0`
  - `0 -> 9`
- the existing `Step` button is ignored for weight editing

The weight must be stored by date so it can be used later for graphing.

Each new day should start by copying yesterday's weight into today's entry so the user can quickly tweak it.

## Interaction Model

### Empty-day widget mode

If `lastMeal == null`, the widget enters weight-entry mode on the right side.

Displayed content:

- today's weight, formatted with one decimal place, for example: `90.6 kg`
- a visible selected digit within the weight
- two trend stats below the weight

### Digit selection

The user taps a specific displayed digit to select it.

Example:

- in `90.6 kg`, selectable digits are `9`, `0`, and `6`
- the decimal separator is not editable
- the selected digit should be visually emphasized

### Digit editing

Pressing the widget's existing `+` or `-` zones:

- changes only the currently selected digit
- changes it by exactly `1`
- wraps within `0..9`

Examples:

- selected `9` in `90.6` and press `+` -> `00.6`
- selected `0` in `90.6` and press `-` -> `99.6`
- selected `6` in `90.6` and press `+` -> `90.7`

The existing calorie `Step` button remains part of the widget for other modes, but it has no effect while in weight-entry mode.

## Data Model Changes

Extend widget persistence in `WidgetStateRepository` to store:

- current-day weight
- selected editable digit index for weight mode
- weight history by day

Recommended storage shape:

- keep the existing daily widget state
- add a simple date-keyed weight history structure in preferences

Possible fields:

- `current_weight_tenths` for today's weight stored as an integer in tenths
- `selected_weight_digit_index`
- `weight_history_<date>` entries or a compact serialized map

Using tenths avoids float precision issues and naturally supports exactly one decimal place.

## Day Rollover Behavior

On day rollover:

1. detect the new date in `WidgetStateRepository.readState()`
2. reset meal-specific daily state exactly as today
3. carry forward yesterday's weight into today's weight
4. preserve weight history
5. reset the selected digit to a default position

Recommended default selected digit:

- the decimal digit, or
- the rightmost integer digit

The final choice can be made during implementation based on which feels better in the widget layout.

## Repository Logic

Add repository helpers for:

- reading today's weight
- formatting weight for display
- selecting a weight digit
- increasing selected digit
- decreasing selected digit
- computing weight trend stats

Digit editing should operate on a normalized string representation so each editable digit can be updated predictably.

Recommended internal model:

- store weight as tenths, for example `906` means `90.6`
- convert to a fixed display form before editing
- map display digits back to numeric places

## Widget Provider Actions

Add new widget broadcast actions for:

- selecting the hundreds / tens / ones / tenths digit as needed by the chosen format
- increasing selected weight digit
- decreasing selected weight digit

Existing actions for last meal adjustment should keep working when a meal exists.

When no meal exists:

- the right-side action zones should route to weight editing instead of last-meal editing

## Widget UI Changes

Update `calorie_widget.xml` and `CalorieWidgetRenderer` so that the right side can render two modes:

### Meal mode

Current behavior remains unchanged when a meal exists:

- last meal label
- last meal calories
- macro summary

### Weight mode

When no meal exists:

- show a `Weight` label
- show formatted weight with one selected digit emphasized
- show two trend stats underneath
- wire digit taps to selection actions
- wire `+ / -` zones to weight digit editing actions

Because `RemoteViews` has styling limits, selected-digit emphasis may need to be implemented using:

- multiple `TextView`s for separate digits, or
- a small fixed layout for integer digits + decimal digit

The implementation should prefer predictable tap targets and readability over clever formatting tricks.

## Trend Stats

Show two compact trend stats under the weight.

Recommended stats:

1. `vs yesterday`
2. `7d trend`

Possible formats:

- `-0.4 vs yday`
- `+0.8 / 7d`

These are useful immediately and align with the future graph feature.

## Validation and Testing

Verify the following:

1. If no meal is logged today, weight mode appears on the right side.
2. If a meal exists, the existing meal/macro UI still appears.
3. Tapping a digit selects it correctly.
4. `+` and `-` update only the selected digit.
5. Digit changes wrap correctly from `9` to `0` and `0` to `9`.
6. Weight always renders with one decimal place.
7. Weight persists across widget refreshes and reboot.
8. A new day starts with yesterday's weight copied forward.
9. Trend stats update from stored history correctly.
10. Small and large widget sizes still render cleanly.

## Expected Files To Change

- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetState.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/WidgetStateRepository.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetProvider.kt`
- `android/app/src/main/java/com/poissoncassant/sculptapp/widget/CalorieWidgetRenderer.kt`
- `android/app/src/main/res/layout/calorie_widget.xml`
- `android/app/src/main/res/values/strings.xml`

## Implementation Strategy

Suggested order:

1. extend state and persistence for daily weight history
2. add repository editing helpers and trend calculations
3. add provider actions for weight selection and digit editing
4. update renderer and layout for weight mode
5. validate rollover, persistence, and widget interactions on device

