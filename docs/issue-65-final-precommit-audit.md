# Issue #65 — Final pre-commit audit

Audited 2026-10-12 on `feature/issue-65-user-auth`. Ready for commit with the documented limitations below. No commit, push, PR or Firebase deployment was performed by this audit.

## Scope and staging

All Issue #65 implementation, regression tests, emulator isolation, scripts and documentation listed below are staged in their final working-tree versions. Previously unstaged changes included the dedicated build type, application/container test seams, fail-closed test helper updates, the two isolation tests, the test runner and emulator source set. These are now included. The only audit additions are script usage documentation and this audit record; no application-code correction was required.

The list below is the complete recommended commit inventory (50 files, paths relative to the repository root):

```text
README.md
app/build.gradle.kts
app/src/androidTest/java/com/comp90018/deadline/ExampleInstrumentedTest.kt
app/src/androidTest/java/com/comp90018/deadline/data/FirebaseEmulator.kt
app/src/androidTest/java/com/comp90018/deadline/data/FirebaseIsolationTest.kt
app/src/androidTest/java/com/comp90018/deadline/data/FirestoreLeaderboardEmulatorTest.kt
app/src/androidTest/java/com/comp90018/deadline/data/FirestoreProfileEmulatorTest.kt
app/src/androidTest/java/com/comp90018/deadline/data/FirestoreProgressSyncEmulatorTest.kt
app/src/androidTest/java/com/comp90018/deadline/data/FirestoreRulesTest.kt
app/src/androidTest/java/com/comp90018/deadline/data/FirestoreTransferEmulatorTest.kt
app/src/androidTest/java/com/comp90018/deadline/feature/account/AccountScreenTest.kt
app/src/androidTest/java/com/comp90018/deadline/testing/EmulatorTestRunner.kt
app/src/emulator/AndroidManifest.xml
app/src/emulator/google-services.json
app/src/emulator/java/com/comp90018/deadline/testing/EmulatorApplication.kt
app/src/emulator/res/xml/emulator_network_security.xml
app/src/main/java/com/comp90018/deadline/app/AppContainer.kt
app/src/main/java/com/comp90018/deadline/app/DeadlineApp.kt
app/src/main/java/com/comp90018/deadline/data/local/datastore/AccountDataStore.kt
app/src/main/java/com/comp90018/deadline/data/local/datastore/ProgressDataSource.kt
app/src/main/java/com/comp90018/deadline/data/remote/firebase/FirebaseAuthDataSource.kt
app/src/main/java/com/comp90018/deadline/data/remote/firebase/FirestoreProfileDataSource.kt
app/src/main/java/com/comp90018/deadline/data/remote/firebase/ProgressFields.kt
app/src/main/java/com/comp90018/deadline/data/repository/AuthRepositoryImpl.kt
app/src/main/java/com/comp90018/deadline/data/repository/SessionGate.kt
app/src/main/java/com/comp90018/deadline/data/sync/ConflictResolver.kt
app/src/main/java/com/comp90018/deadline/domain/repository/AccountRepository.kt
app/src/main/java/com/comp90018/deadline/feature/account/AccountScreen.kt
app/src/main/java/com/comp90018/deadline/feature/account/AccountViewModel.kt
app/src/main/java/com/comp90018/deadline/feature/settings/SettingsScreen.kt
app/src/main/java/com/comp90018/deadline/navigation/AppNavHost.kt
app/src/main/java/com/comp90018/deadline/navigation/Routes.kt
app/src/test/java/com/comp90018/deadline/data/local/AccountDataStoreTest.kt
app/src/test/java/com/comp90018/deadline/data/repository/AccountRepositoryTest.kt
app/src/test/java/com/comp90018/deadline/data/repository/SessionGateTest.kt
app/src/test/java/com/comp90018/deadline/data/sync/ConflictResolverTest.kt
app/src/test/java/com/comp90018/deadline/feature/account/AccountViewModelTest.kt
docs/firestore-security.md
docs/issue-65-android-isolation.md
docs/issue-65-emulator-execution.md
docs/issue-65-final-precommit-audit.md
docs/issue-65-hardening-report.md
docs/issue-65-report.md
docs/issue-65-startup-timeout-diagnosis.md
docs/user-accounts.md
firestore.rules
scripts/README.md
scripts/read_debug_firebase_identity.py
scripts/run_firebase_android_tests.ps1
scripts/test_firebase_emulator_rules.py
```

## Excluded and preserved

- Personal IDE settings: `.idea/gradle.xml` (unstaged modification) and `.idea/other.xml` (untracked).
- Temporary review archives: `issue-65-final-review.patch`, `issue-65-final-review.zip`, `issue-65-full-review.patch`, `issue-65-review.patch`, `issue-65-review.zip` (untracked).
- Generated output, test reports, diagnostics and comparison scripts under ignored `build/` and `app/build/`; ignored Firebase logs and `local.properties`.
- No changes to `app/google-services.json` are staged. Its existing production client configuration is baseline repository content, outside this commit. It was not copied into the emulator fixture or new files.

Nothing above was deleted, reset or discarded. Avoid `git add .` or `git add -A`: the intentionally excluded IDE files and archives remain present.

## Credential and script audit

The staged `app/src/emulator/google-services.json` contains only project `demo-deadline`, project number `1`, dummy app ID `1:1:android:1`, emulator package name and key `emulator-only`. It contains no production keys or OAuth clients.

All staged changed-file contents were scanned for private keys, Google API keys, JWTs, OAuth refresh/access tokens, GitHub tokens and AWS access keys, with zero matches. The existing production configuration's API-key values were also compared directly against every staged changed file: zero matches. Credential-related code and fixtures were reviewed; password literals in tests are synthetic test inputs (including `emulator-password`), not production account passwords. Runtime ID tokens exist only as SDK/emulator test values, never hardcoded token strings. No real smoke-test UID, password, account email or token was added to the commit.

Script review:

- `test_firebase_emulator_rules.py`: standard-library Python; fixed loopback endpoints, no proxies or redirects, explicit demo project/token checks, no production fallback, no rule deployment. Writes test data only to disposable local emulators; artifacts stay under ignored `build/`. Run without Python `-O` so assertions remain enabled.
- `run_firebase_android_tests.ps1`: intentionally Windows-specific; uses local SDK/JDK settings and a validated Android Emulator serial. Verifies demo resources/manifests before installation. The scoped Android test-package network exemption is removed in `finally` and expires after ten minutes if interrupted. No global firewall changes. Existing suites clear demo Firestore data; do not share the emulator with concurrent tests.
- `read_debug_firebase_identity.py`: Python helper with an overridable ADB path; refuses non-debuggable/release apps, reads only local persisted auth state, prints only UID/anonymous status, suppresses credential-bearing output, and performs no app launch or auth mutation. Firebase's internal storage format can change; unknown formats fail closed.

`scripts/README.md` documents prerequisites, portability boundaries, side effects and exact commands. Both Python files passed syntax parsing; the PowerShell file passed its AST syntax check. These scripts introduced no dependency downloads or new runtime packages.

## Deployed rules comparison

Used the existing authenticated Firebase CLI's read-only Rules API functions to fetch the exact `cloud.firestore` release and its referenced ruleset. No Firestore documents were read and no rules/configuration were written.

- Release: `projects/comp90018-deadline/releases/cloud.firestore`
- Ruleset: `projects/comp90018-deadline/rulesets/3a0b7364-a101-4430-bc58-dd4f6faf7cd1`
- Published update time: `2026-10-12T02:48:15.951219Z`
- SHA256 after CRLF-to-LF normalization: `0bfa3d0bb895bf0e730e3759fa2e32234c61c1967b61307297d0ff4144e378c1`

The deployed source, working-tree `firestore.rules` and staged `:firestore.rules` are exactly equal after line-ending normalization. No whitespace, comments or logic beyond line endings were ignored. This is a point-in-time check; another Console publication after this audit would require a new comparison. Evidence is in ignored `build/issue65-published-rules-comparison.json`.

## Final quality checks

Executed from the repository root with JDK 17:

```powershell
.\gradlew.bat ktlintCheck :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleEmulatorAndroidTest --console=plain
git diff --check
git diff --cached --check
```

- JVM: **603 tests, 70 suites, 0 failures/errors/skips**; `testDebugUnitTest` executed during this audit.
- Kotlin lint, debug build, Android lint and emulator instrumentation APK build: passed. Android lint reports **0 errors, 38 warnings and 3 informational findings**; this audit did not alter baselines or suppress findings.
- Both Git whitespace checks: passed.
- Previous host-local rules execution: **125 passed**, zero failures/errors, not rerun during this staging audit.
- Previous Android Firebase execution: **50 passed**, zero failures/errors/skips; existing result XML inspected, not rerun during this staging audit.
- Production smoke verification is user-reported: guest registration retained UID, profile creation succeeded, logout/guest/login restored identity and progress. This audit did not repeat production authentication or database operations.

Final build log: ignored `build/issue65-final-audit-checks.log`. Earlier reports describe historical verification stages, including then-unavailable infrastructure; the startup diagnosis and this audit record contain the final results.

## Remaining limitations and proposed commit

No blocking finding remains. Pattern scans cannot mathematically prove absence of every possible secret, but targeted staged-content inspection and comparison against the known production client keys found none. Android lint's existing warnings/information remain. Debug UID inspection depends on Firebase's private persisted format. The Windows instrumentation wrapper requires a disposable local emulator and its ten-minute network exemption bounds run length. Other UI instrumentation suites were not rerun in this audit.

Proposed commit message:

```text
feat(auth): add optional user accounts and isolated Firebase tests (#65)
```
