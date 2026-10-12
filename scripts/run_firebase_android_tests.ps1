param([string]$Serial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
try {
    $sdkLine = Get-Content local.properties | Where-Object { $_ -like 'sdk.dir=*' }
    $sdkPath = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\')
    $adb = Join-Path $sdkPath 'platform-tools/adb.exe'
    if ($Serial -notmatch '^emulator-\d+$') { throw 'This configuration requires an Android Emulator (10.0.2.2).' }
    $inventory = & $adb devices
    if ($LASTEXITCODE -ne 0 -or -not ($inventory -match "^$Serial\s+device$")) { throw "No ready Android Emulator: $Serial" }
    foreach ($port in @(9099, 8080)) {
        $socket = [System.Net.Sockets.TcpClient]::new()
        try {
            if (-not $socket.ConnectAsync('127.0.0.1', $port).Wait(2000)) { throw "Emulator port $port unavailable" }
        } finally { $socket.Dispose() }
    }
    & .\gradlew.bat :app:assembleEmulator :app:assembleEmulatorAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build failed; nothing will be installed.' }
    [xml]$manifest = Get-Content app/build/intermediates/merged_manifest/emulator/processEmulatorMainManifest/AndroidManifest.xml
    $android = 'http://schemas.android.com/apk/res/android'
    if ($manifest.manifest.package -ne 'com.comp90018.deadline.emulator' -or
        $manifest.manifest.application.GetAttribute('name', $android) -ne 'com.comp90018.deadline.testing.EmulatorApplication') {
        throw 'Wrong target application; refusing instrumentation.'
    }
    $rawManifest = $manifest.OuterXml
    foreach ($forbidden in @('FirebaseInitProvider', 'RemoteConfigRegistrar', 'RemoteConfigKtxRegistrar', 'FirebaseInstallationsRegistrar', 'FirebaseInstallationsKtxRegistrar', 'AbtRegistrar')) {
        if ($rawManifest.Contains($forbidden)) { throw "Forbidden component: $forbidden" }
    }
    [xml]$resources = Get-Content app/build/generated/res/processEmulatorGoogleServices/values/values.xml
    foreach ($pair in @{project_id='demo-deadline'; google_api_key='emulator-only'; google_app_id='1:1:android:1'}.GetEnumerator()) {
        $entry = $resources.resources.string | Where-Object { $_.name -eq $pair.Key }
        if ($entry.InnerText -ne $pair.Value) { throw "Unsafe Firebase resource: $($pair.Key)" }
    }
    [xml]$tests = Get-Content app/build/intermediates/packaged_manifests/emulatorAndroidTest/processEmulatorAndroidTestManifest/AndroidManifest.xml
    if ($tests.manifest.instrumentation.GetAttribute('name', $android) -ne 'com.comp90018.deadline.testing.EmulatorTestRunner' -or
        $tests.manifest.instrumentation.GetAttribute('targetPackage', $android) -ne 'com.comp90018.deadline.emulator') {
        throw 'Wrong instrumentation runner or target.'
    }
    $classes = @('FirebaseIsolationTest', 'FirestoreProfileEmulatorTest', 'FirestoreRulesTest',
        'FirestoreLeaderboardEmulatorTest', 'FirestoreProgressSyncEmulatorTest', 'FirestoreTransferEmulatorTest') |
        ForEach-Object { "com.comp90018.deadline.data.$_" }
    $previousSerial = $env:ANDROID_SERIAL
    # API 35 can classify instrumentation without an Activity as cached/background,
    # blocking its UID even with INTERNET granted. Scope the exemption to this demo
    # package and this run; it also expires if the host process is interrupted.
    $testPackage = 'com.comp90018.deadline.emulator'
    & $adb -s $Serial install -r app/build/outputs/apk/emulator/app-emulator.apk
    if ($LASTEXITCODE -ne 0) { throw 'Isolated target APK installation failed.' }
    $installed = & $adb -s $Serial shell dumpsys package $testPackage
    if ($LASTEXITCODE -ne 0 -or -not ($installed -match 'android.permission.INTERNET: granted=true')) {
        throw 'Installed target APK lacks INTERNET permission.'
    }
    try {
        $env:ANDROID_SERIAL = $Serial
        & $adb -s $Serial shell cmd deviceidle tempwhitelist -d 600000 $testPackage
        if ($LASTEXITCODE -ne 0) { throw 'Could not prepare test UID networking; refusing execution.' }
        & .\gradlew.bat :app:connectedEmulatorAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$($classes -join ',')" '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Instrumentation failed; inspect app/build/reports/androidTests/connected/emulator.' }
    } finally {
        & $adb -s $Serial shell cmd deviceidle tempwhitelist -r $testPackage
        if ($LASTEXITCODE -ne 0) { Write-Warning 'Temporary test UID exemption cleanup failed; it expires within 10 minutes.' }
        $env:ANDROID_SERIAL = $previousSerial
    }
} finally { Pop-Location }
