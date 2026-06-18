# AccessibilityService Design

This document describes the AccessibilityService safety gate. The current
implementation is no-op and detection-only; it does not block apps.

## Purpose

The future AccessibilityService will observe the foreground app and decide
whether a blocking screen should be shown after a policy has been exceeded.

## Mandatory Return Order

Every foreground-app event must return before blocking when any safety condition
is active:

```kotlin
if (safeModeEnabled) return
if (!policyEnforcementEnabled) return
if (packageName in neverBlockPackages) return
if (!policyExceeded(packageName)) return
```

The code path should use `SafetyGate.evaluateBlocking(...)` rather than
duplicating these checks.

## Never-Block Packages

The code-level whitelist lives in:

- `safety/SafetyGate.kt`

The minimum whitelist must include:

- Android Settings
- Google Play Store
- Package Installer
- Screen Time Manager

## Blocking Conditions

Blocking may only be considered when:

- Safe Mode is OFF.
- Policy enforcement is explicitly enabled.
- The target app is not whitelisted.
- Usage access data confirms the target app exceeded its limit.
- Emergency Unlock and Kill Switch remain available.

## Forbidden in First Implementation

The first AccessibilityService implementation must not:

- Block Settings.
- Block package installers.
- Block Screen Time Manager.
- Hide or disable Kill Switch.
- Hide or disable Emergency Unlock.
- Run when Safe Mode is ON.
- Launch any blocking Activity.
- Redirect the foreground app.

## Current No-op Implementation

The current service:

- Registers in Android Accessibility settings.
- Listens for foreground window package events.
- Returns immediately when Safe Mode is ON.
- Returns immediately when Policy Enforcement is OFF.
- Returns after logging when the target package is whitelisted.
- Calls `SafetyGate.evaluateBlocking(...)`.
- Logs `would be blocked` when the simulation says a policy would apply.
- Updates the latest foreground detection status shown in the Safety tab.

It intentionally does not enforce blocking.

## Test Plan Before Implementation

Before coding the service:

- Verify Safe Mode ON prevents every blocking decision.
- Verify every whitelist package returns before policy checks.
- Verify Kill Switch forces Safe Mode ON.
- Verify Emergency Unlock works offline.
- Verify Auto Recovery restores Safe Mode after abnormal startup.
