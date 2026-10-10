# Firestore security and validation

The online leaderboard (#38) is the only data the app stores in Cloud Firestore.
`firestore.rules` protects it (#51). The rules run on Google's servers in front of the
database, so they hold even against a modified app or a client that skips the app
entirely: anything they do not allow is refused.

## Data model

One document per player per level holds that player's best time:

```text
leaderboard/{levelId}/entries/{userId}
  uid          string     the player's anonymous Firebase Auth ID (same as {userId})
  nickname     string     1–20 characters, from Settings or "Anonymous"
  timeMillis   integer    completion time in milliseconds
  week         integer    the level's last semester week (2, 4, …, 12)
  submittedAt  timestamp  set by the server
```

Players are identified by Firebase Anonymous Authentication (#37), so no personal data is
collected. The app reads a level's ranking with a live query ordered by `timeMillis`.

## What the rules allow

Everything not listed here is denied, including every path outside `leaderboard/`.

| Operation | Allowed when |
|---|---|
| Read a ranking | the caller is signed in and the level is `level_1` … `level_6` |
| Create an entry | the caller owns it (`{userId}` is their ID) and the document is valid |
| Update an entry | as for create, and the new `timeMillis` is strictly lower than the stored one |
| Delete an entry | never |

## What makes a document valid

- **Shape:** exactly the five fields above, no more and no fewer.
- **Ownership:** `uid` equals `{userId}`, which equals the caller's ID, so nobody can write or
  overwrite another player's entry.
- **Types:** `nickname` is a string, `timeMillis` and `week` are integers.
- **Nickname:** 1–20 characters and not only spaces (the Settings screen's limit).
- **Level and week:** the level is one of the six semester levels, and `week` is that level's
  last week, which is what the app records for a win.
- **Plausible time:** at least **250 ms per tile on the board**, a sustained four taps a second
  on every tile, and at most 24 hours. The floors are:

  | Level | Weeks | Tiles | Minimum time |
  |---|---|---|---|
  | `level_1` | 1–2 | 18 | 4.5 s |
  | `level_2` | 3–4 | 27 | 6.75 s |
  | `level_3` | 5–6 | 36 | 9 s |
  | `level_4` | 7–8 | 45 | 11.25 s |
  | `level_5` | 9–10 | 54 | 13.5 s |
  | `level_6` | 11–12 | 63 | 15.75 s |

  Real play is far slower, so the floors only reject forged times (for example `1` ms). If the
  tile counts in `SemesterDifficulty` change, update the floors and the week map in the rules.
- **Timestamp:** `submittedAt` must be the server's time (`request.time`), so entries cannot be
  backdated.

Several checks overlap on purpose. For example, a document with a missing field also fails the
type checks, and a level outside the six has no expected week. Each requirement is still
stated explicitly so the rules read as a specification.

## What the rules cannot prevent

- **Plausible fake times.** A modified client can submit any time above the floor. Stopping
  that would need server-side replay of the game, which is out of scope.
- **Many anonymous accounts.** Anyone can create new anonymous identities, so one person could
  post several entries under different nicknames. Each identity can still only hold one entry
  per level.

The app itself never writes an invalid document: it submits only wins, only when faster than
the stored entry, with the Settings nickname (or "Anonymous"). A refused write never affects
gameplay; it only costs the player a leaderboard placing.

## Testing

The rules are tested against the Firebase Emulator Suite, which runs Auth and Firestore
locally on the `demo-deadline` project, never the real one.

- `FirestoreRulesTest` writes raw documents (not through the app) and checks every allowed
  case and every refusal above, each changing one field from a valid entry.
- `FirestoreLeaderboardEmulatorTest` runs the app's leaderboard code against the same rules:
  ranking order, faster-only updates, live updates, and refusals.

Both test classes clear the emulator database before each test, and skip themselves when the
emulators are not running, so normal instrumented test runs are unaffected.

To run them:

1. Install the [Firebase CLI](https://firebase.google.com/docs/cli). Current versions need
   Node.js 20 or newer and Java 21 or newer.
2. From the repository root, start the emulators:

   ```bash
   firebase emulators:start --only auth,firestore --project demo-deadline
   ```

   The emulator reloads `firestore.rules` whenever the file changes.
3. With an Android emulator running, run the tests:

   ```bash
   bash ./gradlew :app:connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.package=com.comp90018.deadline.data
   ```

Debug builds allow plain HTTP to `10.0.2.2` (the host as seen from the Android emulator),
which the Auth emulator needs; release builds stay HTTPS-only.

## Deploying

The rules in the repository are not live until deployed. With the Firebase CLI logged in to
an account that can manage the project:

```bash
firebase deploy --only firestore:rules --project comp90018-deadline
```

This changes only the rules, not the data. Firebase keeps earlier versions, which can be
restored from the console (Firestore Database → Rules).
