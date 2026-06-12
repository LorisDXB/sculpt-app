# Widget Weight Entry Notes

## Behavior

When no meal has been logged for the current day, the widget's right side switches into weight-entry mode.

In that mode:

- today's weight is shown with one decimal place
- the user can tap a digit to select it
- the right-side `+` and `-` controls adjust only the selected digit
- each press changes that digit by exactly `1`
- digits wrap independently:
  - `9 -> 0`
  - `0 -> 9`

The calorie `Step` button is ignored in weight-entry mode and is displayed as `Weight`.

## Persistence

- weight is stored by date in shared preferences as integer tenths
- each new day starts from yesterday's most recent weight
- the selected digit is persisted as widget state

## Trend Stats

The widget currently shows:

- change vs yesterday
- change vs 7-day average

Both values are derived from stored daily weight history and are intended to support a future graph view.
