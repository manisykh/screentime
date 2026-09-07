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
- Foreground service for active usage monitoring and enforcement
- Display-over-other-apps overlay for the primary blocking surface

## Current Modules

- `data/SettingsDataStore.kt`: DataStore instance.
- `data/SettingsRepository.kt`: Safe Mode, Kill Switch, Safe Recovery, and
  Auto Recovery persistence. It also stores policy values, temporary parent
  allowances, local parent/device pairing state, and the remote command inbox.
- `ui/safety/SafeModeViewModel.kt`: UI state and safety actions.
- `usage/UsageStatsRepository.kt`: Usage access permission check and today usage
  query.
- `usage/AppCatalogRepository.kt`: Installed launchable app list for group
  selection.
- `usage/AppVisibility.kt`: Shared app visibility filters for system/internal
  packages.
- `safety/SafetyGate.kt`: Shared Safe Mode, Policy Enforcement, and never-block
  package gate for future blocking decisions.
- `blocking/UsageMonitorForegroundService.kt`: Foreground monitoring and
  blocking enforcement service.
- `blocking/BlockedActivity.kt`: Parent controls and fallback blocked screen.
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

## Parent Management

The current parent-management implementation is the app-side boundary for a
future backend:

- Admin PIN is the single local credential for policy changes, pairing, allowed
  overrides, Safe Recovery, and Emergency Pass confirmation.
- Emergency Pass is not a second PIN. It is an app-scoped level-3 exception
  shared across all level-3 policies with a rolling seven-day cooldown.
- Remote commands are persisted as an inbox in DataStore and can apply:
  app extra time, app unlock-for-today, daily extra time, and daily
  unlock-for-today.
- A real account service can later replace the local simulator by writing the
  same command model into the repository boundary.

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

## Blocking Flow

The current blocking flow does not require AccessibilityService:

```text
UsageMonitorForegroundService
    -> SafetyGate.evaluateBlocking(...)
    -> BlockDecisionEngine.evaluate(...)
    -> SYSTEM_ALERT_WINDOW overlay
    -> BlockedActivity fallback / parent controls
```

`SYSTEM_ALERT_WINDOW` is required for strong blocking over immersive apps.
Without overlay permission, the app can still open a fallback blocked activity,
but Android may allow some foreground apps to regain focus.
