# Optional accounts (Issue #65)

The app still starts at Home. Settings → Account offers registration and login; playing does not require either. Account screens reuse the Compose Material 3 theme, spacing, buttons, ViewModel factories and existing Navigation graph.

## Architecture and Firebase audit

- `DeadlineApp` owns one `AppContainer`. Firebase's normal Android initialization and the default Auth, Firestore and Remote Config instances remain in use. No new Firebase project or instance is introduced.
- The Google Services plugin is applied to `app`; `app/google-services.json` is present in the audited checkout and has a client matching `com.comp90018.deadline`. Its contents were neither printed nor changed. The file was already tracked before this issue. Console provider settings and deployed rules cannot be established from this file.
- Existing versions remain unchanged: Firebase BoM 33.7.0, Google Services 4.4.2, AGP 8.5.1, Kotlin 1.9.0, Compose compiler 1.5.1, Compose BoM 2024.04.01, Navigation 2.7.7, DataStore 1.1.1. No new dependencies.
- `AuthRepositoryImpl` extends the existing anonymous sign-in implementation. `AccountAuthClient` and `ProfileStore` are test seams; only Firebase data sources call the SDK. Compose delegates to `AccountViewModel`.
- Existing `ProgressRepositoryImpl`, `SettingsRepositoryImpl`, `ProgressSyncManager`, `ProgressTransferService` and leaderboard repositories remain in use. CI checks are `ktlintCheck`, `:app:testDebugUnitTest`, `:app:assembleDebug` and `:app:lintDebug`.
- Firebase network/auth errors do not gate gameplay. The installed app still needs a valid Google Services configuration to initialize its SDKs; a missing configuration file is a build/setup problem, not the offline-player state.

## Account lifecycle and isolation

1. Startup reuses the existing Firebase user. If absent, the existing shared anonymous sign-in operation retries without creating concurrent identities. A failed attempt leaves a local guest playable.
2. Registration validates nickname, email, password length and confirmation. An anonymous user is upgraded with `EmailAuthProvider.getCredential` and `FirebaseUser.linkWithCredential`. Linking must preserve the UID; failure never falls back to creating or signing into another account. Only a player with no Firebase user uses `createUserWithEmailAndPassword`.
3. Login uses `signInWithEmailAndPassword`. A failed login keeps the current namespace. A successful login selects only the target UID's local cache, loads its private profile and runs the existing cloud merge. Guest progress is **not** automatically uploaded to an existing registered account.
4. Logout first selects the local guest namespace, then signs out. The UI attempts anonymous recovery; failure leaves an offline guest. Registered progress remains isolated on disk and in Firestore, available after logging back into that UID.
5. Sound, haptics and sensor preferences remain device settings. Nickname and all progress fields are account-specific. No password, credential or token is written to DataStore or Firestore. Password fields use non-saveable Compose memory and clear after authentication; they are not placed in navigation arguments or SavedStateHandle.

`AccountDataStore` is a view over the **same** Preferences DataStore file, not a second progress store. It namespaces the existing four progress keys and nickname by UID, using `account_owner` as the selection. A first-use transaction adopts legacy unscoped data for the current identity. Migration atomically merges completed levels, PBs and unlocked weeks with any existing destination; an existing nonblank target nickname wins. Repeating migration does not replace newer target progress. Switching UIDs leaves the old namespace intact. Offline local guest data is moved into a newly created anonymous/registered identity only on that creation path, never on login. Subsequent upgrades of an anonymous identity need no migration because its UID is unchanged.

The selected owner is checked against Firebase and local selection before private reads/writes, and again after a suspended write transform. `Account.storageReady` explicitly marks isolated local setup failures while UID/email continue to reflect Firebase. A transition immediately hides the previous namespace, including if activating the new namespace fails. The shared `SessionGate` serializes authentication changes, completion recording, sync, transfer operations and settings writes. Nested calls in the owning coroutine share the gate. A child coroutine cannot inherit gate ownership; it fails fast instead of bypassing the mutex. Transfer requests capture and recheck their intended UID after acquiring the gate. A session-version check preserves queued completions across first guest-UID acquisition and credential linking, while invalidating work across login/logout. A completion queued for a different player session is discarded rather than credited to the wrong account; already persisted progress remains in its original namespace. Account SDK operations run in the application scope so leaving/destroying a form cannot interrupt local reconciliation after Firebase has switched users.

To explicitly move guest progress into an existing account, first create a transfer code in Settings while online, then log into that account and redeem the code in Settings. This reuses the existing expiring transfer-code flow and merge policy. An abandoned anonymous UID remains archived locally, but has no new account-picker or automatic recovery UI; create a transfer code or register before switching if you need to retrieve that guest's progress.

## Firestore schema and merging

New private document: `users/{uid}` with exactly:

| Field | Value |
| --- | --- |
| `nickname` | 1–20 characters, nonblank |
| `email` | Auth user's email, private |
| `accountType` | `registered` |
| `createdAt` | server timestamp, immutable after creation |
| `lastLoginAt` | server timestamp on profile login/refresh |
| `updatedAt` | server timestamp on each write |

Profile initialization uses a transaction and merge writes. It preserves `createdAt`, existing nickname when no edit was requested, and every other collection. If Auth succeeds but the profile write fails, the UI explicitly says the player is signed in and offers profile retry. A registration nickname is saved locally first and is the fallback when retrying creation of a missing profile. Existing remote profiles remain authoritative on normal login/refresh. Authentication success remains success when progress sync returns offline/refused/failed, throws, or is cancelled. The UI displays separate local/profile/progress pending states and offers **Retry Account Sync**. Progress sync does not disable Continue Playing. Automatic success navigation waits until pending warnings are resolved so they are not lost when replacing the form.

Existing paths remain unchanged:

- `progress/{uid}` — same transaction and progress format, not moved under `users`.
- `leaderboard/{levelId}/entries/{uid}` — same public nickname/score format, never profile email.
- `transfers/{code}` — existing explicit progress transfer.

Same-UID progress merges use union of completed levels, minimum valid PB time (earlier timestamp on ties), maximum unlocked semester week and latest modification time. Cloud reads and merges accept only `level_1` through `level_6`. There are **six levels covering twelve semester weeks**: `highestUnlockedWeek` remains 1–12. It must not be clamped to six. The generic local serializer retains its existing round-trip behavior; obsolete cloud level IDs are filtered at restoration/merge boundaries.

Nickname edits apply to the private profile and local nickname used by future leaderboard submissions. Historical leaderboard entries retain their recorded nickname until a faster score replaces them, preserving existing score-only update rules. Linking preserves their UID and identity.

## Security rules

`firestore.rules` adds owner-only single-document reads/writes for `users/{uid}`. Profile collection listing and deletion are denied. Fields are allowlisted; email must match the Firebase Auth token; nickname and timestamps are validated; creation time cannot change. Existing progress, transfer and leaderboard ownership permissions are retained. Private profile writes require a matching linked email identity, rather than assuming the token sign-in provider describes all linked providers. Progress/transfer PBs now use the same one-day upper bound as leaderboard entries. Email is never copied into leaderboard documents. No rules were deployed by this implementation.

## Manual Firebase Console setup

1. Open the **existing** Firebase project used by this checkout. In Project settings → General → Your apps, verify the Android app package is `com.comp90018.deadline`. If configuration must be refreshed, download that Android app's `google-services.json` into `app/google-services.json`; do not print it or add credentials/service-account keys.
2. Open Authentication → Sign-in method (the Console may group Authentication under Security). Keep **Anonymous** enabled. Enable **Email/Password**, then Save. Email-link sign-in is not used.
3. Review Authentication → Settings → Password policy. The client checks the six-character Firebase minimum; any stronger project policy is enforced by Firebase and shown as a password error. Choose and communicate the project's intended policy.
4. Open Firestore Database in this same project. Keep the existing database and collections. Review this checkout's `firestore.rules`, then manually publish it in the Rules tab after your approval/testing. Alternatively, after selecting and verifying the existing project, an authorized operator may run `firebase deploy --only firestore:rules --project <existing-project-id>`. This implementation did not run that command.
5. No new indexes, collection migrations, service accounts, OAuth SHA fingerprints or manually created profile documents are needed for these email/password flows.
6. Smoke test a guest win → registration (same UID), logout → fresh guest, login → restored progress, nickname edit, airplane-mode gameplay/logout, and an already-registered-email linking failure. Inspect private profiles only as an authorized project operator.

Firebase references: [Android account linking](https://firebase.google.com/docs/auth/android/account-linking), [email/password setup](https://firebase.google.com/docs/auth/android/password-auth).

## Verification

Use JDK 17 and the existing Android SDK, then run:

```text
gradlew.bat ktlintCheck :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
gradlew.bat :app:compileEmulatorAndroidTestKotlin
```

New JVM tests cover validation, linking versus creation, UID/progress preservation, link/login failure, profile loading/retry and nickname updates, offline logout/recovery, UID isolation, DataStore restart, mismatched owner protection, session serialization, duplicate UI submissions, success/error states and obsolete-level filtering.

`FirestoreProfileEmulatorTest` uses the local emulator harness to test real linking, profile idempotency, private access, forged email/extra-field rejection and preserved progress. `AccountScreenTest` covers guest continuation, registered state, busy state and error visibility. Instrumentation uses the isolated `emulator` build and requires an Android Emulator able to reach local Auth and Firestore; missing services fail closed. See [safe Android instrumentation](issue-65-android-isolation.md) for the repeatable command. Compilation alone does not establish that these tests pass. No real credentials are used.

Execution results for the initial implementation are recorded in the implementation report; the subsequent fixes and current check results are in [the hardening report](issue-65-hardening-report.md). Production Console configuration, deployed rules, device UI behavior, process termination during an SDK operation and real multi-device/offline recovery remain manual integration checks.


## Recovery boundaries

A new identity's offline-guest migration intent is retained in memory through local-write failures and is consumed only by successful activation. A delayed anonymous request that finds an existing signed-in user must not mark that user as newly created. Migration itself is one DataStore transaction, so an interrupted commit retains both source and target.

If the process dies after Firebase creates an identity but before local migration commits, startup cannot safely prove the source's ownership from a new UID alone. It selects the authenticated UID without automatically importing the retained offline guest namespace. The source is preserved; automatic cross-process recovery of that ambiguous association is intentionally not implemented. Explicit migration requires confirmed ownership (the existing transfer workflow when available, or a reviewed recovery), never guessing from the current login.

Do not launch and await separate coroutine jobs that reacquire SessionGate while already holding it. Firebase Auth operations keep the gate until they settle; cancelling the underlying Android Task would not reliably cancel the identity change. Network profile/progress calls remain bounded, and progress syncing no longer blocks account-screen continuation. Device/process-death tests are still needed for release confidence.
