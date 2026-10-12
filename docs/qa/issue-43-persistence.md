# Issue #43: repository and persistence acceptance

Validated on 2026-10-11 against main `cc292c92173f3329772ab61fac0329d0125134d3`, with the eight repository tests added by this change. No production code changes were required.

## Coverage and results

| Acceptance item | Coverage | Result |
| --- | --- | --- |
| DataStore | Real temporary Preferences file using `DeadlineDataStore.create`; closing its coroutine scope and reopening the same file; shared settings/progress keys; corrupt protobuf and wrong-type recovery | Pass |
| Personal Best | First/faster/equal/slower completions; previous-best outcome; concurrent results for the same level; separator-containing IDs; restart persistence | Pass |
| Progress | Completed-level union, week unlocking and final-week cap; modification timestamp never regresses; simultaneous completions, remote merges and settings writes | Pass |
| Default values | Explicit fresh repository defaults; absent/wrong-type keys; corrupt shared file resets both repositories and accepts subsequent writes | Pass |
| Merge logic | Fastest result per level, earlier achievement on equal times, highest unlocked week/latest modification, union of completed IDs, commutativity/associativity/idempotence, repeat merge and restart persistence | Pass |

The six directly relevant JVM classes contain **42 tests**: `ProgressDataSourceTest` (8), `SettingsDataSourceTest` (5), `WrongTypeRecoveryTest` (4), `PersistenceRepositoryTest` (8), `PlayerProgressTest` (9), and `ConflictResolverTest` (8).

`PersistenceRepositoryTest` uses production repository implementations rather than fake repositories. Its concurrent scenario writes 20 local completions, 20 remote progress merges and 20 settings toggles, then reopens the file and checks every stored record, settings and progress metadata. Another test races 20 times for the same level and checks that the fastest survives.

## Regression validation

- Debug JVM suite: **576 passed**, zero failures/errors/skips.
- Release JVM suite: **576 passed**, zero failures/errors/skips.
- Android API 35 emulator: **2 passed**, zero failures/errors/skips, selecting `ProgressFlowTest` and `ResultProgressFlowTest`. These exercise the real app container/DataStore, winning a level, unlocks and the best-time display after replay.
- `assembleDebug`, `assembleRelease`, `ktlintCheck`, `lintDebug`, `lintRelease`: passed. Lint reports 48 Debug / 35 Release warnings and zero errors; these warnings already existed in the reviewed main tree.

## Reproduction

Use JDK 17 and the project's configured Android SDK. Run from the repository root:

```powershell
.\gradlew.bat testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease ktlintCheck lintDebug lintRelease --console=plain --max-workers=2
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.comp90018.deadline.integration.ProgressFlowTest,com.comp90018.deadline.integration.ResultProgressFlowTest' --console=plain --max-workers=2
```

JUnit XML is written under `app/build/test-results/`; device XML is under `app/build/outputs/androidTest-results/connected/`. Logs and a preserved XML snapshot for this run are retained in the local QA artifacts.

## Acceptance boundary

The five items explicitly listed in #43 are covered and pass. No blocking repository/persistence defect was found in this scope. Issue #43 can be closed when this test change is merged and CI passes.

Reopening a store simulates normal application restart by stopping its scope before constructing another store; it does not simulate power loss during an incomplete write. Corrupt-file recovery intentionally resets both settings and progress to defaults rather than recovering previous records. The two device tests are selected persistence flows, not a new run of the entire Android suite. Firebase offline/reconnect and server failure acceptance belongs to #46 and is not claimed here.
