# Issue #48: performance and stability

Review date: 2026-10-14 (Australia/Sydney). Base: main `037439525ad0225ec3c81299dfca0ec6d4d0a566`. This work is independent of the unmerged #46 / PR #134. **Refs #48; keep the issue open for physical-device acceptance.**

## Confirmed problem and fix

On the real generated 63-tile Level 6 board, 100 stress updates caused 6,300 additional `isTileSelectable` queries, sorting and tile-model mapping despite unchanged board topology. The regression test failed against the original implementation (`expected 63, actual 6363`) and passes after caching immutable engine board/tray projections. Game status invalidates the availability cache; stress, music slowdown, Undo availability and tray capacity still update on every publication. Action/terminal-state tests verify selection, Undo, shuffle, Restart and disabling tiles on loss.

The board's Compose content is remembered using its tiles, dimensions and peek amount. HUD updates therefore preserve unchanged board content. `rememberUpdatedState` forwards input to the latest event handler; an Android regression test invokes a previously captured tile callback after replacing the handler and changing HUD state. Existing layout, interaction and peek tests remain part of the full regression suite.

## Coverage and validation

| Area | Evidence |
| --- | --- |
| JVM game/domain/repository tests | Debug **644/644**, Release **644/644**, zero failures/errors |
| New Android tests | **7/7 passed**: 63-tile rotation/recreation, 100 duplicate dispatches, 20 select/Undo/Restart cycles and background return, background stress-clock suspension, current callback forwarding, generated-board timings, sensor delivery/unregister |
| Full Android regression | **160/160 passed**, zero failures/errors/skips, including existing UI/navigation, sensor, persistence and isolated Firebase suites |
| Builds | Debug, Release, isolated Emulator, EmulatorAndroidTest, non-debuggable Benchmark target and benchmark driver built successfully |
| Static checks | Ktlint passed; Debug and Release lint passed |
| Macrobenchmark | Four cases, five measured iterations each, before and after; cold startup runs without Compose SDK tracing, interaction/HUD cases run with full tracing |
| Physical low-end phone | Unavailable; 60 fps / 16.7 ms and visible feedback under 100 ms remain unverified |

The rapid-input test repeatedly dispatches the same captured semantic action; it proves stale-input safety, not physical touch delivery speed. The interaction benchmarks use real UiAutomator taps on production game composables.

The rotation test requests Activity orientation and verifies configuration/state retention across recreation. The tablet-based AVD letterboxes the requested landscape window inside its portrait display; that screenshot is not a native physical landscape-display capture.

## Local benchmark results

Both valid A/B sets use the same fresh Android 15/API 35 x86_64 AVD: 1080×1920, density 420, 2 vCPUs, configured RAM 2,048 MB, 8 GB userdata, host GPU (AMD Radeon RX 9070 XT). Target: non-debuggable, shell-profileable benchmark APK, `CompilationMode.Full`, generated boards seeded with 48. This variant shares the isolated `.emulator` package and requires disposable local Firebase `demo-deadline`; production Firebase is not used.

Counts below are per-iteration medians from actual Compose trace sections. CPU times are Macrobenchmark's pooled frame-duration percentiles, not end-to-end frame presentation times or a certified FPS result.

| Scenario | GameBoard composition count before → after | TileView composition count before → after | Frame CPU P95 before → after |
| --- | ---: | ---: | ---: |
| Level 6, five select/Undo pairs and Restart | 31 → 11 | 1,847 → 633 | 113.50 → 38.50 ms |
| Level 1, same actions | 22 → 11 | 362 → 186 | 49.11 → 38.76 ms |
| Level 6, 2 seconds of HUD ticks only | 11 → 0 | 693 → 0 | 94.70 → 34.22 ms |

The HUD case directly verifies elimination of unnecessary board work. Frame CPU P95 still exceeds 16.7 ms on this emulator; Level 6 interaction P99 remains 117.85 ms after the change. These results do not establish the issue's 60 fps acceptance criterion. Emulator results and a single A/B run cannot predict low-end physical-device performance.

Cold time to initial display: **2,148.58 → 2,180.08 ms** median. No startup improvement is claimed. This measures the isolated MainActivity path and includes emulator-only fail-closed Firebase reachability checks.

The benchmark-only activity records ACTION_UP → next window `onDraw` as a feedback **proxy**. After-change samples, including warm-up: n=132, P50=6.99 ms, P95=16.25 ms, maximum=113.10 ms. This does not measure whether the expected changed pixels were presented, and an observed sample exceeds 100 ms; the visible-feedback target remains open. Its global Recomposer change count is diagnostic only; per-function composition counts above come from Perfetto, not that global counter.

Generated-board diagnostics: all six configured sizes (18/27/36/45/54/63), three seeds each, correct tile counts, load times 1.43–31.45 ms on this emulator. Gesture sensor delivery observed about 50 Hz for accelerometer, gyroscope and rotation vector; unregister stopped further dispatch. This verifies the configured `SENSOR_DELAY_GAME` path in an emulator. It does not certify hardware jitter, power use, accuracy or AutoLight sensor rate; ambient-light behavior remains covered by existing lifecycle/controller tests.

## Reproduction

Use JDK 17 and the repository's Gradle wrapper. Start disposable Firebase Auth/Firestore emulators on localhost 9099/8080 for `demo-deadline`, and an Android API 30+ emulator. Configure `local.properties` with `sdk.dir`. Keep the host otherwise idle during performance collection.

```powershell
.\gradlew.bat ktlintCheck :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease
.\scripts\run_game_macrobenchmarks.ps1 -Serial emulator-5554 -OutputDirectory C:\QA48 -RunLabel review
```

The wrapper builds/validates the non-debuggable isolated target, installs only QA packages, runs cold and full-traced frame phases separately, rejects JUnit failures even when ADB returns exit code zero, and copies each phase's JSON/Perfetto traces before the next instrumentation run replaces device output. It temporarily exempts only the QA package from background-network restrictions; the exemption is removed in `finally` and expires after 30 minutes. The wrapper requires an emulator because the isolated Application uses `10.0.2.2`; **do not run it against a physical phone**. Use a short Windows output path to avoid long-path artifact problems.

For full Android tests, install `app-emulator.apk` first, grant the same scoped QA networking exemption, then run `:app:connectedEmulatorAndroidTest` with `-Pandroid.testInstrumentationRunnerArguments.qaScreenshotPrefix=issue48` and `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`. Remove the exemption afterwards. The suite clears disposable Firebase test data; do not share the emulator with concurrent tests.

## Android Studio / manual review

1. Open this checkout in Android Studio with Gradle JDK 17. Select the **emulator** build variant, start the disposable Firebase services and run on the Android emulator. The separate `.emulator` package is test-only. Tests seed QA progress to unlock Level 6; do not apply that fixture to a real user profile.
2. Open Level 6: tap an uncovered tile, confirm exactly one tile moves to the tray, Undo restores it, and Restart clears the tray/resets time and stress. Rapidly tap the same location and repeat these actions. The app must not duplicate a consumed tile or crash.
3. Select one tile, rotate portrait → landscape → portrait and recreate the activity. The selected tile/tray, remaining board and Undo availability must persist. Scroll short/wide layouts to reach the board and controls.
4. Background the app for several seconds, then return. Board/tray state must persist; background time must not be added to stress. Verify input, audio and enabled sensor gestures resume. Repeat with Shake/Tilt/AutoLight toggled on/off.
5. For individual Compose recomposition inspection, use the debuggable **emulator** variant and Android Studio Layout Inspector's recomposition counts. Reset counts after initial composition, leave the board idle while the HUD ticks, then tap/Undo, shuffle and tilt. Unchanged idle tiles should not recompose due solely to HUD updates; affected tiles must still update during interactions.
6. For CPU/system traces, open the saved `.perfetto-trace` in Android Studio Profiler. Inspect GameBoard/TileView slices, frame deadlines and slow frames. Do not compare the earlier software-renderer capture to this host-renderer A/B set.
7. Before closing #48, repeat profiling and sustained gameplay on a low-end Android phone using a build/backend configured for disposable test data. Record hardware/OS/refresh rate, thermal conditions, duration and representative seeds. Assess frame presentation deadlines and actual visible input response, including tail latency, rotation, background return and sensor power use. This physical-device step has not been performed.

## Evidence and limits

Committed evidence: [benchmark summary](issue48/benchmark-summary.json), [generation timings](issue48/generation.csv), [sensor sampling](issue48/sensors.csv), [after-change feedback proxy/global composition samples](issue48/runtime-after.csv).

Raw local evidence is retained outside Git under `C:\Users\admin\Desktop\90018\qa-tools`: `48before` (HUD/cold), `issue48-results/host-before-interactions` (interaction baseline), `48after` (optimized traces/metrics/runtime CSV), `issue48-results/focused` (focused Android XML/diagnostics), and `issue48-results/full` (160-test XML and screenshots). Earlier failed captures are also retained: full-traced cold startup broadcast timing, a launch-completion capture failure, disk exhaustion on the old software-renderer AVD, and transient ADB artifact-copy disconnects. None is counted as a successful benchmark. Cold collection is separated from SDK tracing; the final wrapper retries artifact copies without rerunning measurements.

References: [Macrobenchmark overview](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview), [Compose tracing](https://developer.android.com/develop/ui/compose/tooling/tracing), [Benchmark releases](https://developer.android.com/jetpack/androidx/releases/benchmark). These explain the non-debuggable/profileable target and tracing setup. Emulator measurements are local diagnostics, not physical-device acceptance.

## QA comment draft

Confirmed and fixed unnecessary board work on stress/HUD updates. Regression coverage includes generated 63-tile boards, duplicate input, rotation/recreation, Undo/Restart, background clock handling and sensor unregister; local A/B tracing reduces idle GameBoard/TileView compositions from 11/693 to 0/0. Merge the verified optimization once CI is green, but keep #48 open: low-end-device 60 fps and actual visible feedback under 100 ms have not been validated.
