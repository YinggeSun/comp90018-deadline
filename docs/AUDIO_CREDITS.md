# Audio credits

Verified on 2026-10-10 against the four OpenGameArt source pages and their downloadable files. Before converting the click, all four supplied assets were byte-for-byte identical to the source downloads (SHA-256 below).

All four source pages list [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/). CC0 does not require attribution; creator/source credits are retained here voluntarily. The source pages list no additional attribution requirement. These records verify the listed source/license and asset identity, rather than providing a separate warranty of third-party rights.

| Resource | Original title | Creator / source-page author | Source | License | Attribution requirement |
| --- | --- | --- | --- | --- | --- |
| `bgm_gameplay` | Cozy Puzzle In-Game 1 | MintoDog | [Source page](https://opengameart.org/content/cozy-puzzle-in-game-1) | CC0 1.0 | None required; credit retained voluntarily |
| `sfx_tile_click` | Click | qubodup (page author; describes extraction from pdsounds.org) | [Source page](https://opengameart.org/content/click) | CC0 1.0 | None required; credit retained voluntarily |
| `sfx_match` | Pop sounds, `pop1.wav` | EZduzziteh | [Source page](https://opengameart.org/content/pop-sounds-0) | CC0 1.0 | None required; credit retained voluntarily |
| `sfx_stress_max` | Short alarm | yd | [Source page](https://opengameart.org/content/short-alarm) | CC0 1.0 | None required; credit retained voluntarily |

## Asset identity

| App file under `app/src/main/res/raw/` | Verified original download | Original SHA-256 |
| --- | --- | --- |
| `bgm_gameplay.ogg` | [cozy_puzzle_in-game_1_bpm118_0.ogg](https://opengameart.org/sites/default/files/cozy_puzzle_in-game_1_bpm118_0.ogg) | `afa440cb8814907b215b285a0503d39b125b6c83d9fca3f686c969107f04c7f7` |
| `sfx_tile_click.wav` | [click.wav](https://opengameart.org/sites/default/files/click.wav) | `9e8dbbd40836eaa3f8305403e869fbee0752e85fcb86b3a536fb03844f40912e` |
| `sfx_match.wav` | [pop1.wav](https://opengameart.org/sites/default/files/pop1.wav) | `758416a61890a61f4e1f00a65531e75e7d867cad1e96392d829860b8aa3a66f6` |
| `sfx_stress_max.ogg` | [alarm_0.ogg](https://opengameart.org/sites/default/files/alarm_0.ogg) | `dc76a67748c9cef0b91913fafbe47cf8ee4499c4f813dbe12e028d2806f1eab8` |

The BGM, match, and warning files are unchanged from these downloads. The BGM also embeds title `Cozy Puzzle In-Game 1` and artist `MintoDog`.

The click is derived from the verified original, not replaced: all 1,688 mono samples at 44,100 Hz were read from its complete data chunk, converted from signed 24-bit to signed 16-bit PCM by rounding, and written with consistent RIFF/data lengths. No resampling, gain change, or trimming was applied. Maximum quantization error was 128 units in the original 24-bit scale. The resulting file is 3,420 bytes with SHA-256 `bbc06c5edf33d943edcc565553c94a31404672eadbf28634e017b9dd6cf85641`. The malformed original RIFF header excluded its final four samples from strict RIFF readers; conversion preserves those samples as well.

## Playback lifetime and QA

AppContainer owns the single audio manager. The navigation destination controls gameplay music, while MainActivity start/stop controls application visibility. Rotation does not release the shared pool. Leaving a running game stops effects; finishing a game pauses music but lets short effects finish through Result navigation and ViewModel cleanup. SoundPool stays application-owned until actual backgrounding or SFX-off releases it, including pending loads. Entering a new gameplay session clears old effects. No navigation delay is added.

BGM reports actual playback state and retries failed starts/errors at one-second intervals, with at most three attempts per playback activation. Deactivation cancels retries. Each SoundPool sample is loaded once per pool lifetime, with a five-second load timeout and a bounded pending queue. Failed samples discard requests until background/foreground or SFX off/on recreates the pool. This avoids tight retries and stale playback.

Manual device checks: confirm the final match remains audible on Result; BGM stops at win/loss; Home/lock/background stops all audio; rotation and repeated Back/retry do not overlap music; SFX-off stops completion sounds; Stress warnings occur only on non-terminal maximum crossings; check rapid taps, cold decoding, volume balance, loop seam, and settings after process restart. Test the minimum supported Android version and a current device. Android decoder/device playback still requires manual verification.
