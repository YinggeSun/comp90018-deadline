# Level generation and solver QA (#42)

Use Java 17 and a configured Android SDK, then run:

```bash
bash ./gradlew :app:testDebugUnitTest
```

On Windows use `gradlew.bat`. These are deterministic JVM tests with no emulator
or external services. A seed/board failure can be reproduced from the assertion
message; there are no machine-speed assertions.

## Coverage

Existing suites cover all semester difficulty configurations, generation geometry,
ID/position uniqueness, triple-compatible types, bounded search, invalid boards,
forced unsolvable stacks, retry budgets and each rejected solver result, and verified
fallback identity/configuration. Existing solver tests replay solutions in the engine.

`SemesterGenerationRegressionTest` adds 100 signed seeds for each of the 12 weeks
(1,200 cases), checking candidate invariants and reproducibility and replaying every
accepted pipeline solution through `DefaultGameEngine`. Each move must be available;
each ID is used exactly once; the final board and tray must both be empty and WON.
JUnit reports each week separately. A pipeline fallback is permitted and is also
replayed; these tests do not claim every raw candidate is solvable.

`GenerationBoundaryRegressionTest` covers Long.MIN/MAX seeds, maximum safe sparse
coordinates, unsafe dimensions, an inclusive Int.MAX_VALUE layer bound, and retry
seed wraparound from Long.MAX_VALUE to Long.MIN_VALUE. Rejected dimensions must
throw in the raw generator and return the verified fixed fallback in the pipeline.

`SolverReferenceRegressionTest` checks 200 shuffled four-type boards with two
staggered six-layer stacks. Its small exhaustive oracle uses direct coordinate
arithmetic and independent tray counts: it does not use the production solver,
overlap graph, or engine for deciding solvability. The sample must include both
solvable and unsolvable boards. Every solver-reported solution is additionally
replayed through the real engine. This complements the existing three-stack sample.
The reference is intentionally bounded to 12 tiles (4,096 possible remaining masks),
so it is suitable for CI rather than an unbounded fuzz search.

## Scope

These tests validate domain generation/solving and fallback behavior. They do not
claim generated levels are wired into the UI, difficulty is balanced for humans,
or a late-week fallback is as hard as the requested level. UI integration and
playtesting are separate acceptance checks.
