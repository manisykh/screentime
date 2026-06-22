# AccessibilityService Design

This document describes the AccessibilityService safety gate and blocking
handoff. The current implementation can launch the local blocking screen only
after all safety checks pass.

## Purpose

The AccessibilityService observes foreground-style app events and decides
whether the blocking screen should be shown after a policy has been exceeded.

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

## Forbidden Behavior

The AccessibilityService must not:

- Block Settings.
- Block package installers.
- Block Screen Time Manager.
- Hide or disable Kill Switch.
- Hide or disable Emergency Unlock.
- Run when Safe Mode is ON.
- Kill another app process.
- Disable system navigation.
- Enforce policy while Policy Enforcement is OFF.

## Current Blocking Implementation

The current service:

- Registers in Android Accessibility settings.
- Listens for foreground window, window-content, and focus events.
- Retrieves interactive windows to identify the active app package during
  PIP or multi-window transitions.
- Returns immediately when Safe Mode is ON.
- Returns immediately when Policy Enforcement is OFF.
- Returns before policy evaluation when the target package is whitelisted.
- Calls `SafetyGate.evaluateBlocking(...)`.
- Evaluates daily, app, and group limits.
- Launches `BlockedActivity` when a policy is exceeded.
- Updates the latest foreground detection status shown in the Safety tab.
- Keeps Emergency Unlock and Kill Switch available from the blocking screen.

The service never attempts to terminate another app. Blocking is implemented by
showing a full-screen `TYPE_ACCESSIBILITY_OVERLAY` blocking layer from the
AccessibilityService. This is the primary path for games and immersive apps that
ignore Home actions or Activity launches. If the accessibility overlay fails,
the service attempts an app overlay when "draw over other apps" permission is
granted. If both overlay paths fail, it falls back to moving the current app to
Home with both the accessibility Home action and a launcher Home intent, opening
`BlockedActivity`, and retrying the blocked-screen launch once after a short
delay. Leaving the blocking screen without an override also sends the user Home.

The overlay includes Emergency Unlock, a Kill Switch, and a button that opens
the full blocked-screen controls. It is removed automatically when Safe Mode is
enabled, Policy Enforcement is disabled, Emergency Unlock succeeds, or Kill
Switch is activated.

While a target remains blocked, a short-interval overlay guard checks whether
the blocking layer is still attached. If the system detaches it while the app is
still blocked, the service restores the overlay. Policy reevaluation runs off
the main thread; only WindowManager view changes run on the main thread.

## PIP and Re-entry Behavior

Picture-in-picture and multi-window apps may emit different accessibility
events from full-screen apps. To improve detection, the service listens for:

- `TYPE_WINDOW_STATE_CHANGED`
- `TYPE_WINDOWS_CHANGED`
- `TYPE_WINDOW_CONTENT_CHANGED`
- `TYPE_VIEW_FOCUSED`

When an event is reported by System UI or another non-user app, the service
falls back to the active accessibility window package. The implementation uses
that window data only to identify the package to evaluate; it does not inspect
or store screen text.

When Screen Time Manager or its blocked screen is the active or focused window,
PIP packages are ignored for blocking evaluation. This prevents a floating PIP
window from repeatedly relaunching the blocked screen while the blocked screen
is already visible.

Repeated events are throttled briefly so the app can re-block quickly without
running heavy policy checks for every small UI update.

Accessibility events alone are not enough for games or video apps that can stay
open without emitting new window events. A foreground enforcement loop samples
the current active window package at a short interval and evaluates the same
SafetyGate and policy decision path. This allows blocking to occur while an app
is already running, not only when the user changes windows or reopens the app.

When a blocked app is opened again, the service re-evaluates it and launches
the blocking screen again after a short cooldown. The blocked screen intercepts
Back in real blocking mode and sends the user to Home rather than returning to
the blocked app.

Some media apps automatically enter picture-in-picture when another Activity is
shown on top of them. The service cannot forcibly close another app's PIP
window, so it sends media pause events for a short guarded period after opening
the blocking screen. Apps that honor Android media sessions should pause
playback even if the user taps play in the PIP controls during that window,
while re-entry into the full app remains blocked by the normal safety gate.

The pause guard is intentionally short-lived and checks the safety gate before
each pause request. Safe Mode, Policy Enforcement OFF, whitelist, or
always-allowed status stops the guard.

## Test Plan Before Implementation

Before coding the service:

- Verify Safe Mode ON prevents every blocking decision.
- Verify every whitelist package returns before policy checks.
- Verify Kill Switch forces Safe Mode ON.
- Verify Emergency Unlock works offline.
- Verify Auto Recovery restores Safe Mode after abnormal startup.
