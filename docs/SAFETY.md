# Safety Rules

## Prime Directive

Safety First. No feature may create a condition where a developer or user is
permanently locked out of the device.

## Developer Safe Mode

Developer Safe Mode must exist before any blocking feature.

When Safe Mode is enabled:

- App blocking is disabled.
- Usage time limits are disabled.
- AccessibilityService enforcement is disabled.
- Background monitoring is stopped.
- Blocking screens are not shown.

Safe Mode must be enabled by default during development and must be visible on
the main screen.

## Emergency Unlock

Emergency Unlock must work without an internet connection.

Accepted mechanisms:

- Developer PIN
- Emergency password
- Hidden local menu

The goal is immediate recovery of app and device control.

Initial development PIN:

- `0000`

This default PIN exists only so early builds always have a known local recovery
path. Before real device enforcement is added, this must be changed through a
parent/developer settings flow.

## Kill Switch

The Kill Switch must immediately:

- Disable all policies.
- Stop AccessibilityService enforcement.
- Stop Foreground Service monitoring.
- Clear all active blocks.
- Enable Safe Mode.

Implementation rule:

- Kill Switch and Emergency Unlock must use the same recovery path.
- That path must set Safe Mode to `true`.
- That path must set policy enforcement to `false`.

## Auto Recovery

The app must enter Safe Mode automatically when it detects:

- Crash recovery
- Database errors
- Settings load failure
- Suspected infinite loop
- Repeated startup failure

Initial implementation:

- On app start, the app marks the current run as not clean.
- On normal `onStop`, the app marks the run as clean.
- If the next startup sees an unclean previous run, it activates Safe Mode and
  disables policy enforcement.
- This is a conservative crash recovery signal. It does not replace future
  structured crash reporting or database error handling.

## Accessibility Whitelist

These packages must never be blocked:

- Android Settings
- Google Play Store
- Package Installer
- Screen Time Manager

During early development, no other apps should be blocked.

## Development Rules

- AccessibilityService must not be implemented before Safe Mode exists.
- Every blocking feature must respect Safe Mode.
- Settings must never be blocked.
- Real devices should remain in Safe Mode during development.
- Create a Git commit before dangerous feature development.
- Any failure mode must preserve user control of the device.

## Policy Storage Without Enforcement

Usage limits and app group budgets may be stored before blocking exists.

Stored policy values must not:

- Launch a blocking screen.
- Stop another app.
- Start AccessibilityService enforcement.
- Start foreground monitoring.
- Override Safe Mode.

Policy summaries are also display-only. A limit being exceeded must not cause
blocking until the AccessibilityService safety gate is explicitly implemented.

Warning and exceeded states must not directly enforce policy. By themselves
they must not:

- Start background enforcement.
- Block apps.
- Change Safe Mode.

Android notifications and WorkManager checks may record and notify policy
states after notification permission is granted, but they must not block apps
or enable policy enforcement.

## Safety Gate

All future blocking code must use the shared safety gate before considering a
block:

- Safe Mode must be OFF.
- Policy enforcement must be explicitly enabled.
- Target package must not be in the never-block whitelist.
- A policy must be exceeded.

The gate is implemented as `SafetyGate.evaluateBlocking(...)`, which returns a
reason when blocking evaluation is denied. The current code still does not
implement blocking.

## No-op AccessibilityService

The current AccessibilityService implementation is detection-only.

It may:

- Appear in Android Accessibility settings.
- Observe foreground app package changes.
- Display the latest foreground detection status in the Safety tab.
- Record event-log entries.
- Run `SafetyGate.evaluateBlocking(...)`.
- Record `would be blocked` simulation events.

It must not:

- Launch a blocking screen.
- Redirect the user.
- Close or stop another app.
- Call global accessibility actions for enforcement.
- Enforce policy while Safe Mode is ON.

Kill Switch remains the highest-priority recovery path because it enables Safe
Mode. With Safe Mode ON, the service must return before any detection logic that
could lead toward enforcement.

With Policy Enforcement OFF, the service must also return before whitelist or
policy checks. This keeps accessibility permission separate from policy
activation.

## Pre-Blocking Screen State

The project may contain a blocked-screen Activity before real blocking is
enabled.

In this state:

- The blocked-screen Activity may be opened only from an explicit in-app preview
  action.
- AccessibilityService must not launch the blocked-screen Activity.
- AccessibilityService must only record detection and `would be blocked` events.
- Emergency Unlock and Kill Switch must remain available from the preview screen.
- The blocked-screen Activity must remain `exported=false`.

Real blocking requires a separate implementation step and must not be connected
until the user explicitly approves it after validating Safe Mode, Kill Switch,
Emergency Unlock, whitelist behavior, and policy decision logging.
