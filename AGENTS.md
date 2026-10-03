# ScreenRest repository instructions

## Production release checks

When the user asks for a release review, production deployment, store submission,
or launch readiness check, treat the following as release blockers:

- `deleteCurrentUserData` or `cleanupDuplicateChildLinks` is deployed with
  `enforceAppCheck: false`.
- The release Android build is not using the Play Integrity App Check provider.
- The production signing certificate SHA-256 is not registered for the Firebase
  Android app and Play Integrity.
- Changed Firestore rules or callable functions have not been validated and
  deployed to the intended production Firebase project.

Before a production release, change both sensitive callable functions to
`enforceAppCheck: true`. Enable `consumeAppCheckToken: true` for
`deleteCurrentUserData` when the release client requests limited-use App Check
tokens, then deploy and verify valid App Check requests in production metrics.

The current `enforceAppCheck: false` values are an intentional development-only
exception for repeated uninstall/reinstall testing. Never describe the app as
production-ready while that exception remains.
