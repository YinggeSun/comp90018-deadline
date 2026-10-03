# PR #62 integration with main

The launcher keeps main's AppNavHost. Person3TestScreen remains a developer demo;
it no longer replaces the application's home screen.

GameViewModel keeps main's level lookup, lifecycle ViewModel, StateFlow, stable
board dimensions and engine-derived selection rules. Sensor actions publish into
the same state flow. GameScreen binds sensors to its lifecycle and releases both
subscriptions when disposed, even if its lifecycle owner is still started.
Selection feedback and accepted shuffle feedback use the haptic manager.

Shuffle preserves tile IDs, positions, type counts, tray contents and overlap
relationships. A shuffle that changes the board clears earlier undo snapshots;
subsequent selections can still be undone under main's existing match policy.
No-op shuffles do not report success or trigger shuffle feedback. Restart restores
the original board and clears peek state. Non-finite tilt input cannot poison the
filter or enter UI transforms.

Validation: 165 JVM tests and 17 API 35 emulator instrumentation tests passed.
Debug APK builds; lint reports 0 errors and 39 warnings.

Manual follow-up: test shake cooldown, tilt readability, background/foreground
and vibration patterns on a physical phone. The current level-select menu exposes
only the single-type, single-layer sample level, so it cannot demonstrate a visible
shuffle or layered peek. Use a test-injected GameViewModel for LEVEL_3 to exercise
those behaviors; this integration does not expand the level-select menu.
