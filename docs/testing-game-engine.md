# Game engine QA (#41)

Use Java 17 and a configured Android SDK. Run the full JVM suite with:

```bash
bash ./gradlew :app:testDebugUnitTest
```

On Windows use `gradlew.bat`. These tests need no emulator, Firebase, or sensors.

## Coverage

Existing suites cover tile overlap/availability, exact tile transfer, three-tile
matching, tray capacity, win/loss precedence, terminal-state freezing, undo depth
and match boundaries, shuffle invariants, restart, and stress/Coffee rules.

`GameEngineSequenceTest` adds 40 reproducible seeds, each with 200 mixed selections,
undo, shuffle, and restart operations. An independent reference computes coverage
from tile coordinates and stress from explicit tuned values without calling the
production graph/stress helpers. After every operation it checks the complete
state, public `canUndo`, availability for all original IDs, tile uniqueness, and
stability of every previously exposed `engine.state` snapshot. Before each
operation the test retains the actual engine state and an independent copy of
its board/tray lists, then rechecks all retained snapshots after every subsequent
operation. The reference state is used only as the operation oracle, not as a
substitute for a previously exposed engine snapshot. Seeds cover undo depths
0, 1, 3, and unlimited.
Failure messages contain seed, step, and tile ID for reproduction.

Shuffle is checked as a contract: IDs/positions and remaining type counts must
stay fixed; tray/stress/status remain unchanged. The reference accepts the actual
permutation, then independently checks subsequent availability and undo behavior.
It does not assume a particular random permutation.

`GameEngineCapacityRegressionTest` reaches the real seven-slot boundary through
the public API with four tile types, without reflection or a fake tray capacity.
It checks loss, frozen terminal state, restart, and a Coffee triple completing in
the seventh slot (matching and recovery before loss; committed undo history).
The fixture supplies explicit week/stress parameters, so changing provisional
production tuning defaults cannot silently change the capacity regression.
Hand-built boundary boards exercise the engine contract, which currently treats
an empty board as WON even if a malformed level leaves residual tray tiles.
The solvability validator separately requires clearing both board and tray.

## Scope

This suite covers the core engine, not UI rendering, sensor hardware, persistence,
or Firebase. Compose/manual/physical-device QA remains in issues #44/#45/#46.
Run build and Android lint with the suite when reviewing a production change.
