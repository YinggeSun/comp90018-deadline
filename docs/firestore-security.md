# Firestore security and validation

The app stores three things in Cloud Firestore: the online leaderboard (#38), each player's
cloud copy of their progress (#39), and short-lived transfer codes for moving progress to
another phone (#39). `firestore.rules` protects both (#51). The rules run on Google's servers in front of the
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

Each player also has one progress document, the cloud copy of what is stored on the device:

```text
progress/{userId}
  completedLevelIds   list of strings      levels cleared, from level_1 … level_6
  highestUnlockedWeek integer              1–12
  personalBests       map                  levelId → { timeMillis, achievedAtMillis }
  lastModifiedMillis  integer              latest local change, for reference
  updatedAt           timestamp            set by the server
```

### How progress sync works

A sync runs at startup (once the device is online), after every win, and whenever the device
reconnects. It reads the cloud copy, merges it with local progress, and writes the result back
in one Firestore transaction, then merges that result into local progress. The merge never
makes anything worse: completed levels are combined, the furthest unlocked week wins, and each
level keeps its faster Personal Best. Because the write is a transaction, a stale copy cannot
overwrite progress saved from another device in the meantime. Offline, a sync simply fails and
the next one, after reconnecting, uploads what was played.

### Transfer codes

Anonymous identities exist only on the device, so a new phone or a reinstall starts with a new
one. To move progress, Settings → *Move to another phone* creates a code on the old phone (it
syncs first), and entering that code on the new phone merges the code's progress in and syncs it
to the new identity's cloud copy. A code is a snapshot:

```text
transfers/{code}       code: 8 characters from 23456789ABCDEFGHJKLMNPQRSTUVWXYZ
  ownerId            string     the creator's ID
  completedLevelIds, highestUnlockedWeek, personalBests, lastModifiedMillis
                                 as in progress/{userId}
  createdAt          timestamp  set by the server
  expiresAt          timestamp  at most 24 hours after creation
```

The alphabet leaves out look-alikes (0, O, 1, I), giving 32^8, about a trillion, possible codes,
generated with `SecureRandom`. A missing code and an expired code both read as refused, so
nobody can tell which codes exist.

## What the rules allow

Everything not listed here is denied, including every path outside `leaderboard/`.

| Operation | Allowed when |
|---|---|
| Read a ranking | the caller is signed in and the level is `level_1` … `level_6` |
| Create an entry | the caller owns it (`{userId}` is their ID) and the document is valid |
| Update an entry | as for create, and the new `timeMillis` is strictly lower than the stored one |
| Delete an entry | never |
| Read own progress | the caller is the owner (`{userId}` is their ID) |
| Create own progress | the caller is the owner and the document is valid (below) |
| Update own progress | as for create, and nothing gets worse: no completed level is dropped, the unlocked week does not go down, and no Personal Best gets slower or disappears |
| Delete progress | never |
| Read a transfer code | the caller is signed in, asks for that exact code, and it has not expired |
| List transfer codes | never |
| Create a transfer code | the caller is signed in, the code has the right format, `ownerId` is the caller, the progress fields are valid, `createdAt` is the server time, and `expiresAt` is in the future and at most 24 hours away |
| Change or delete a transfer code | never |

## What makes a leaderboard entry valid

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

## What makes a progress document valid

- **Shape:** exactly the five fields above, no more and no fewer.
- **Values:** `completedLevelIds` only lists the six levels; `highestUnlockedWeek` is 1–12;
  `personalBests` only has keys for the six levels, each holding an integer `timeMillis` at
  or above that level's minimum time (the same floors as the leaderboard) and an integer
  `achievedAtMillis`.
- **Timestamp:** `updatedAt` must be the server's time.

Several checks overlap on purpose. For example, a document with a missing field also fails the
type checks, and a level outside the six has no expected week. Each requirement is still
stated explicitly so the rules read as a specification.

## What the rules cannot prevent

- **Guessing codes.** With about a trillion codes and a 24-hour lifetime, guessing a live code is
  impractical, but the rules cannot rate-limit attempts. A guessed code only reveals a snapshot
  of someone's levels and times, and redeeming it can only add progress to the guesser's own
  device.

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
- `FirestoreProgressSyncEmulatorTest` runs progress sync against the same rules, including two
  devices on one account ending up with everything.
- `FirestoreTransferEmulatorTest` moves progress between two separate accounts with a transfer
  code, including the new phone's cloud copy. The rules tests also create a code that expires
  after three seconds and check it is refused afterwards.

Tests that forge a timestamp use one an hour in the past. A client timestamp of "now" can equal
the emulator's server time to the millisecond, because the Android emulator shares the host's
clock, which would make those tests flaky.

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
