# Issue #65 verification scripts

Run these commands from the repository root. None deploy rules, commit, push, or create a PR.

## Local Firestore rules checks

```text
python scripts/test_firebase_emulator_rules.py
```

Requires Python 3.9+ (standard library only) and running Auth, Firestore and hub emulators
on localhost ports 9099, 8080 and 4400 for `demo-deadline`. Works on Windows, macOS and Linux.
Use normal Python execution, without `-O`, so test assertions remain enabled.

The runner rejects non-loopback destinations and redirects, disables proxies, validates
Auth token project claims, and checks that the running rules match the local file. It uses
user tokens rather than admin authorization. The literal `emulator-password` is a disposable
test fixture, not a production credential. Unique accounts/documents remain in the local
emulators. Results and coverage are written only under ignored `build/`.

## Android Firebase instrumentation (Windows PowerShell)

Set `JAVA_HOME` to an installed JDK 17 and configure `sdk.dir` in the untracked
`local.properties`. Start the local Firebase emulators and an Android Emulator, then run:

```powershell
.\scripts\run_firebase_android_tests.ps1 -Serial emulator-5554
```

This wrapper intentionally uses Windows `gradlew.bat` and `adb.exe`; it is not a portable
Unix shell script. The serial may be changed, but must identify an Android Emulator.
Physical devices are unsupported because `10.0.2.2` is the virtual-device host alias.

It validates generated demo-only resources and manifests before installation, targets the
separate `.emulator` package, verifies installed INTERNET permission, and executes the 50
selected Firebase tests. A per-package Android background-network exemption is removed in
`finally` and expires after at most ten minutes if the host process is interrupted. It does
not disable any global firewall or weaken the app's emulator reachability guard. The
isolated APKs remain installed. A much longer test run requires an explicitly revised
exemption duration. The existing suites clear the local demo Firestore database; run them
only against a disposable emulator, without concurrent tests sharing that database.

See [Android startup diagnosis](../docs/issue-65-startup-timeout-diagnosis.md) for evidence,
the exact expanded Gradle command and the successful 50-test result.

## Read a debug app's saved UID

```text
python scripts/read_debug_firebase_identity.py --serial emulator-5554
```

Requires Python 3.9+ and an already installed, debuggable `com.comp90018.deadline` app.
ADB is discovered from `local.properties`; `--adb /path/to/adb` overrides it. The Python
helper supports Windows, macOS and Linux with the corresponding SDK path. It uses this
checkout's existing `app/google-services.json` solely to select the default app's local
preference file; it does not print configuration values or make Firebase requests.

The helper refuses release/non-debuggable apps. It prints only saved UID and anonymous
status, keeps raw preference data in process memory, and suppresses credential-bearing
output and parser errors. It does not start the app, sign in/out, clear data or modify files
on the device. Firebase's private storage format is version-dependent; an unknown format
fails closed. This is a persisted-state diagnostic, not an in-memory debugger snapshot.

For UID preservation verification, run it before registration, register through the same
installed app's **Create Account** screen, wait for completion, then rerun it. The UID must
stay identical while `anonymous` changes from `true` to `false`. Never uninstall, clear
data or sign out between those observations. Do not paste raw Firebase preferences or
capture tokens in logs. No UID logging is added to either debug or release app code.
