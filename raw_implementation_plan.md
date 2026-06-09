# Implementation Plan - Android Widget-First AI Calorie Tracker

## 1. Product Summary

Build an Android-only personal calorie tracker.

The product is not a normal app. The main interface is a home-screen widget. The installed app exists only because Android requires an app shell to install/configure the widget and hold permissions/settings.

Core user flow:

1. User triggers meal capture quickly.
2. Phone camera opens.
3. User takes a photo of their meal.
4. Image is sent to a cloud vision AI API.
5. AI estimates calories and macros.
6. Result is stored locally.
7. Widget updates automatically.

The widget shows:

- calories remaining for the current day;
- macros of the last logged meal;
- quick adjustment controls directly on the widget.

No account.
No cloud database.
No barcode.
No manual food search.
No Play Store distribution requirement.
Personal APK only.

## 2. Platform and Stack

### Target platform

Android only.

### Main stack

- React Native for the minimal app shell.
- Native Android widget implementation.
- Kotlin preferred for native Android code.
- Local storage only.
- Cloud AI API for image analysis.

### Important architecture constraint

React Native cannot directly render a normal interactive home-screen widget like a React Native screen.

Android widgets are rendered through Android's widget system using `RemoteViews` or a widget abstraction library. Therefore:

- React Native can manage the app shell.
- Native Android code should manage the widget.
- Shared local storage bridges the app, widget, and background processing.

Recommended options:

Option A - Safer:

- React Native app shell.
- Native Kotlin `AppWidgetProvider`.
- XML/RemoteViews widget layouts.
- Native Android services/workers for updating widget.

Option B - Faster if stable:

- React Native app shell.
- `react-native-android-widget`.
- Still validate gesture support early.
- Fall back to native Kotlin widget if limitations block the desired UX.

Preferred: start with Option A unless `react-native-android-widget` fully supports the required interactions.

## 3. Widget UX

### Minimum widget size

Use a 4x2 widget as the MVP.

Reason:

- 2x2 is too cramped for calories + macros + adjustment controls.
- 4x2 gives enough room while staying practical.
- Later, optional sizes can be added.

### Widget layout

Split into two zones:

Left side:

- daily calories remaining;
- adjustment gesture/control.

Right side:

- last meal calories;
- last meal macros:
- protein;
- carbs;
- fat.

Example:

```text
+------------------------------+
|  LEFT              RIGHT     |
|                              |
|  1450 kcal left    Last meal |
|                    620 kcal  |
|                    P 42g     |
|                    C 58g     |
|                    F 18g     |
+------------------------------+
```

### Gesture model

User wants swipe gestures on numbers.

Android widget limitation:

- horizontal swipes are unreliable because launchers use them to navigate home screens;
- vertical swipe is the only realistic swipe direction;
- taps are more reliable than gestures.

Recommended MVP interaction model:

- Use tap zones first:
- top half of a value = increase;
- bottom half of a value = decrease.
- Tap on a number cycles adjustment step:
- 10;
- 50;
- 100;
- 250.
- Macro values update proportionally when last meal calories are adjusted.

Optional later experiment:

- Vertical swipe up on calories remaining: increase remaining calories.
- Vertical swipe down on calories remaining: decrease remaining calories.
- Vertical swipe up/down on last meal calories: adjust last meal calories.

Do not depend on long press.

## 4. App UI Scope

The app should have almost no normal interface.

Required app screens only:

### First launch setup screen

Fields:

- daily calorie target;
- goal mode:
- weight loss;
- weight gain;
- maintenance optional;
- optional macro preference:
- balanced;
- high protein;
- low carb optional.

Actions:

- add widget instruction;
- grant camera permission;
- test API key if needed.

### Settings screen

Minimal:

- daily calorie target;
- API key;
- reset today;
- clear all local data;
- widget setup help.

No meal history screen.
No analytics.
No food database.
No search.
No barcode.
No account.

## 5. Capture Flow

Use the easiest reliable implementation:

### Recommended MVP capture method

Use an Android launcher shortcut / widget tap action that opens a camera capture Activity.

Flow:

1. User taps capture area on widget or uses app shortcut.
2. Native Android camera capture opens.
3. User takes photo.
4. App receives image URI.
5. Image is compressed locally.
6. Image is sent to AI API.
7. Result is parsed.
8. Local data updates.
9. Widget refreshes.

Avoid background instant photo capture for MVP. It is more complex, permission-sensitive, and less reliable.

### Capture triggers

Implement in this order:

1. Widget tap: "Log meal".
2. Android launcher shortcut: "Log meal".
3. Optional later: Quick Settings tile.
4. Optional later: device gesture integration if user configures it manually.

## 6. AI API Strategy

Use a cheap cloud vision model that supports image input and structured JSON output.

Requirements:

- accepts meal photo;
- returns calorie estimate;
- returns protein/carbs/fat estimate;
- returns concise reasoning internally, but app only stores numbers;
- supports JSON output;
- cheap enough for personal use;
- reliable enough for rough tracking.

The app should not depend on a nutrition database.

The AI should estimate from the image only, but the prompt may instruct it to use general nutrition knowledge and rough online-style validation assumptions.

No barcode scanning.
No food search.
No manual ingredient lookup.

### AI response schema

The app should require strict JSON:

```json
{
  "meal_name": "string",
  "calories": 0,
  "protein_g": 0,
  "carbs_g": 0,
  "fat_g": 0,
  "confidence": "low|medium|high"
}
```

The app should ignore uncertainty for UX purposes, but storing confidence locally is still useful for debugging.

### AI prompt

Use a strict prompt:

```text
You are estimating nutrition from a meal photo for personal calorie tracking.

Return only valid JSON.

Estimate the total meal calories and macros from the visible food. Use common portion-size assumptions when exact quantities are unclear. Account for likely hidden calories such as oil, butter, sauces, cheese, dressings, and cooking fat when visually plausible.

Do not ask follow-up questions.
Do not say you are uncertain.
Do not return ranges.
Return one best estimate.

Use this JSON schema exactly:
{
  "meal_name": "short description",
  "calories": integer,
  "protein_g": integer,
  "carbs_g": integer,
  "fat_g": integer,
  "confidence": "low" | "medium" | "high"
}

Rules:
- Calories must be realistic for the full visible meal.
- Macros should roughly match calories.
- If multiple foods are visible, estimate the whole plate/bowl/tray.
- If drinks are visible, include them if they look caloric.
- If the image is not food, return:
{
  "meal_name": "not food",
  "calories": 0,
  "protein_g": 0,
  "carbs_g": 0,
  "fat_g": 0,
  "confidence": "low"
}
```

### Macro validation

After receiving the AI result, validate consistency locally:

```text
macro_calories = protein_g * 4 + carbs_g * 4 + fat_g * 9
```

If macro calories differ too much from total calories:

- keep AI calories as source of truth;
- scale macros proportionally;
- round to integers.

Threshold:

- if difference > 25%, normalize macros.

## 7. Local Data Model

Use local storage only.

Recommended storage:

- SQLite if using React Native side heavily;
- SharedPreferences/DataStore for widget-facing current state;
- optionally both:
- SQLite for internal records;
- SharedPreferences/DataStore for fast widget state.

Since there is no history screen, the app only needs current-day state plus last meal.

### Stored state

```json
{
  "date": "YYYY-MM-DD",
  "daily_calorie_target": 2500,
  "calories_consumed_today": 1050,
  "calories_remaining": 1450,
  "last_meal": {
    "timestamp": "ISO-8601",
    "meal_name": "rice chicken bowl",
    "calories": 620,
    "protein_g": 42,
    "carbs_g": 58,
    "fat_g": 18
  },
  "adjustment_step": 50,
  "goal_mode": "weight_loss"
}
```

### Daily reset

Each day, reset:

- calories consumed today = 0;
- calories remaining = daily target;
- last meal can either:
- remain visible but marked as yesterday;
- or be cleared.

Preferred:

- reset calories daily;
- clear last meal display at first widget update after midnight;
- show `No meal today`.

Implement daily reset with:

- local date check on every widget update;
- optional WorkManager scheduled around midnight;
- never rely only on background scheduling.

## 8. Widget Update Logic

Widget should update when:

- app starts;
- photo is logged;
- user adjusts values;
- date changes;
- device comes back online after failed analysis;
- widget is added/resized.

Use a single source of truth:

```text
WidgetStateRepository
```

Responsibilities:

- read current local state;
- apply daily reset if needed;
- expose render-ready widget values;
- update widget after changes.

Native widget should not contain business logic beyond dispatching actions.

## 9. Adjustment Logic

User can modify calories directly from the widget.

### Adjust daily calories remaining

If user increases remaining calories:

```text
calories_remaining += step
calories_consumed_today -= step
```

Clamp:

- calories_consumed_today cannot go below 0.

If user decreases remaining calories:

```text
calories_remaining -= step
calories_consumed_today += step
```

### Adjust last meal calories

If last meal is adjusted:

```text
delta = new_last_meal_calories - old_last_meal_calories
calories_consumed_today += delta
calories_remaining -= delta
```

Macros scale proportionally:

```text
ratio = new_calories / old_calories
protein_g *= ratio
carbs_g *= ratio
fat_g *= ratio
```

Clamp:

- last meal calories cannot go below 0;
- consumed calories cannot go below 0.

## 10. Offline Behavior

No AI processing offline.

If offline:

- widget continues showing last stored values;
- capture can either be blocked or queued.

Preferred MVP:

- block capture with a simple message:
- "Offline. Last values are shown."
- do not queue images.

Reason:

- queueing photos adds complexity;
- personal-use MVP does not need it.

## 11. File/Module Structure

Suggested structure:

```text
/app
  /src
    /screens
      SetupScreen.tsx
      SettingsScreen.tsx
    /storage
      settingsStorage.ts
      widgetBridge.ts
    /api
      nutritionVisionApi.ts
    /types
      nutrition.ts
    App.tsx

/android/app/src/main/java/...
  /widget
    CalorieWidgetProvider.kt
    CalorieWidgetRenderer.kt
    WidgetActionReceiver.kt
    WidgetStateRepository.kt
  /camera
    MealCaptureActivity.kt
    ImageCompressor.kt
  /ai
    NutritionApiClient.kt
    NutritionResultParser.kt
  /storage
    LocalStateStore.kt
  /workers
    MidnightResetWorker.kt
```

## 12. Implementation Phases

### Phase 1 - Native widget proof of concept

Goal:

- prove React Native + Android widget integration works.

Tasks:

- create RN Android project;
- add native Android widget;
- show static 4x2 widget;
- update widget from local stored state;
- add widget to home screen;
- confirm resizing behavior.

Acceptance:

- widget appears on Android home screen;
- widget can display calories and macros;
- app can update widget state.

### Phase 2 - Local state and daily reset

Tasks:

- implement local state repository;
- store daily target;
- store current date;
- implement date-based reset;
- expose current widget state.

Acceptance:

- calories reset on new day;
- widget shows correct current-day values;
- no account or remote DB exists.

### Phase 3 - Widget interactions

Tasks:

- implement tap zones or vertical swipe if feasible;
- implement adjustment step cycle;
- implement calories remaining adjustment;
- implement last meal adjustment;
- refresh widget after every action.

Acceptance:

- user can adjust calories from widget without opening the app;
- user can adjust last meal numbers from widget;
- values persist after reboot/app close.

Important:

- test on at least one Pixel Launcher or stock-like launcher.
- if vertical swipe is inconsistent, use tap zones.

### Phase 4 - Camera capture

Tasks:

- add widget action to launch meal capture;
- implement camera capture Activity;
- request camera permission;
- save temporary image;
- compress image before upload.

Acceptance:

- user taps widget;
- camera opens;
- user takes photo;
- image is available for API call.

### Phase 5 - AI nutrition estimation

Tasks:

- choose API provider;
- implement image upload;
- send strict prompt;
- parse JSON response;
- validate macro/calorie consistency;
- store result locally;
- update widget.

Acceptance:

- photo results in estimated calories/macros;
- widget updates after analysis;
- malformed AI response is handled safely.

### Phase 6 - Minimal settings app

Tasks:

- first launch setup;
- daily calorie target setting;
- goal mode setting;
- API key setting if needed;
- reset today;
- clear data;
- widget setup instructions.

Acceptance:

- user can configure target;
- user can install/use widget;
- app has no unnecessary screens.

### Phase 7 - Polish and reliability

Tasks:

- handle API failure;
- handle offline state;
- handle non-food images;
- add loading state on widget:
- "Analyzing..."
- add error state:
- "Couldn't estimate"
- prevent duplicate logging if user taps twice;
- test reboot behavior;
- test midnight reset;
- test multiple widget instances.

Acceptance:

- app is stable enough for daily personal use.

## 13. API Provider Decision

Compare cheap vision APIs and choose one.

Required criteria:

- image input;
- JSON output;
- low cost;
- acceptable food recognition;
- easy REST integration;
- no need for account system inside the app.

Candidate categories:

- OpenAI small vision model;
- Gemini vision model;
- other low-cost multimodal API.

Do not build local AI inference for MVP.

## 14. Non-Goals

Do not implement:

- barcode scanning;
- manual food database;
- search;
- meal history;
- charts;
- social features;
- accounts;
- subscriptions;
- cloud sync;
- Play Store compliance work;
- iOS;
- web app;
- wearable integration;
- recipes;
- reminders;
- medical/diabetic tracking.

## 15. Main Risks

### Risk 1 - Widget gesture limitations

Mitigation:

- prototype widget interactions first;
- support vertical swipe only;
- fallback to tap zones.

### Risk 2 - AI calorie estimates are imperfect

Mitigation:

- optimize prompt;
- account for hidden calories;
- allow fast correction from widget;
- do not claim medical-grade accuracy.

### Risk 3 - React Native/widget mismatch

Mitigation:

- keep widget native;
- use RN only for minimal app shell;
- share state through native-accessible storage.

### Risk 4 - Overbuilding

Mitigation:

- no history;
- no food database;
- no account;
- no analytics;
- no barcode.

## 16. Final MVP Definition

The MVP is complete when:

1. User can install APK.
2. User can place a 4x2 home-screen widget.
3. User can set daily calorie target.
4. User can tap widget to take a meal photo.
5. App sends photo to cloud AI.
6. AI returns calories/macros.
7. Widget shows calories remaining and last meal macros.
8. User can adjust numbers directly on widget.
9. Values reset daily.
10. Data stays local.
11. App is usable without any normal meal history interface.
