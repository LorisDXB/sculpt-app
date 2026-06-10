# Open Issues

## Widget

### Widget flicker / disappear-reappear during meal analysis transitions

- Status: partially improved, not fully fixed.
- Current behavior: the home-screen widget sometimes disappears and reappears briefly when switching into the analyzing state and sometimes shortly after the analyzed result is shown.
- Important clarification: the remaining issue is the whole widget flickering, not just text inside it changing.
- What was tried:
  - moved meal analysis off the activity thread into a background worker
  - added partial widget updates for analysis-state transitions
  - reverted an attempted suppression of system `APPWIDGET_UPDATE` broadcasts because it caused regressions
- Current conclusion: the flow is usable, but the visual refresh is still not perfectly stable.

### Last meal macros stay bottom-anchored on larger widget sizes

- Status: known layout compromise, not fully fixed.
- Current behavior: on the right-side "Last meal" section, the macro line can stay anchored lower than desired when the widget is scaled up, instead of naturally staying tucked right under the meal name and calorie value like the "Remaining" side.
- Context: this version was kept because it preserved macro visibility on tighter sizes such as `4x2`, where previous layout attempts clipped or hid the macro text.
