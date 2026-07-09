# Detection Status

`Recent Detection` is a diagnostic area, not a usage list.

It shows the latest regular user app evaluated by the foreground monitor service.
System apps, keyboards, Settings, installer apps, required allowlist apps, and this
app itself should not appear here.

## Should Appear

- User-opened regular apps that can be evaluated for blocking
- Apps with app limits, group limits, schedule blocking, or allow-only blocking
- Apps evaluated while Safe Mode is OFF and Policy Enforcement is ON

## Should Not Appear

- Screen Time Manager
- Android Settings
- Google Play Store
- Package Installer / Permission Controller
- System UI, launcher, keyboard, accessibility helper apps
- Required safety whitelist apps

## Purpose

- Verify foreground detection without relying on Accessibility permission
- Confirm SafetyGate filters system and never-block packages
- Confirm block decisions before and during real blocking
