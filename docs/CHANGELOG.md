# Changelog

## 2026-07-05

- Removed the duplicate always-allowed apps card from the Blocking tab so app
  allowance management appears only inside allow-only mode.
- Added PIN visibility toggles to shared Compose PIN fields and hardened the
  blocking overlay PIN inputs to use numeric password mode by default.
- Added a Safety tab usage-consistency diagnostic that compares the foreground
  monitor notification counter against Today Usage for the same app.
- Added an allow-policy relationship summary inside allow-only mode to clarify
  required allowed apps, global always-allowed apps, and schedule-specific
  allowed apps.
- Expanded group statistics so each group can show its top used apps and any
  app-level limit/temporary allowance context.
- Added parent-management state, local parent/device pairing, and a remote
  command inbox that can apply remote extra time or unlock-for-today commands
  to the existing temporary allowance system.
- Clarified Admin PIN vs Emergency PIN roles in Settings: Admin PIN approves
  policy and parent overrides, while Emergency PIN remains an offline Safe Mode
  recovery path.

## 2026-07-04

- Hardened local-day usage reset behavior by filtering UsageStats fallback data
  unless the app has a same-day usage signal, pruning unobserved monotonic and
  continuity entries, and clearing persisted monitor foreground state on daily
  rollover.
- Clarified allowed-app policy ownership: required never-block apps are kept out
  of user allowed-app storage, user allowed apps sync into schedule allowed-app
  lists as already allowed, and schedule templates store only schedule-specific
  allowed apps.
- Moved launcher phone and messaging apps out of the absolute never-block list
  so users can choose whether to always allow them, while keeping the in-call
  telephony system UI as a required safety exception.
- Reused the wheel-style time picker for daily, group, and app duration editing
  and contained wheel/list scrolling so bottom sheets do not move while the
  inner picker is being scrolled.
- Collapsed the always-allowed app lists by default so required and user-allowed
  app details are shown only when the user expands the section.
- Put the required allowed-app list inside its own contained scroll area when
  the always-allowed section is expanded.
- Consolidated permission action buttons into a single Settings permission
  section, removed scattered permission buttons from other tabs, and replaced
  the top-right Settings shortcut with a permission warning badge when setup is
  incomplete.
- Tuned the Settings permission section into a flatter compact list with a
  smaller header warning badge, compact status chip, reduced warning tint, and
  smaller permission action buttons.
- Removed the completed permission card border and elevation so the ready state
  reads as a clean status panel instead of a warning card.

## 2026-07-01

- Added periodic and immediate service health checks for Usage Access, overlay,
  notification permission, notification access, exact alarm readiness, and
  foreground monitor freshness.
- Added Safety tab health status so missing permissions or a stale monitor can
  be seen without digging through logs.
- Added local-day rollover recovery using date/time change broadcasts, a
  midnight exact-alarm hook, and an in-app rollover loop to clear daily
  temporary allowances on the Korean local day boundary.
- Clarified schedule blocking and allow-only mode behavior in the UI.
- Allowed app groups to be completely deleted and preserved the empty app-group
  state instead of recreating a default group.
- Improved app search input so Korean IME composition is preserved and searches
  can match both app names and package names.
- Stabilized app-group name editing with a composition-aware local text buffer
  so Korean and English input are not overwritten by draft recomposition.
- Moved per-schedule allowed-app editing into a bottom sheet, added allowed-app
  counts to schedule templates, and surfaced guidance that each template can
  have its own allowed apps.
- Reworked schedule blocking from preset/template-save controls into schedule
  item management with new/delete actions, name editing, inline time/day
  controls, and per-schedule allowed-app selection.
- Updated the overview policy summary so schedule, group, and app-limit draft
  changes are reflected immediately while editing.
- Added schedule status to the overview policy summary and Safety diagnostics,
  including active schedule, next schedule, schedule window, repeat days, and
  allowed-app counts.
- Localized blocked-screen titles and reason text for daily limit, app limit,
  group limit, schedule blocking, and allow-only mode blocks.
- Reorganized the main tabs into Overview, Time, Block, Stats, Safety, and
  Settings. Time now groups daily, app, and group limits; Block now groups
  schedule blocking, allow-only mode, and always-allowed apps.
- Reset the foreground monitor's active app session and usage cache at the
  local-day boundary so usage smoothing cannot carry yesterday's app time into
  today's app totals.
- Added usage anomaly auditing and impossible-value guards so a tablet/OEM
  UsageStats spike cannot persist hundreds of hours into today's usage, policy
  summaries, or blocking calculations.

## 2026-06-28

- Aligned the blocking permission model with commercial app-blocker style
  setup: Usage Access, notification access, display-over-other-apps,
  notification permission, and alarms/reminders.
- Removed AccessibilityService from the app manifest and blocking readiness.
- Added a NotificationListenerService entry for notification-access setup and
  use active media sessions to pause/stop PIP or media playback before falling
  back to media key events.
- Added exact-alarm recovery scheduling so the foreground monitor has another
  recovery path after task removal or process cleanup.

## 2026-06-26

- Hardened blocking re-entry and foreground counting: Home exit grace now ends
  as soon as a managed app returns to foreground, foreground detection falls
  back to recent UsageStats when UsageEvents are delayed, and active-session
  grace is longer to avoid timer stalls during transient foreground gaps.
- Prevented Policy Enforcement from being enabled unless Usage Access and
  display-over-other-apps permission are ready, so the UI does not imply strong
  blocking when Android overlay permission is missing.
- Kept the blocking overlay as the primary block surface instead of immediately
  launching BlockedActivity, and removed AccessibilityService from the blocking
  path so strong blocking depends on display-over-other-apps permission.
- Added a first-run permission setup dialog that consolidates Usage Access,
  display-over-other-apps, and notification permission setup into one checklist.
- Split foreground usage refresh from heavier statistics refresh so returning to
  the app updates today's usage without reloading 30-day charts and top-app
  statistics.
- Moved SafeMode UI-state composition work to a background dispatcher so policy
  summaries and block-decision previews do not run on the main UI thread.
- Reduced tab-transition rendering cost by drawing only the active tab during
  the transition instead of keeping the previous full tab content alive.
- Added in-memory app-icon bitmap caching and background icon decoding to reduce
  list scroll and tab-switch jank.
- Cached launchable package lists and app labels inside UsageStatsRepository to
  avoid repeated PackageManager work during usage refreshes.
- Added a shared usage-continuity store so the monitor notification, main app,
  workers, and block screen all read from the same same-day monotonic usage
  source.
- Persisted active foreground usage during limited-app monitoring and flushed it
  on app switches/service teardown so usage cannot jump backward after process
  death, memory cleanup, or delayed UsageStats updates.
- Added usage-monitor heartbeat status with last tick, foreground app, decision,
  recovery reason, and stop reason for Safety tab diagnostics.
- Strengthened the monitor watchdog worker so boot, app update, task removal,
  and periodic checks restart the foreground monitor when policy enforcement is
  active but the monitor is stopped or stale.
- Centralized blocking decisions in BlockDecisionEngine so the foreground
  monitor, accessibility helper, and preview paths use the same priority order:
  fail-safe exits, allow-only mode, daily limit, schedule, group limit, then app
  limit.
- Kept limited-app foreground sessions alive across transient System UI/PIP/full
  screen signals so blocking does not stop evaluating while a game or video app
  remains visibly active.
- Restarted the foreground usage monitor from MainActivity.onResume when policy
  enforcement is active, covering cases where the service was killed while the
  app state itself did not change.
- Changed policy, schedule, app-limit, and blocked-screen extra-time controls
  to 1-minute increments so short-limit blocking tests can run quickly.
- Changed real blocking enforcement to show the overlay shield before launching
  BlockedActivity, then remove the shield only after the blocked screen is
  confirmed in front. This prevents silent activity-launch failures from leaving
  the blocked app visible after its limit is exceeded.

## 2026-06-25

- Added boot and package-replaced recovery so policy checking and the usage
  monitor are re-scheduled after device restart or app update.
- Added same-day stable usage continuity inside the foreground monitor so
  notification and block decisions do not move backward when UsageStats briefly
  reports a lower value after app switching.
- Added schedule templates that can save, apply, and delete current schedule
  windows through the existing policy-draft save flow.
- Improved allow-only mode setup by surfacing always-allowed app management
  directly below the allow-only control when the mode is enabled.
- Hid the daily statistics chart when all returned daily usage values are zero,
  showing the empty-state text instead.
- Refined the bottom save action with consistent tab alignment, a larger status
  dot, border, and state-colored fill behavior.
- Routed policy summary group and app limit gauges through the same progress
  bar component for visual consistency.
- Removed Accessibility permission from the visible blocking-readiness
  requirements. Usage Access, Safe Mode, policy enforcement, whitelist, and
  emergency recovery remain the required readiness gates.
- Kept AccessibilityService as an optional foreground-signal helper and
  re-applied its event configuration at service connection time for more
  reliable diagnostics when the user enables it.
- Added a one-shot UsageMonitor recovery worker after monitor task removal so
  recent-app cleanup and memory cleanup events get an additional restart path.
- Extended the foreground missing grace window to reduce false monitoring
  drops during short UsageStats gaps while switching limited apps.
- Expanded the Statistics tab from a 7-day list to a 30-day horizontally
  scrollable usage chart with today, weekend, average, and peak-day context.
- Added flexible Top Apps statistics ranges so the ranking can switch between
  1-day, 7-day, and 30-day usage totals.
- Restyled the policy-save PIN dialog to match the app's rounded card, status
  badge, and primary action button visual system.
- Improved schedule-blocking setup with clearer active-window/repeat summaries,
  bedtime/study/meal presets, and quick every-day/weekday/weekend repeat chips.

## 2026-06-24

- Reworked foreground usage accounting so UsageEvents are the primary source
  whenever available, avoiding stale aggregate UsageStats values that can keep
  counting a backgrounded, closed, or PIP app.
- Added an in-process foreground app tracker fed by AccessibilityService active
  and focused windows. The monitor now trusts this fresh signal before falling
  back to UsageStats, and launcher/System UI/Settings events clear the active
  usage session.
- Removed duplicate active-session elapsed-time compensation from blocking
  services so notification time, main app usage, and block decisions use one
  shared usage calculation path.
- Stopped active usage sessions on screen-off/keyguard signals and paused the
  foreground monitor's detailed counting while the device is not interactive.
- Added same-day monotonic usage smoothing so temporary UsageStats jitter cannot
  lower an app's counted time and reopen a just-blocked app below its limit.
- Hid Kill Switch from normal test-user surfaces while keeping the internal
  recovery path available for developer/debug safety.
- Extended schedule blocking with live status, time-window display, and quick
  bedtime/study presets.
- Added allow-only mode so only required exceptions and user-selected always
  allowed apps can open while the mode is active.
- Added a Statistics tab with recent daily usage, today's top apps, and group
  usage summaries.
- Wired Accessibility foreground app signals directly into the usage monitor so
  switching among multiple limited apps immediately refreshes the ongoing
  notification and block decision for the newly foregrounded app.
- Added AccessibilityService direct blocking fallback so managed apps are still
  blocked after device memory cleanup even if the foreground monitor service has
  not restarted yet.
- Added a foreground monitor task-removal restart request to reduce monitoring
  gaps after recent-app or memory cleanup events.
- Added focused foreground-detection debug logs under the `STM-Foreground` tag
  to compare Accessibility events, monitor immediate requests, UsageStats
  foreground resolution, notification decisions, and blocking decisions while
  switching among multiple limited apps.
- Added active foreground-session usage smoothing in the monitor so notification
  seconds continue moving even when UsageStats delays foreground-time updates
  for a newly opened limited app.

## 2026-06-23

- Hardened the foreground blocking overlay without changing the existing
  Open Manager flow or time display: overlay references are volatile, overlay
  removal from the monitor loop now runs on the main dispatcher, repeated
  package evaluation is cached within one monitor tick, and the overlay requests
  focus while consuming Back key events.
- Updated foreground monitor notifications to include today's temporary extra
  time, for example `(+5m)`, and to use the effective limit after parent time
  overrides.
- Changed real blocking to open the full parent-control blocked screen first,
  using the simple overlay only as a fallback if the activity cannot be opened.
- Differentiated blocked-screen backgrounds: app/group blocks use a dark navy
  background while daily total blocks use a dark red background.
- Reduced notification noise and unnecessary foreground usage evaluation for
  unrestricted apps: detailed second-level notification text is now shown only
  for apps with direct app/group limits or when a block is about to happen,
  while unrestricted apps show a quiet "Monitoring limited apps" status.
- Improved foreground app detection after a block: hidden System UI, launcher,
  and keyboard foreground events no longer clear the current visible app, and
  the monitor keeps a short grace window for transient UsageStats gaps so the
  next limited app starts second-level monitoring promptly.
- Added a foreground blocking watchdog that keeps enforcing while a package is
  still blocked: it repeatedly pauses/stops active media for PIP/video apps,
  re-sends Home and reopens the blocked screen when the blocked app regains
  foreground, and avoids disrupting the parent controls screen when Screen Time
  Manager itself is foreground.
- Strengthened the blocked screen visual state by tinting the card itself:
  app/group blocks now use a blue-tinted card and daily total blocks use a
  red-tinted card with stronger borders and badges.
- Added blocked-screen foreground verification: after launching the blocked
  activity, the foreground monitor now checks whether Screen Time Manager
  actually reached the front and falls back to the overlay shield if a PIP,
  video, or immersive app remains visible.
- Added schedule blocking policy storage, UI controls, and block decisions so
  selected days and time windows can block apps while still respecting Safe
  Mode, policy enforcement, required exceptions, and always allowed apps.
- Added a short home-exit grace path from the blocking screen so pressing Home
  clears the current block overlay/guard and reaches the launcher instead of
  alternating between the overlay and BlockedActivity.

## 2026-06-22

- Restored the blocking overlay Open Manager action so it opens the blocked
  controls screen with parent PIN, extra time, and unlock-today options instead
  of jumping directly to the app main screen.
- Added a short manager-open grace window to prevent the foreground monitor from
  immediately reattaching the blocking overlay while the controls screen is
  launching.
- Aligned the foreground monitor's daily usage package filter with the main app
  usage list by excluding hidden/non-launchable packages and very short entries,
  reducing Daily Time mismatches between the block UI and the main dashboard.
- Kept second-level time in the ongoing notification while showing block-screen
  usage with the same minute label format used by the main app.

## 2026-06-21

- Added a foreground usage monitor service that runs with an ongoing
  notification, checks the current foreground app every second through
  UsageStats, updates the notification with second-level usage, and enforces
  blocks without requiring AccessibilityService.
- Defined the product target as AppBlock-style enforcement: second-level
  foreground monitoring, ongoing notification, home redirection on exceed, and
  immediate re-block on app re-entry rather than OS-level process killing.
- Added active foreground session timing inside the monitor service so the
  currently running app is counted before UsageStats finalizes the session on
  background/stop. This prevents limits from being detected only after the app
  is backgrounded and reopened.
- Moved real blocking responsibility from AccessibilityService to the foreground
  monitor path. AccessibilityService now records would-block detections as a
  secondary signal so the two enforcement paths do not fight each other.
- Strengthened real blocking enforcement for immersive games and video apps:
  the AccessibilityService watchdog now falls back to UsageEvents foreground
  detection when accessibility windows are unavailable, compensates active
  foreground session usage before UsageStats settles, keeps a blocking overlay
  guarded, hard-enforces Home/BlockedActivity when the blocked app remains in
  foreground, and repeatedly pauses active media while the block still applies.
- Removed repeated Home/BlockedActivity forcing and periodic overlay
  remove/re-add while a blocking overlay is already attached, preventing the
  blocked app and block UI from alternating during gameplay.
- Restored accessibility overlay as the primary blocking window, made the
  blocking scrim fully opaque, added immersive system-UI flags, and reassert
  only the overlay while the blocked app remains foreground. Home/BlockedActivity
  fallback is now used only when overlay attachment fails.
- Documented the reliability boundary of normal-permission blocking and noted
  Device Owner / managed-device mode as the path for stricter enforcement.

## 2026-06-12

- Re-applied the UI/UX comparison review with the blue primary palette,
  Material slider styling, equal day chips, shorter quick apply labels, and
  neutral secondary usage bars.
- Reworked App Limits into a flat divider list inside the policy card, removed
  the nested scrolling list, added lightweight incremental rendering, and
  localized the remaining hard-coded app limit labels.
- Replaced the stepped Daily Limits slider with a custom smooth track that
  keeps 15-minute snapping while removing visible dots and the vertical thumb.
- Reconnected Policy tab saving through Admin PIN verification and DataStore
  persistence for daily limits, app groups, and per-app limits.
- Added app group editing for group name, budget, app membership, and deletion;
  app membership is kept unique across groups to avoid duplicated group usage.
- Updated background policy checks to evaluate each app group independently
  instead of combining all group packages under the legacy single-group budget.
- Added a shared `SafetyGate.evaluateBlocking(...)` result model so Safe Mode,
  Policy Enforcement, and never-block whitelist checks use one ordered gate.
- Connected the no-op AccessibilityService and block-decision simulation to the
  shared safety gate; Policy Enforcement OFF now prevents block evaluation just
  like Safe Mode.
- Added horizontal swipe gestures for switching Overview, Policy, and Safety
  tabs without adding a pager dependency.
- Replaced the language toggle text with drawn flag-style icons for Korean and
  English.
- Connected the Policy Summary Edit action to the Policy tab, moved policy
  saving to the top of the Policy screen, added budget-overflow save blocking,
  and made app lists scroll inside their cards.
- Unified Today Usage and Policy Summary usage bar colors through the same
  normal, warning, and exceeded status logic.
- Replaced the App Limits tap-cycle row with a horizontal swipe control: dragging
  an app row fills the background from left to right, snaps to 15-minute
  increments, shows the selected time next to the action button, and saves on
  drag end.
- Split policy editing into dedicated Policy and Apps tabs, moved language,
  PIN, and event log controls into a new Settings tab, removed the header flag
  language toggle, and removed app-list "show more" paging so full app lists are
  available immediately inside scrollable cards.
- Renamed the Policy tab to Days/Groups, moved group management out of Apps and
  into that tab, and replaced swipe-to-fill app limit rows with a tap-to-edit
  bottom sheet that offers presets and the minute slider.
- Changed the tab selector to horizontally scrollable content-sized pills so
  Days/Groups is not truncated on phones, made the selected tab use the primary
  color, and refined app-limit editing with 5-minute slider steps plus -5/+5
  controls.
- Made the app-limit editor bottom sheet skip the partial state and open fully,
  with internal scrolling and navigation-bar padding so action buttons remain
  reachable without manually swiping the sheet upward.
- Added tab strip edge hints with a subtle fade and arrow marker so users can
  tell when more tabs are available to the left or right.
- Moved policy editing into a ViewModel-owned draft state shared by Days/Groups
  and Apps, so unsaved changes persist across tabs and saving always uses the
  same draft.
- Strengthened policy budget validation by checking app-limit totals and group
  budget totals against each individual weekday limit, with overflow days shown
  in the save card.
- Reworked today's usage collection to use local-day UsageEvents foreground
  sessions only, avoiding `totalTimeInForeground` fallback over-counting and
  lowering the visible threshold to 10 seconds for easier validation.
- Hardened background policy checks: WorkManager now skips alerts while Safe
  Mode or Policy Enforcement is inactive, records each warning/exceeded alert
  once per day and target, posts notifications only for newly recorded alerts,
  and resets alert keys when the event log is cleared.
- Added a Safety-tab block screen preview that renders the would-block app,
  remaining time, and parent PIN UI without launching or enforcing any real
  block.
- Added a non-exported `BlockedActivity` skeleton for the future blocking MVP,
  including emergency PIN unlock and Kill Switch escape paths, and wired it only
  to a manual Safety-tab preview button.
- Expanded the never-block SafetyGate whitelist to include system UI, launchers,
  keyboards, dialer/phone, messaging, and permission controller packages, while
  leaving AccessibilityService block-screen launch intentionally disconnected.
- Stopped keyboard/system helper packages from updating the Safety tab's recent
  detection card, including stale persisted detection values.
- Improved policy editing before blocking: daily limits, group budgets, and app
  limits now share 5-minute controls with direct minute entry; selected group
  apps and limited apps sort first; policy saving uses a fixed bottom Admin PIN
  dock; app limits cannot exceed their group budget; policy summary now separates
  app groups from app limits with group detail sheets and consistent gray gauge
  rails.
- Improved pre-blocking validation behavior: UsageStats refresh now clears stale
  usage immediately when permission is missing, policy alerts are evaluated
  through one shared Safe-Mode-aware runner, refresh/save can trigger immediate
  warning or exceeded alerts, accessibility/helper packages are hidden from
  recent detection, and the recent detection card explains that only block-
  evaluable user apps are shown.
- Improved Phase 7 smoothness: tab changes now use a lightweight fade/slide
  transition, app icons cache their bitmap conversion per package, and foreground
  refreshes are throttled so returning to the app avoids redundant installed-app
  and usage refresh work.

## 2026-06-08

- Applied refined UI tokens for background, surface, primary, safe, warning,
  exceeded, and border colors.
- Updated Overview cards with a usage progress ring, semantic progress bars,
  and a top-app Today Usage presentation.
- Reworked daily policy editing from stacked numeric fields into day chips,
  a 15-minute-step slider, and weekday/weekend quick apply actions.
- Improved app group controls with horizontally scrollable group chips and
  fixed newly added groups to become selected immediately.
- Restored damaged Korean Safety strings and reduced English-only labels in
  the default Korean UI.
- Further aligned Overview and Policy screens with the reference mobile UI:
  larger header, pill tabs, oversized rounded cards, circular daily limit chips,
  policy action pills, app group summary chips, and searchable app limit rows.
- Tuned mobile UI proportions after visual review: reduced oversized dashboard
  typography, prevented status metrics and policy buttons from wrapping,
  restored safe ASCII/Canvas icons, and adjusted slider colors and card spacing.
- Reworked the redesign pass to match the supplied UI/UX specification more
  closely: restored 20dp gutters, 16dp card gaps, 24dp cards, 36dp icon buttons,
  equal-width day chips, muted pill search, compact app rows, and removed Admin
  PIN controls from the Policy tab.
- Applied the comparison review priorities: visible over-spill progress bars,
  smaller ring center typography, neutral Today Usage secondary bars, Material
  Slider without dotted ticks, two-line quick action buttons, flat App Limits
  list rows with dividers, unified Policy Summary app rows, and tabular number
  font features.

## 2026-06-07

- Initialized Git repository.
- Created initial Android Studio project baseline commit.
- Added project documentation structure.
- Added Developer Safe Mode requirements and safety rules.
- Added DataStore-backed policy enforcement safety flag.
- Added offline Emergency Unlock PIN path.
- Updated Kill Switch to force Safe Mode and disable policy enforcement.
- Added Auto Recovery startup detection that restores Safe Mode after an
  unclean previous run.
- Added UsageStatsManager permission flow and today app usage display.
- Added stored weekday and weekend total usage limit settings.
- Added stored MVP app group budget settings.
- Kept policy enforcement disabled while limit settings are being introduced.
- Improved Today Usage list to show app labels and icons instead of raw package
  names when possible.
- Filtered system apps, updated system apps, and Screen Time Manager itself out
  of the Today Usage list.
- Added in-app Korean and English language selection with Korean as the default.
- Added installed-app checkbox selection for app group budgets.
- Replaced direct package-name entry in the app group UI with user-facing app
  name and icon rows.
- Added stored per-app usage limit settings.
- Added policy summary calculations for total, group, and per-app limits.
- Added Monday through Sunday total usage limit settings.
- Added normal, warning, and exceeded status calculation for policy summaries.
- Added in-app warning and exceeded counts without notification or blocking.
- Simplified the main UI into Overview, Policy, and Safety tabs.
- Reduced the default screen to status, policy summary, and today's usage.
- Moved emergency controls into the Safety tab and policy editing into the
  Policy tab.
- Added internal event log for safety and policy events.
- Added Android notification channel and warning/exceeded notification helper.
- Added WorkManager periodic policy checks without blocking.
- Added separate Admin PIN and Emergency PIN update flows.
- Added SafetyGate whitelist code for future blocking decisions.
- Added AccessibilityService design document without implementing the service.
- Refined the main Compose UI with a cleaner commercial-style dashboard,
  segmented navigation, status badges, progress summaries, and a custom app
  color system.
- Added Blocking Readiness checks for accessibility settings, Safe Mode,
  Usage Access, whitelist, Emergency Unlock, and Kill Switch.
- Added Block Decision Simulation to preview which apps would be blocked
  before implementing any real blocking behavior.
- Added adaptive phone/tablet Compose layout with a max content width,
  two-pane expanded screens, and phone/tablet previews.
- Improved tab switching responsiveness by rendering long app lists with
  bounded lazy lists instead of composing every installed app row at once.
- Added multi app group policy storage with Add group and Delete group
  controls while keeping the existing single-group settings compatible.
- Added a no-op AccessibilityService that appears in Android Accessibility
  settings, detects foreground app package changes, respects Safe Mode and the
  whitelist, calls SafetyGate, and logs would-block decisions without enforcing
  any block.
- Added a Policy Enforcement safety switch and connected it to the
  AccessibilityService return order.
- Added latest foreground detection status storage and Safety tab display.
- Moved usage stats and installed-app refresh work off the main thread to
  reduce app resume and tab-switch stalls.
- Updated foreground detection so returning to Screen Time Manager does not
  overwrite the last external app detection.
- Filtered launchers, keyboards, setup/system UI packages, and other
  non-user-facing system components out of usage and policy app lists.
- Strengthened real blocking behavior: the AccessibilityService now listens to
  additional window-content and focus events for PIP/multi-window detection,
  falls back to active window package detection, re-blocks app re-entry faster,
  throttles duplicate evaluations, and the blocking screen sends real Back/Home
  exits to the launcher instead of returning to the blocked app.
- Added a short media pause guard after opening the blocking screen so video
  apps that enter PIP on block are more likely to stop playback even if the user
  taps play again during the guard window.
- Changed real blocking to send Home before opening the blocking screen, then
  retry the block launch once shortly after, so full-screen games and immersive
  apps are less likely to remain visible or playable behind the block.
- Prevented PIP windows from repeatedly relaunching the blocked screen while
  Screen Time Manager's blocked screen is already the active or focused window.
- Added a launcher Home intent fallback in addition to the accessibility Home
  action before launching the blocked screen, improving behavior for games that
  ignore or delay the global Home action.
- Switched real blocking to a full-screen accessibility overlay as the primary
  path for games and immersive apps, with app overlay and `BlockedActivity`
  fallbacks. The overlay keeps Emergency Unlock and Kill Switch visible.
- Added a blocking overlay guard that periodically verifies the overlay remains
  attached while the target is still blocked and restores it if the system
  detaches it.
- Added a foreground enforcement loop so games and video apps can be blocked
  while already running, even when no new accessibility window event is emitted.
