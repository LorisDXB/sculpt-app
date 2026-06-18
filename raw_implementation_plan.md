# Milestone C — Widget UX and Reliability

## Task C1 — Add loading state while waiting for AI response

### Current behavior

After submitting a meal to the AI, a grey screen appears while waiting for the response. Once the response is received, the user is redirected back to the home screen.

### Desired behavior

After submitting a meal:

* The redirection to the home screen should happen immediately.
* The Last Meal section should enter a loading state.
* Replace the Last Meal contents with:

  * a spinner/loading indicator;
  * an "Analyzing..." message.

### Acceptance criteria

* No grey screen is shown.
* Returning to the home screen is immediate.
* The user clearly sees that analysis is in progress.
* Once the response arrives, the loading state disappears automatically and the Last Meal information is displayed.

---

## Task C2 — Fix tap areas

### Current behavior

Increase and decrease areas are split:

* top 50%;
* bottom 50%.

This causes accidental presses.

### Desired behavior

Split the interaction zone vertically into:

* Top 40% → increase.
* Middle 20% → no action.
* Bottom 40% → decrease.

### Acceptance criteria

* Touches in the middle zone do nothing.
* The app should not open when touching the middle zone.
* Increase/decrease interactions become less error-prone.

---

## Task C3 — Fix macro overlap

### Current behavior

Large calorie values cause the macro values underneath to overlap with the bottom-left "-" button.

### Desired behavior

Ensure:

* macros never overlap buttons;
* the widget remains readable for large values.

Possible solutions:

* smaller macro text;
* dynamic spacing.

### Acceptance criteria

* No overlapping elements.
* All controls remain visible.

---

## Task C4 — Make widget responsive

### Current behavior

The layout is optimized for a single widget size.

### Desired behavior

Widget should adapt correctly to:

* 4×2;
* 4×3;
* larger widget sizes;
* different screen sizes.

Text sizes and spacing should scale appropriately.

### Acceptance criteria

* No clipping.
* No overlap.
* No unexpected UI placement.
* Layout remains balanced.

---

## Task C5 — Automatic daily reset refresh

### Current behavior

The reset logic already exists, but crossing midnight does not refresh the widget.

Values only appear reset after rebooting or manually refreshing.

### Desired behavior

When the date changes:

* calories consumed should reset;
* calories remaining should reset;
* last meal state should update;
* the widget should refresh automatically.

Implementation requirements:

1. Date check during every widget update.
2. Scheduled midnight refresh.
3. Do not rely on device reboot.

### Acceptance criteria

* Reboot is unnecessary.
* Widget refreshes correctly after midnight.
* Reset values become visible automatically.

---

# Milestone D — AI Accuracy Improvements

## Task D1 — Add speech-to-text context

### Goal

Allow the user to provide optional context before sending the image to the AI.

### Flow

1. User captures a meal.
2. On the confirmation screen, display a microphone button to the right of the confirmation button.
3. Pressing the microphone starts speech-to-text.
4. User may say:

```text
I only ate half.
This contains olive oil.
Two servings.
160 grams of chicken.
```

5. The transcript is automatically included in the AI prompt.
6. No additional confirmation step is required.

### Acceptance criteria

* Speech input is optional.
* Transcript is included in the prompt.
* Image-only flow still works when the microphone button is not used.

---

## Task D2 — Improve prompt for multiple foods

### Goal

Assume that all visible foods are being consumed.

Examples:

* sandwich + fries;
* drink + burger;
* multiple plates.

### Acceptance criteria

The AI estimates the entire visible meal.

---

## Task D3 — Improve prompt for boxes and containers

### Goal

If food appears inside a container or package, assume the entire contents are consumed unless the user specifies otherwise through speech-to-text.

Examples:

* sushi box;
* takeout container;
* meal-prep box.

### Acceptance criteria

Calories represent the full contents of the container.

---

## Task D4 — Use packaging information

### Goal

Extract useful information from packaging.

Examples:

* 160 g chicken;
* 500 ml drink;
* visible serving size.

Use these values to refine estimates.

### Acceptance criteria

Visible quantities influence calorie and macro estimation.

---

## Task D5 — Use visible brands

### Goal

If a recognizable brand is visible, use it to improve estimation accuracy.

Example:

Current:

```text
Beer can
```

Desired:

```text
Heineken
Corona Extra
Monster Energy Ultra
```

### Acceptance criteria

Brand recognition improves calorie and macro accuracy.

---

# Milestone E — Weight Tracking

## Task E1 — Daily weight tracking

### Goal

Allow the user to log weight daily.

### Empty state

If no meals have been entered today, display:

```text
Enter today's weight
```

### After entering weight

Show weekly statistics:

* weight gained;
* weight lost.

### Acceptance criteria

* Weight history is stored locally.
* Weekly gain/loss statistics are available.
* Weight tracking works independently from meal logging.
