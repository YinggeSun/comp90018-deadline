# Remote Config: gameplay balance

Gameplay balance can be tuned through Firebase Remote Config without a new build (#40, #52).
The app works fully without it: every value has a default compiled into the app, and Remote
Config can only replace a default with a valid value.

## Parameters

Stress values apply to Stress and recovery; Shake and Tilt values tune the motion features.

| Key | Default | Valid range | What it does |
|---|---|---|---|
| `stress_maximum` | 100 | 10–1000 | Size of the Stress gauge. |
| `stress_high_threshold` | 75 | 1–1000, at most stress_maximum | Stress at which High Stress warnings start. |
| `stress_degradation_probability` | 0.2 | 0–1 | Chance a tap slips onto a neighbouring tile at Maximum Stress. |
| `stress_accumulation_percent_per_second` | 1.5 | 0–20 | Stress added per second of play, as a percentage of the gauge, in every week. |
| `coffee_recovery_base` | 20 | 0–1000 | Stress removed by a Coffee triple in Week 1. |
| `coffee_recovery_decline_per_week` | 1 | 0–100 | How much less a Coffee triple removes each later week. |
| `music_recovery` | 8 | 0–1000 | Stress removed by a Music triple. |
| `music_slowdown_multiplier` | 0.5 | 0–1 | Stress speed while the Music effect lasts (0.5 = half speed). |
| `music_slowdown_millis` | 8000 | 0–60000 | How long the Music slowdown lasts, in milliseconds. |
| `shake_threshold_g` | 1.7 | 0.5–5 | Linear acceleration (in g, gravity removed) a shake must reach. |
| `shake_required_hits` | 2 | 1–10 | Consecutive strong readings needed for a shake. |
| `shake_cooldown_millis` | 900 | 0–10000 | Minimum time between shuffles, in milliseconds. |
| `shake_smoothing` | 0.25 | 0.01–1 | Shake filter smoothing (1 = no smoothing). |
| `tilt_deadzone_degrees` | 8 | 0–45 | Tilt below this angle does nothing. |
| `tilt_full_peek_degrees` | 32 | 1–90, above the deadzone | Tilt angle for a full peek. |
| `tilt_smoothing` | 0.18 | 0.01–1 | Tilt filter smoothing (1 = no smoothing). |
| `tilt_release_hysteresis_degrees` | 2 | 0–45, at most the deadzone | How far below the deadzone the peek releases, to avoid flicker. |

`remoteconfig.template.json` lists the same parameters with their defaults and descriptions.

## How values reach a game

1. At startup, `ConfigRepository` first applies the values fetched in an earlier session
   (Remote Config keeps them on the device, so this works offline), then fetches new ones in
   the background with a 10-second timeout. New values are fetched at most once an hour.
2. Every value is parsed and range-checked by `RemoteBalance`. A missing or invalid value keeps
   its default; if the values of one group (Stress, Shake or Tilt) clash, for example a full-peek
   angle below the deadzone, that whole group keeps its defaults. Refused keys are logged.
3. Each game reads the balance once, when it starts: the Stress values go to the game engine and
   the Shake and Tilt values to the sensors. A change never alters a game in progress, and the
   game engine never sees Firebase.

## Changing a value

In the Firebase console, open **Run → Remote Config** (or search for Remote Config), add or edit
a parameter with one of the keys above, enter a number within its valid range, and **Publish
changes**. Devices pick the new value up on their next fetch (at most an hour, then the next
game started). To compare two parameter sets during playtesting, use Remote Config conditions,
for example a percentage of users.

## Week-based Stress

Stress now builds with time at one rate for every week (#58), so
`stress_accumulation_percent_per_second` applies to all weeks. The week-based part of the
balance is Coffee Recovery, which shrinks by `coffee_recovery_decline_per_week` each week.
