# Issue 65: Guest migration recovery

The offline Guest uses the fixed `local-guest` namespace. Before Firebase may create
an anonymous or email/password identity, `AccountDataStore.prepare()` commits a
journal source under `guest_migration/source`. Only a successful identity-creation
result may confirm `guest_migration/destination`; confirmation checks that the
returned UID is still Firebase's current UID. Login never confirms a migration.
The journal stores namespace/UID identifiers only, not credentials.

On startup and Retry Account Setup, activation reads this journal from the same
Preferences DataStore file. A bound intent is resumed only when its source is
`local-guest`, the stored owner is still that source, and its destination exactly
matches the current Firebase UID. No namespace discovery is used. Source and target
progress are merged losslessly. The destination write, source removal, owner switch,
and journal acknowledgement are a single atomic DataStore transaction. A failure
before commit preserves the source and intent; a lost caller acknowledgement after
commit leaves an already-completed, safely repeatable operation.

There is no atomic transaction spanning Firebase Auth and local DataStore. If a
process dies after Firebase saves its UID but before the app confirms that UID in
the journal, the app must not infer ownership from an anonymous flag or an email.
It reports storage as not ready. Retry Account Setup remains safe but cannot prove
an unbound destination. Account offers **Sign out and recover saved Guest**: this
explicit action validates the fixed saved source, signs out, and clears the intent
without copying any current account's data. The saved offline Guest remains playable;
a subsequent identity creation can start a fresh journal. Registered namespaces are
untouched. A bound intent with an unrelated current UID is handled the same way.

All application auth operations and progress session changes remain serialized by
SessionGate. Startup no longer selects a Firebase namespace before repository recovery.
The production AppContainer supplies the durable journal; repository-only tests may
omit it when exercising authentication independently of local storage. The in-memory
pending UID is only the current process's successful SDK result, allowing a failed
confirmation write to retry. It is not the authority for recovery after restart.

Explicit login from an offline Guest cancels a prepared intent before signing in,
while retaining the Guest source. Login selects only the existing account namespace.
Logout, linking, Firestore filtering and other account isolation behavior are unchanged.

## Regression checks

`AccountRepositoryTest.anonymousUidPersistedBeforeMigrationRecoversAfterRealFileRestart`
is an equivalent reproduction, not the reviewer's original test. It failed on the
pre-fix implementation: Retry Account Setup succeeded but restored no `offline_level`.
It uses the production DataStore factory, closes/joins its scope, then opens the same
file with new store and repository instances. Firebase identity is simulated separately.
`GuestMigrationRestartTest` covers intent ordering, pre-confirmation interruption,
explicit recovery, interrupted atomic commit, lost acknowledgement, repeated restarts,
unrelated identities, registered-account isolation, empty install, preflight failure,
direct registration, UID-preserving link, login and confirmation retries.

Run with Java 17:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:testReleaseUnitTest :app:ktlintCheck :app:assembleDebug :app:lintDebug --continue --console=plain
.\gradlew.bat :app:testDebugUnitTest --rerun --tests '*GuestMigrationRestartTest' --tests '*AccountRepositoryTest.anonymousUidPersistedBeforeMigrationRecoversAfterRealFileRestart' --console=plain
python scripts/test_firebase_emulator_rules.py
.\scripts\run_firebase_android_tests.ps1 -Serial emulator-5554
```

The emulator commands require the demo-only prerequisites documented in
[scripts/README.md](../../scripts/README.md). They must not target production Firebase.
These restart checks simulate process loss by reconstructing instances over a real file;
they do not claim to kill/restart a real Android process during Firebase SDK persistence.
An already-orphaned namespace from an older app without a journal cannot be safely
assigned to a Firebase UID automatically.

## Initial verification on Windows / Java 17 / Android API 35

- Pre-fix equivalent regression: 1 executed, 1 failed (`expected [offline_level], actual []`).
- Debug JVM: 627 passed, 0 failed/errors/skipped.
- Release JVM: 627 passed, 0 failed/errors/skipped.
- Fourteen real-file restart checks repeated in five fresh Gradle invocations:
  70 passed, 0 failed. `--rerun` forced actual test execution.
- Kotlin style, debug assembly and Android lint passed. Lint reported 38 warnings
  and 3 informational findings, no errors. Kotlin style also passed in the actual checkout.
- Host-local Firebase rules suite: 125 passed, 0 failed/execution errors.
- Firebase Android selection: 50 executed, 49 passed, 1 failed.
- Focused Android follow-up: 8 executed, 7 account-screen checks passed,
  including the new explicit-recovery-action test; the transfer expiry retry failed.

At that stage, the Firebase failure was
`FirestoreTransferEmulatorTest.anExpiredCodeIsNotReadFromTheOfflineCache`.
The first run failed while creating the code (PERMISSION_DENIED); the isolated
retry created it but returned null on the first read, before testing offline cache
behavior. This existing test grants only three seconds until expiry. At that stage timing was a
suspected contributor; the final investigation below establishes the cause. Its assertions, expiry,
rules and transfer production code were not changed. That initial Android Firebase run must not be described as passing.

The checkout contained an unrelated malformed `ic_launcher_round.xml` edit, which
blocked resource compilation before any tests ran. It was preserved. Builds and JVM /
Android execution used `build/pr126-verification-workspace`, a separate local copy
with the committed launcher icon and the same changed Kotlin sources. No git merge,
checkout reset, production Firebase access, rules deployment or commit was performed.
Logs and XML evidence are under ignored `build/pr126-restart-*`. The checkout itself
still needs its launcher XML edit resolved before its full build can pass.

The focused Android follow-up used the verified emulator runner and package:

```powershell
$env:ANDROID_SERIAL='emulator-5554'
adb -s emulator-5554 shell cmd deviceidle tempwhitelist -d 600000 com.comp90018.deadline.emulator
# Run in a try/finally so the package exemption is removed even on test failure.
.\gradlew.bat :app:connectedEmulatorAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.comp90018.deadline.data.FirestoreTransferEmulatorTest#anExpiredCodeIsNotReadFromTheOfflineCache,com.comp90018.deadline.feature.account.AccountScreenTest' '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' --console=plain
adb -s emulator-5554 shell cmd deviceidle tempwhitelist -r com.comp90018.deadline.emulator
```

Changed files for this fix:

- `app/src/main/java/com/comp90018/deadline/app/AppContainer.kt`
- `app/src/main/java/com/comp90018/deadline/data/local/datastore/AccountDataStore.kt`
- `app/src/main/java/com/comp90018/deadline/data/local/datastore/GuestMigrationJournal.kt`
- `app/src/main/java/com/comp90018/deadline/data/repository/AuthRepositoryImpl.kt`
- `app/src/main/java/com/comp90018/deadline/domain/repository/AccountRepository.kt`
- `app/src/main/java/com/comp90018/deadline/feature/account/AccountScreen.kt`
- `app/src/main/java/com/comp90018/deadline/feature/account/AccountViewModel.kt`
- `app/src/test/java/com/comp90018/deadline/data/repository/AccountRepositoryTest.kt`
- `app/src/test/java/com/comp90018/deadline/data/repository/GuestMigrationRestartTest.kt`
- `app/src/androidTest/java/com/comp90018/deadline/feature/account/AccountScreenTest.kt`
- `docs/qa/issue-65-guest-recovery.md`

Suggested commit: `fix(auth): recover guest migration safely across process restarts`

## Final blocker diagnosis

A fresh, isolated execution of the original transfer test recorded:

- Start: 1791786084265 ms; requested expiry: 1791786087265 ms.
- Creation completed at 1791786086696 ms (2,431 ms elapsed).
- First read returned no progress at 1791786087453 ms (3,188 ms elapsed).
- The assertion expecting the initial valid PlayerProgress failed before the test
  disabled networking or evaluated the offline cache. In the original source this
  is line 124; line 115 is the runBlocking wrapper.

The three-second TTL raced the emulator/client round trips. The production reader
uses Source.SERVER and converts the rules' PERMISSION_DENIED into NotFound/null;
the rules require request.time to be earlier than expiresAt. The earlier create-time
PERMISSION_DENIED is also consistent with this deadline race. The measured reproduction
used one test with acknowledged database cleanup, successful creation, and separate
Firebase apps. No production expiry or cleanup defect was found.

The fix changes only instrumentation infrastructure:

- FirestoreTransferEmulatorTest creates a normally valid code, explicitly verifies
  the cached document, disables the reader's network, and expires the server fixture.
- FirebaseEmulator.expireTransferAsAdmin updates only expiresAt to the Unix epoch.
  The REST commit requires the document to already exist. It is hardcoded to the
  local demo project, validates the code, rejects redirects, and has bounded I/O.
- The cache must still contain the same document after expiry. All original offline
  read, Offline redemption, unchanged progress and online NotFound assertions remain.
- No sleeps, production clock changes, rule changes or production service calls were
  introduced. A wider initial validity window only prepares the fixture; expiry is
  explicitly controlled before exercising the behavior under test.

Raw diagnostic evidence is saved in ignored build/pr126-final-transfer-before.xml,
build/pr126-final-transfer-before.log and build/pr126-final-transfer-timing.log.
The earlier create-time exception is retained in
build/pr126-restart-firebase-android-first.xml. These contain the runner's assertion
and exception stack traces, including the Firestore PERMISSION_DENIED cause.

The launcher blocker is app/src/main/res/mipmap-anydpi/ic_launcher_round.xml, line 6:
`</adaptive-ico n>` instead of `</adaptive-icon>`. It is tracked but unstaged; the index
matches HEAD. XML parsing reports an invalid token at line 6, column 15, and Android
resource compilation reports a mismatched closing adaptive-icon tag. Its last-write
metadata was 2026-10-12 15:04:32 local time. Git/filesystem metadata cannot identify
which person or process made an uncommitted edit. The last committed icon change was
1c7e23c (campus backgrounds/app icon), unrelated to Issue 65. The malformed edit and
personal .idea/review artifacts remain untouched and excluded from the verification
checkout and recommended Issue 65 commit scope.

The isolated directory now has its own Git baseline at 87d0f54, plus only the Issue 65
changes. Its source files match the working checkout except for the committed, valid
launcher icon. No user branch or user working file was reset.

The final audit of the eleven Guest Recovery files found no new defect: journal source
is fixed and durable before identity creation; confirmation and restart activation
validate the exact Firebase UID; source removal and destination/owner/acknowledgement
commit atomically; retries are idempotent; mismatches fail closed; explicit recovery
signs out before selecting the saved source; registered namespaces are never imported.
The earlier ConflictResolver and ProgressFields fixes match HEAD unchanged, and their
regression tests remain in the full JVM suites. No Guest Recovery redesign was made.

Additional files in the final Issue 65 commit scope (13 total including the prior list):

- app/src/androidTest/java/com/comp90018/deadline/data/FirebaseEmulator.kt
- app/src/androidTest/java/com/comp90018/deadline/data/FirestoreTransferEmulatorTest.kt

## Fresh final verification results

All results in this section were executed again after the deterministic expiry fix,
using Java 17 on Windows and emulator-5554 / API 35:

| Check | Result |
| --- | --- |
| Original timing probe, before fix | 1 executed, 1 failed at initial valid-code read |
| Fixed expiry test, separate fresh instrumentation runs | 3/3 passed |
| Complete Firebase instrumentation selection | 50/50 passed |
| Account UI instrumentation selection | 7/7 passed |
| Combined final Android run | 57 passed; 0 failures/errors/skips |
| Debug JVM | 627 passed; 0 failures/errors/skips |
| Release JVM | 627 passed; 0 failures/errors/skips |
| Host Firebase checks | 125 passed; 0 failures/execution errors |
| Kotlin style | Passed, including the actual user checkout |
| Fresh Debug build and Android lint | Passed; 44 tasks executed with --rerun-tasks |
| Lint findings | 38 warnings, 3 informational; no errors |
| Unstaged and staged whitespace checks | Passed |

The prior 70 restart repetitions are historical results, not claimed as fresh in
this pass. The full JVM suites above execute the restart regressions again.
Both JVM tasks used --rerun to force execution, and the final build/lint invocation
used --rerun-tasks to avoid relying on cached results. Production code and rules were
not changed by the transfer test correction.

Exact final Gradle commands (run from the isolated verification checkout):

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat :app:testDebugUnitTest --rerun :app:testReleaseUnitTest --rerun :app:ktlintCheck :app:assembleDebug :app:lintDebug --continue --console=plain
.\gradlew.bat :app:assembleDebug :app:lintDebug --rerun-tasks --continue --console=plain

# Repeated three times, each with a fresh instrumentation execution:
.\gradlew.bat :app:connectedEmulatorAndroidTest --rerun '-Pandroid.testInstrumentationRunnerArguments.class=com.comp90018.deadline.data.FirestoreTransferEmulatorTest#anExpiredCodeIsNotReadFromTheOfflineCache' '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' --console=plain

# Final combined run, after all host checks finished:
.\gradlew.bat :app:connectedEmulatorAndroidTest --rerun '-Pandroid.testInstrumentationRunnerArguments.class=com.comp90018.deadline.data.FirebaseIsolationTest,com.comp90018.deadline.data.FirestoreProfileEmulatorTest,com.comp90018.deadline.data.FirestoreRulesTest,com.comp90018.deadline.data.FirestoreLeaderboardEmulatorTest,com.comp90018.deadline.data.FirestoreProgressSyncEmulatorTest,com.comp90018.deadline.data.FirestoreTransferEmulatorTest,com.comp90018.deadline.feature.account.AccountScreenTest' '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' --console=plain
```

Each instrumentation invocation selected ANDROID_SERIAL=emulator-5554 and used the
same temporary demo-package network exemption / finally cleanup shown above. The
already verified EmulatorApplication and demo-only generated configuration remained
unchanged. Host checks ran separately, from the user checkout:

```powershell
python scripts/test_firebase_emulator_rules.py
.\gradlew.bat :app:ktlintCheck --console=plain
git diff --check
git diff --cached --check
```

Final evidence: build/pr126-final-transfer-after-{1,2,3}.xml,
build/pr126-final-android-all.xml, build/pr126-final-quality.log,
build/pr126-final-host.log, and build/pr126-final-rebuild-lint.log.
No verification failure remains for the isolated Issue 65 changes. The user's
working checkout is still blocked by the preserved unrelated launcher typo; exclude
that edit from Issue 65. GitHub CI itself was not rerun remotely.

User branch remains feature/issue-65-user-auth at 87d0f54. Nothing is staged.
The intended Issue 65 scope is ten modified tracked files plus three new files
(journal, restart tests, this report), listed above. Unrelated state remains:
modified .idea/gradle.xml and launcher XML; untracked .idea/other.xml and the five
issue-65 review patch/ZIP artifacts. No commit, push, merge, rules deployment, or
production Firebase data access was performed.
