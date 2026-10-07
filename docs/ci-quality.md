# Android quality checks

The `Android quality` workflow runs on every PR into `main` (including forks),
pushes to `main`, and manual runs. It uses Java 17, Gradle 8.7 from the checked-in
wrapper, Android SDK 34, and build tools 34.0.0. SDK command-line tools are pinned
to build 12266719 (16.0), compatible with this Java 17 setup. Actions use the
Node 24 runtime and are pinned to commit SHAs.
The workflow has read-only repository permissions and needs no secrets.

## What must pass

Run these from the repository root with Java 17 and an installed Android SDK:

```bash
bash ./gradlew ktlintCheck :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

On Windows, use `gradlew.bat` instead of `bash ./gradlew`. Set `JAVA_HOME` to a
Java 17 installation and configure `ANDROID_HOME` or an ignored `local.properties`.
Android Studio's bundled JDK may be newer than the version this project supports.

CI reports formatting, JVM tests, APK compilation, and Android lint separately.
It attempts the remaining QA steps after a failure and uploads reports under
`android-quality-reports` for 14 days. A cancelled run is not a passing check.
Device/Compose instrumentation and physical sensor tests are separate QA tasks;
this workflow does not claim to run them. Android lint errors fail the job;
existing warnings are reported but are not treated as errors.

## Formatting baseline

ktlint-gradle 12.1.1 runs ktlint 1.2.1 with Android style. Both are pinned.
The root and `app` module baselines record existing violations at introduction.
New files and violations outside those baseline entries fail `ktlintCheck`.
Baseline entries are tied to file/rule/line locations, so editing an existing
file can expose old violations too; fix those rather than expanding the baseline.

Do not regenerate the baseline to make a PR green. Any baseline additions need
an explicit explanation and reviewer approval. Formatting debt should be removed
in separate changes. `ktlintFormat` can modify many existing files and does not
respect the baseline; inspect its diff carefully before committing.

## Make the check required (repository administrator)

The contributor account can push branches but cannot edit repository rulesets.
CI status alone does **not** prevent a merge until the following rule is enabled:

1. Merge this workflow after its PR run passes.
2. Open Settings > Rules > Rulesets and edit the active ruleset targeting `main`.
3. Enable **Require status checks to pass**, select the GitHub Actions check
   **Android quality**, and require the branch to be up to date before merging.
4. Keep deletion/force-push protection and the PR requirement enabled. Review
   bypass permissions; users with an allowed bypass can still bypass checks.
5. Verify with a temporary PR containing a failed assertion or formatting error:
   the check must be red and merge blocked for an account without bypass rights.
   Remove the probe, rerun CI, and confirm merge becomes available.

The exact required check name is the job name `Android quality`, not the Gradle
task names or artifact name. Do not use path filters: even docs-only PRs need to
produce a check when it is required. If a merge queue is enabled later, add the
`merge_group` trigger before making this check required for that queue.

Repository rules are configured in GitHub, not by merging this file. An
administrator must complete and verify the steps above; a contributor cannot
activate required checks with a branch push.

## Existing and stacked PRs

The `pull_request.branches: [main]` filter matches the PR's **base**, not its head.
A PR targeting another feature branch will not run this workflow on a push,
even if its head contains the workflow file. After merging its dependency,
retarget it to `main`, update its branch and verify a new successful check.

PRs opened before this workflow exists need the workflow in their tested head.
Merge this CI PR first, then update those branches from `main`. If the team
wants pre-merge CI evidence, merge the CI branch into those task branches and
push them while keeping their PR base as `main`; merge the CI PR before the
dependent task PRs. Do not use an old green run as evidence for a new head.

## PR workflow

Update from `main`, create a task branch, run relevant checks, and open a PR with
scope, test results, and limitations. Inspect the Actions check and its report
artifact. Ask another team member to review before merging. Never interpret a
successful APK build as proof that JVM tests, lint, or device tests passed.
