# Screen Time Manager

## Project Goal

Screen Time Manager is a digital wellbeing app for Android tablets and phones.
Its purpose is to help manage device usage without ever risking permanent
self-lockout.

## Core Features

- Total device usage time limits
- Per-app usage time limits
- App group usage time limits
- Weekday and weekend policies
- Blocking screen
- Parent management controls
- Usage budget controls

## MVP Scope

1. Developer Safe Mode is visible and enabled by default.
2. Safe Mode state is persisted with DataStore.
3. Emergency Unlock and Kill Switch are available before blocking features.
4. UsageStatsManager-based usage summary is added after safety controls.
5. AccessibilityService is implemented only after Safe Mode, Emergency Unlock,
   Kill Switch, Auto Recovery, and whitelist rules are documented and present.

## Roadmap

### Phase 0: Project Baseline

- Initialize Git repository.
- Commit the initial Android Studio project.
- Add project, architecture, safety, and changelog documentation.

### Phase 1: Safety Foundation

- Developer Safe Mode UI
- DataStore-backed Safe Mode state
- Kill Switch action
- Emergency Unlock design
- Auto Recovery entry points
- Accessibility whitelist policy

### Phase 2: Usage Visibility

- UsageStatsManager permission flow
- Total device usage summary
- Per-app usage summary

### Phase 3: Limits and Budgets

- Total usage limits
- Per-app limits
- App group budgets
- Weekday and weekend schedules

### Phase 4: Blocking

- AccessibilityService
- Blocking screen
- Parent PIN unlock

Blocking must not be implemented before the Phase 1 safety foundation.

## Current Implementation Status

- Phase 0 complete.
- Developer Safe Mode UI complete.
- Safe Mode persistence with DataStore complete.
- Offline Emergency Unlock initial PIN path complete.
- Kill Switch recovery path complete.
- Auto Recovery startup detection complete.
- UsageStatsManager permission and today usage display complete.
- Total usage limit settings are stored with Admin PIN verification but not enforced.
- Multiple app groups can be created, edited, and stored but not enforced.
- App group apps can be selected from installed launchable apps.
- Per-app usage limit settings are stored with Admin PIN verification but not enforced.
- Policy summary shows total, app group, and per-app usage against limits.
- Day-of-week total usage limits are stored but not enforced.
- Policy summary classifies each limit as normal, warning, or exceeded.
- Warning and exceeded states are displayed in-app only.
- Main UI is organized into Overview, Policy, and Safety tabs.
- Internal event log records safety and policy events.
- Android notifications can be requested for warning/exceeded policy states.
- WorkManager periodically checks usage policy state without blocking.
- Admin PIN and Emergency PIN are separated.
- Shared blocking safety gate is implemented for Safe Mode, Policy Enforcement,
  and never-block packages.
- AccessibilityService is implemented as no-op detection only; it does not block.
- Blocking screen is not implemented yet.
