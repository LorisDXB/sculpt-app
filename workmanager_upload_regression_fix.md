# Meal Upload Regression Fix

## Summary

The meal photo upload flow regressed on the `dev` branch after moving OpenAI image analysis out of `MealCaptureActivity` and into `WorkManager`.

On real devices, API key validation still worked, but image analysis from a captured photo failed with:

- `UnknownHostException: Unable to resolve host "api.openai.com"`
- widget message: `Network error while analyzing meal.`

This happened even though the same phone and network conditions worked on `main`.

## Root Cause

`main` performed image compression and the OpenAI request from a foreground thread inside `MealCaptureActivity`.

`dev` changed that flow to:

1. capture photo
2. enqueue `MealAnalysisWorker`
3. close the activity
4. perform upload from `WorkManager`

That worker-based path was the regression. In the affected device conditions, DNS resolution for `api.openai.com` failed from the worker, while the foreground app process could still validate the API key successfully.

## Fix

The fix was to restore the known-good request pattern from `main` while keeping the newer camera UI:

- remove `WorkManager` from the meal image upload path
- perform compression and `NutritionApiClient.analyzeMealImage(...)` from a background `Thread` owned by `MealCaptureActivity`
- keep widget state updates in the activity-driven flow
- keep the newer capture/review UI and voice transcript support

## Files Changed

- [android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt](/mnt/c/Users/gdedx/Delivery/PoissonCassant/sculpt-app/android/app/src/main/java/com/poissoncassant/sculptapp/camera/MealCaptureActivity.kt)

## Why This Fix Worked

Foreground API-key validation already proved that normal in-app networking worked on the device.

By moving the image-analysis request back out of `WorkManager` and into the foreground activity flow, the app returned to the same execution model as `main`, which resolved the DNS failure regression.

## Verification

Observed result after the fix:

- take picture
- send to AI
- image analysis succeeds
- widget updates correctly

## Debug Evidence

Before the fix, logs showed:

```text
UnknownHostException: Unable to resolve host "api.openai.com"
```

This occurred during the worker-based image analysis request, not during API key validation.
