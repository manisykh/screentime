# Architecture

Screen Time Manager follows MVVM.

```text
UI (Jetpack Compose)
        |
ViewModel
        |
Repository
        |
DataStore / Room / Android system services
```

## Layers

### UI

- Jetpack Compose screens
- Always-visible Developer Safe Mode status
- Kill Switch action surface
- Future usage statistics and policy screens

### ViewModel

- Exposes screen state
- Handles user actions
- Must keep Safe Mode behavior explicit

### Repository

- Owns persistence and platform service boundaries
- DataStore for small settings such as Safe Mode
- Room for larger policy and usage budget data when needed

### Platform Services

- UsageStatsManager for usage statistics
- WorkManager for periodic background work
- AccessibilityService for detection now and blocking only after the remaining
  safety foundations are complete

## Current Modules

- `data/SettingsDataStore.kt`: DataStore instance.
- `data/SettingsRepository.kt`: Safe Mode, Kill Switch, Emergency Unlock, and
  Auto Recovery persistence. It also stores the current MVP usage policy values.
- `ui/safety/SafeModeViewModel.kt`: UI state and safety actions.
- `usage/UsageStatsRepository.kt`: Usage access permission check and today usage
  query.
- `usage/AppCatalogRepository.kt`: Installed launchable app list for group
  selection.
- `usage/AppVisibility.kt`: Shared app visibility filters for system/internal
  packages.
- `safety/SafetyGate.kt`: Shared Safe Mode, Policy Enforcement, and never-block
  package gate for future blocking decisions.
- `blocking/ScreenTimeAccessibilityService.kt`: No-op foreground detection
  service that records safety-gated would-block decisions without enforcement.
- `worker/UsagePolicyCheckWorker.kt`: Periodic display-only policy checks that
  write event logs and may send Android notifications.
- `notification/UsageNotificationHelper.kt`: Notification channel and policy
  alert helper.
- `MainActivity.kt`: Compose UI and Android settings intents.

## Policy Settings

Current policy settings are stored in DataStore:

- Weekday total usage limit in minutes
- Weekend total usage limit in minutes
- Monday through Sunday total usage limits in minutes
- Multiple app group names
- Multiple app group package lists
- Multiple app group budgets in minutes
- Per-app limit rules stored as package-to-minutes pairs

These values are not enforced yet. Enforcement must wait until the remaining
safety gates are complete and must always respect Safe Mode.

## Policy Summary

The ViewModel currently calculates:

- Total used minutes against today's day-of-week limit
- App group used minutes against the group budget
- Per-app used minutes against each stored app limit

These calculations are display-only and must not trigger blocking.

## Safety Gate

Every future blocking path must depend on a single safety decision:

```kotlin
if (!SafetyGate.evaluateBlocking(
        safeModeEnabled = safeModeEnabled,
        policyEnforcementEnabled = policyEnforcementEnabled,
        targetPackageName = packageName,
    ).canEvaluateBlocking
) {
    return
}
```

This check is required before any blocking screen, app interception, or
enforcement logic.

## Pre-Blocking Flow

The current blocking-related flow is intentionally split:

```text
AccessibilityService
    -> SafetyGate.evaluateBlocking(...)
    -> BlockDecisionEngine.evaluate(...)
    -> event log only

Safety tab preview button
    -> BlockedActivity preview
```

`BlockedActivity` is registered for the future blocked-screen experience, but
the AccessibilityService does not start it yet. This keeps the app one step
before real enforcement while allowing the blocked-screen UX, Emergency Unlock,
and Kill Switch paths to be reviewed safely.
