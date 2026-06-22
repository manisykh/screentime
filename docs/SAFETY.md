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

User-selected always-allowed apps are also treated as never-block targets at
runtime. Required system safety apps cannot be removed from the whitelist.

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
reason when blocking evaluation is denied. Real blocking may proceed only after
this gate allows policy evaluation and the policy decision says a limit is
exceeded.

## Foreground Usage Monitor Enforcement

The foreground usage monitor service is the primary enforcement path.
The target product behavior is AppBlock-style enforcement: monitor the current
foreground app at short intervals, show an ongoing usage notification, move the
user away from an exceeded app, keep a local block surface visible, and block
re-entry while the policy remains exceeded.

It may:

- Run only when Safe Mode is OFF and Policy Enforcement is ON.
- Use UsageStats to check the current foreground app at short intervals.
- Show an ongoing monitor notification with second-level usage.
- Send the user to the launcher when a blocked app reaches its limit.
- Show a local blocking overlay when overlay permission is granted.
- Fall back to the local blocked screen when overlay permission is unavailable.

It must:

- Stop and remove blocking overlays when Safe Mode or Kill Switch is activated.
- Respect the required whitelist and user-selected always-allowed apps.
- Keep Emergency Unlock and Kill Switch available on block surfaces.

## AccessibilityService Enforcement

The current AccessibilityService implementation is a secondary detection path.
Real enforcement is handled by the foreground usage monitor service so blocking
does not require Accessibility permission.

It may:

- Appear in Android Accessibility settings.
- Observe foreground app package changes.
- Retrieve interactive window packages for PIP or multi-window detection.
- Display the latest foreground detection status in the Safety tab.
- Record event-log entries.
- Run `SafetyGate.evaluateBlocking(...)`.
- Record would-block detections when a limit is exceeded.

It must not:

- Close or stop another app.
- Kill another app process.
- Launch a blocking screen while the foreground monitor service is responsible
  for enforcement.
- Enforce policy while Safe Mode is ON.
- Enforce policy while Policy Enforcement is OFF.

## Blocking Reliability Boundary

Normal app permissions can combine UsageStats, AccessibilityService, media
pause, and overlay windows, but Android does not guarantee that a third-party
app can permanently cover or control every immersive game, secure surface, PIP
window, or vendor-customized foreground surface.

Strict commercial-grade enforcement for a managed child device should be
designed as an optional Device Owner / managed-device mode. That mode can use
Android management APIs in addition to the current safety gate. It must still
keep Emergency Unlock, Kill Switch, required whitelist apps, and Safe Mode
recovery paths available before stronger enforcement is enabled.
- Block required whitelist or user always-allowed packages.
- Store screen text from accessibility windows.

Kill Switch remains the highest-priority recovery path because it enables Safe
Mode. With Safe Mode ON, the service must return before any detection logic that
could lead toward enforcement.

With Policy Enforcement OFF, the service must also return before whitelist or
policy checks. This keeps accessibility permission separate from policy
activation.

## Blocking Screen State

The blocked-screen Activity remains `exported=false` and may be opened from:

- An explicit in-app preview action.
- AccessibilityService after the safety gate and policy decision both allow
  enforcement.

In real blocking mode:

- Emergency Unlock and Kill Switch must remain visible.
- Parent/Admin PIN time override may grant temporary access for today.
- A full-screen accessibility overlay may cover games or immersive apps after
  AccessibilityService safety checks pass. Android "draw over other apps"
  permission is used only as a fallback overlay path.
- The blocking overlay must expose Emergency Unlock and Kill Switch.
- The service may send Home before opening the blocking screen so full-screen
  apps do not remain usable behind the block.
- Back must not return the user directly to the blocked app.
- Leaving the blocking screen without an override should send the user Home.
- Media playback may be paused for a short guarded period when the blocking
  screen opens, but the app must not attempt to kill another app or forcibly
  close its PIP window.
