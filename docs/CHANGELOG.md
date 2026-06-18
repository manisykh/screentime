# Changelog

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
