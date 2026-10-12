# Issue #65 implementation report

Implemented in the existing `feature/issue-65-user-auth` working tree. No branch creation, commit, push, pull request or Firebase deployment was performed.

## Audit and configuration

The existing application container, anonymous Auth repository, Firebase default instances, DataStore repositories, Firestore transaction-based progress sync, transfer codes and leaderboard formats were reused. Home remains the start destination; account management is optional through Settings. No gameplay logic or dependency versions changed.

The Google Services plugin, Firebase Auth/Firestore dependencies and matching Android client configuration are present. The existing `app/google-services.json` matches `com.comp90018.deadline`; its contents were not printed or modified. Whether Anonymous/Email-password providers are enabled and which security rules are deployed requires Console verification.

Full version inventory, architecture findings and manual setup: [user accounts](user-accounts.md).

## Behavior and data decisions

- Registration validates inputs and links an existing anonymous UID, preserving its local progress, cloud progress and leaderboard identity. Linking failure does not fall back to another account.
- A player without a Firebase identity can continue locally or register when connectivity returns. Existing signed-in sessions are reused.
- Login selects the target UID's local cache, loads/creates its private profile and runs the existing cloud merge. Failed login preserves guest data. A profile-sync failure after Auth success is reported as signed-in with a retry, rather than presenting it as failed credentials.
- Logout isolates account-specific progress/nickname and attempts anonymous recovery. Registered caches remain accessible only when that UID is selected again. Offline guest recovery failure does not require login.
- Per-UID namespaces live inside the existing DataStore file. A first-use transaction adopts legacy progress. Offline guest migration happens only when creating a new identity; login never automatically imports another UID's data. Existing transfer codes provide explicit migration.
- The session gate serializes account changes with sync, transfers, completion recording and settings writes. Pending work cannot merge the previous account's response into a new account.
- Merge still takes completed-level union, fastest PB and maximum unlocked week. Cloud restoration/merge filters obsolete level IDs. Six levels continue to span twelve weeks.
- Private profile schema: `users/{uid}` containing nickname, email, `accountType=registered`, and server-created `createdAt`, `lastLoginAt`, `updatedAt`. Existing `progress/{uid}`, leaderboard and transfer documents are not migrated.
- Security rules add owner-only profile access with field/type/timestamp/email validation, immutable creation time and no listing/deletion. Existing collection permissions remain. No credentials are stored in the new data model.

## Files

Paths below are relative to the repository root.

| Created | Purpose |
| --- | --- |
| `app/src/main/java/com/comp90018/deadline/domain/repository/AccountRepository.kt` | Account state, operations and validation |
| `app/src/main/java/com/comp90018/deadline/data/local/datastore/AccountDataStore.kt` | UID namespaces, legacy adoption and mismatched-owner protection |
| `app/src/main/java/com/comp90018/deadline/data/repository/SessionGate.kt` | Serialized account-sensitive work |
| `app/src/main/java/com/comp90018/deadline/data/remote/firebase/FirestoreProfileDataSource.kt` | Testable profile store and transactional Firebase implementation |
| `app/src/main/java/com/comp90018/deadline/feature/account/AccountViewModel.kt` | Async account operations, duplicate prevention and error states |
| `app/src/main/java/com/comp90018/deadline/feature/account/AccountScreen.kt` | Profile/login/registration UI |
| `app/src/test/java/com/comp90018/deadline/data/repository/AccountRepositoryTest.kt` | Auth, profile and lifecycle regressions |
| `app/src/test/java/com/comp90018/deadline/data/local/AccountDataStoreTest.kt` | Restart/isolation regressions |
| `app/src/test/java/com/comp90018/deadline/feature/account/AccountViewModelTest.kt` | Optional access, busy/success/error states |
| `app/src/androidTest/java/com/comp90018/deadline/data/FirestoreProfileEmulatorTest.kt` | Real SDK linking and profile security tests |
| `app/src/androidTest/java/com/comp90018/deadline/feature/account/AccountScreenTest.kt` | Compose account states and guest continuation |
| `docs/user-accounts.md` | Lifecycle, schema, migration, setup and testing guide |
| `docs/issue-65-report.md` | This report |

| Modified | Purpose |
| --- | --- |
| `app/src/main/java/com/comp90018/deadline/app/AppContainer.kt` | Wire existing repositories to account storage, profiles and session gate |
| `app/src/main/java/com/comp90018/deadline/data/repository/AuthRepositoryImpl.kt` | Extend existing anonymous repository with account lifecycle |
| `app/src/main/java/com/comp90018/deadline/data/remote/firebase/FirebaseAuthDataSource.kt` | Credential linking, create/login/logout SDK calls |
| `app/src/main/java/com/comp90018/deadline/data/remote/firebase/ProgressFields.kt` | Filter obsolete cloud levels |
| `app/src/main/java/com/comp90018/deadline/data/sync/ConflictResolver.kt` | Keep merged level IDs within six current levels |
| `app/src/main/java/com/comp90018/deadline/feature/settings/SettingsScreen.kt` | Account entry/state; registered nickname editing through Account |
| `app/src/main/java/com/comp90018/deadline/navigation/Routes.kt` | Account/login/register routes |
| `app/src/main/java/com/comp90018/deadline/navigation/AppNavHost.kt` | Route wiring and removal of stale Settings/account forms from back stack |
| `app/src/test/java/com/comp90018/deadline/data/sync/ConflictResolverTest.kt` | Six-level/twelve-week regression |
| `firestore.rules` | Private-profile permissions and validation |
| `docs/firestore-security.md` | Updated collection/privacy description |
| `README.md` | Link to account documentation |

## Checks executed on 12 October 2026 (Sydney)

Used the installed JDK 17 without editing machine Java settings or private IDE configuration. The shell default was Java 8; Gradle required explicit `JAVA_HOME` for this run and access to its existing cache.

| Check | Result |
| --- | --- |
| `ktlintCheck` | Passed with the existing baselines |
| `:app:testDebugUnitTest` | Passed: 582 tests across 69 suites, 0 failures/errors/skips |
| `:app:assembleDebug` | Passed; APK at `app/build/outputs/apk/debug/app-debug.apk` |
| `:app:lintDebug` | Passed: 0 errors, 52 warnings |
| `:app:compileDebugAndroidTestKotlin` | Passed; includes new Compose/Firebase emulator tests |
| `git diff --check` | Passed |
| Android UI/Firebase emulator execution | Not run: `adb devices` returned no connected devices; no device/emulator session available |
| Production/real multi-device end-to-end tests | Not run; no Console configuration/deployment or real credentials used |

Quality log: `build/reports/issue65/quality.log`. Gradle's usual XML/HTML test and lint reports are under `app/build/`.

## Required manual setup and remaining limits

Follow the numbered [Firebase Console setup](user-accounts.md#manual-firebase-console-setup): select the existing project, verify package/config, keep Anonymous enabled, enable Email/Password, review password policy, then manually approve/publish the reviewed rules. No new indexes or data migration are needed.

Run the compiled device/emulator tests and the documented airplane-mode, process-restart, linking-failure and multi-device smoke scenarios before release. Existing historical leaderboard rows retain their recorded nickname until a faster result replaces them. An abandoned guest namespace has no account-picker UI; use registration or a transfer code before switching accounts if recovery is required. Local caches survive logout but are removed by clearing app data/uninstalling; cloud records survive logout.

The formatter initially touched seven unrelated existing instrumentation tests. Their exact formatting-only diffs were inspected and removed; those files are unchanged in the final tree. No unrelated baseline or formatting changes are included.
