# Issue #65 — second-pass review and hardening

Work remained on `feature/issue-65-user-auth`. The initial staged implementation and existing review bundles were preserved; second-pass edits are unstaged. No commit, push, PR or Firebase deployment was performed.

## Confirmed defects and fixes

| File | Failure path found | Fix |
| --- | --- | --- |
| `AccountDataStore.kt` | Migration copied source keys over an existing target, potentially lowering unlocked progress/PBs and replacing its nickname. | Reuse the existing progress codec and conflict resolver inside the same DataStore transaction. Union completions, keep faster PBs and higher weeks; retain a nonblank target nickname. Remove source keys only in the successful transaction. Repeated migration is idempotent. |
| `AccountDataStore.kt` | After failed activation, selection could hide the old account while `updateData` still accepted its Firebase UID. The resulting empty view could overwrite/delete old private fields. A suspended transform also had no second ownership check. | Check both local selection and Firebase identity before the transform and after suspension. Reject the write atomically when either changes. |
| `AuthRepositoryImpl.kt` | Firebase success followed by activation/nickname persistence failure returned a generic failure without an explicit local-setup state. A failed logout activation could leave the account displayed normally while its storage was hidden. | Publish actual Firebase UID/email with `storageReady=false` during setup/failure, clear cached nickname on storage failure, distinguish post-authentication setup failures, and retry setup without repeating registration. Logout failure retains the actual identity and exposes recovery. |
| `AuthRepositoryImpl.kt` | `signInOnce()` always requested guest migration, even if a delayed anonymous request found a different existing login. A failed migration also lost the new-identity migration flag on retry. | Only the operation that actually creates an identity records its UID as eligible for guest migration. Keep that intent through local failure; consume it after activation. Existing-account reuse and normal login never infer migration permission. |
| `SessionGate.kt` | The coroutine context marker was inherited by children; a child could bypass the mutex as if it owned the gate. | Bind reentrancy to the owning coroutine Job. Ordinary nested calls still work; inherited child ownership fails fast. |
| `AppContainer.kt` | Transfer creation/redemption could obtain one UID, wait for the gate, then run under a successor identity. A raw UID guard also discarded a queued offline guest win when that same guest acquired its first anonymous UID. | Capture/recheck transfer UID after gate acquisition. Use a session version for completions: first guest-UID creation and linking preserve it, while login/logout invalidate old work. Do not submit a leaderboard score when its local save failed. |
| `AccountViewModel.kt`, `AppContainer.kt` | A sync exception/cancellation could erase successful authentication state, and the container discarded the actual `SyncResult`, hiding offline/refused results. Sync kept continuation disabled. | Return the real sync result. Separate authentication result from local/profile/progress pending states. Sync exceptions/cancellation retain success; rethrow cancellation correctly. Progress sync runs with `busy=false`, retaining duplicate-operation protection separately. |
| `AccountScreen.kt` | Replacing a form immediately could discard its pending-sync warning; returning to Profile also started a redundant profile refresh and blocked continuation again. | Navigate only when successful setup/sync has no pending warnings. Keep the signed-in account and retry actions visible otherwise. Remove the redundant automatic refresh; startup/login already load profiles. Keep Continue Playing enabled during progress sync and expose recovery for local setup failure. |
| `firestore.rules` | Progress/transfer personal bests had a lower time bound but lacked the existing leaderboard's one-day upper bound. Profile registration depended implicitly on the email claim. | Apply the same PB upper bound and explicitly require the profile email to match a linked email identity as well as the token email. Preserve paths and all valid owner/leaderboard/transfer access. |

`ProgressDataSource.kt` only exposes its existing read/write codec internally for the atomic migration; it does not introduce a new serializer or progress store. Six levels still span twelve semester weeks. Existing cloud paths and merge semantics remain compatible.

## Risks investigated and limits retained

- **Wrong-UID late responses:** account changes remain serialized with profile/progress/leaderboard/transfer work. Existing sync merges and SDK document paths remain UID-specific. Tests cover gate ordering, stale queued requests and failed activation. No wrong-UID write was observed in the JVM tests. Real Firebase/device concurrency remains unexecuted.
- **Nested gate deadlocks:** current production nested calls stay in the owning coroutine and pass the suite. A child cannot inherit ownership now. Do not launch and await an independently scoped job that reacquires the gate while holding it; normal mutex deadlock rules still apply.
- **Long-running authentication:** Firebase Auth tasks still hold the gate until they settle. Cancelling an Android Task await does not reliably cancel the underlying identity change, so this review does not release the gate prematurely. Profile/progress network waits remain bounded; UI progress syncing no longer blocks continuation. Device-level latency and process interruption need manual testing.
- **Process death between Firebase creation and local commit:** the DataStore migration is atomic and retains its source on failure. Within the running process, retry remembers the newly created UID. After process death, a new instance cannot prove that an unowned offline guest namespace belongs to the current UID. Startup therefore selects the authenticated namespace without importing the retained source. Ambiguous cross-process recovery needs explicit ownership confirmation; automatic guessing is intentionally not implemented.
- **Legacy cloud documents containing obsolete level IDs:** normal client reads/merges filter them, while rules retain their existing monotonic-update protection. A server document from an older incompatible schema can therefore require a separately reviewed server repair. No such production data was accessed or reproduced, and no destructive migration was added.
- **Passwords/navigation:** credentials remain in non-saveable Compose state/call arguments only. No DataStore, SavedStateHandle, navigation argument or Firestore credential storage was added. The existing Home start route and Material 3 components are unchanged.

## Security Rules review

Reviewed the entire rules file, including progress, leaderboards and transfers. Owner checks, field allowlists, known levels, corresponding semester weeks, server timestamps, monotonic progress, faster-only leaderboard updates, transfer expiry and collection-list restrictions remain intact. Profile creation/update preserves immutable `createdAt`, validates email/nickname/account type, and allows server-timestamp profile transactions.

`String.trim()` is an actual [Rules String method](https://firebase.google.com/docs/reference/rules/rules.String), and Rules supports [short-circuit boolean evaluation](https://firebase.google.com/docs/rules/rules-language). These were investigated rather than replaced based on Kotlin/Java assumptions. A token's `sign_in_provider` describes how the token was obtained, while `firebase.identities` describes linked identities; the explicit registration check uses the latter so it does not assume a freshly linked account must already have a password sign-in token. See the [Auth claims reference](https://firebase.google.com/docs/rules/rules-and-auth).

**Rules are not emulator-verified in this environment.** Compilation of Android test code does not compile or exercise Firestore Rules. No rules were deployed.

## Regression coverage added

Added **21 JVM test cases** over the original 582:

- `AccountDataStoreTest`: populated destination merge/PB/nickname preservation, legacy merge, interrupted commit/restart, failed-selection write rejection, conservative login after interruption, suspended writes, and guest acquisition versus account-switch session versions.
- `AccountRepositoryTest`: Auth success then disk failure, retained new-identity migration intent, nickname persistence failure after linking, failed logout/recovery, profile failure without guest nickname leakage, and a delayed anonymous request finding an existing login.
- `AccountViewModelTest`: sync exception, sync cancellation, profile-pending success, local-setup-pending success, continuation during pending sync and duplicate prevention.
- New `SessionGateTest`: inherited child ownership rejection, queued transfer identity change, cancelled waiters and valid nested calls.

Expanded the compiled instrumentation tests:

- `FirestoreProfileEmulatorTest`: immutable login timestamp on nickname-only update, backdated timestamps, blank/wrong/oversized nickname, account type, extra fields, forged email, profile deletion, registered cross-user access and valid profile creation.
- `FirestoreRulesTest`: over-one-day progress PB rejection (shared validator also protects transfers).
- `AccountScreenTest`: password visibility semantics and continuation/retry during pending sync; scrolling makes existing continuation assertions work on smaller screens.

## Final quality results

Run on 12 October 2026 using the installed JDK 17, existing dependency versions and existing ktlint baselines:

| Check | Result |
| --- | --- |
| `ktlintCheck` | Passed |
| `:app:testDebugUnitTest` | Passed: **603 tests, 70 suites, 0 failures, 0 errors, 0 skipped** |
| `:app:assembleDebug` | Passed |
| `:app:lintDebug` | Passed: **0 errors, 52 warnings** |
| `:app:compileDebugAndroidTestKotlin` | Passed |
| `git diff --check` | Passed |
| Android UI/Firebase integration execution | **Not run** |

The device inventory was empty, `firebase` CLI was unavailable, and localhost ports 8080/9099 had no running emulator listeners. No production credentials or services were used to substitute for emulator testing. An initially mis-scoped new concurrency test and a missing Compose test import were corrected before the successful final run.

Log: `build/issue65-hardening-checks.log`. Standard reports: `app/build/reports/` and `app/build/test-results/testDebugUnitTest/`. Only explicit changed Kotlin paths were formatted; staged first-pass files and review archives were not reset or regenerated.

## Readiness

The branch is **ready for manual Firebase and device testing**, not a claim of production end-to-end verification. Follow [the existing Console setup](user-accounts.md#manual-firebase-console-setup), review the Rules changes, and run the local emulator tests before approving any deployment. Exercise airplane-mode login/logout, linking collision, low-storage recovery, process death during identity creation, multi-device merge and small-screen/back-navigation behavior. The conservative cross-process migration boundary above remains an explicit limitation.
