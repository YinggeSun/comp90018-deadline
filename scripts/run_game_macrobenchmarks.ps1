param(
    [string]$Serial = 'emulator-5554',
    [string]$OutputDirectory = '',
    [ValidateRange(3, 50)][int]$Iterations = 5,
    [ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$RunLabel = 'qa'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = Split-Path $PSScriptRoot -Parent
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $repo "build/performance/$RunLabel" }
$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
if ($Serial -notmatch '^emulator-\d+$') { throw 'This benchmark uses 10.0.2.2 and requires an Android Emulator.' }
Push-Location $repo
try {
    $sdkLine = Get-Content local.properties | Where-Object { $_ -like 'sdk.dir=*' } | Select-Object -First 1
    $sdkPath = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\')
    $adb = Join-Path $sdkPath 'platform-tools/adb.exe'
    function Copy-BenchmarkArtifact([string]$RemotePath, [string]$Destination) {
        for ($attempt = 0; $attempt -lt 3; $attempt++) {
            & $adb -s $Serial pull $RemotePath $Destination
            if ($LASTEXITCODE -eq 0) { return }
            # ADB can briefly disconnect after a long trace; retry the copy, never the measurement.
            if ($attempt -lt 2) { Start-Sleep -Seconds 3 }
        }
        throw 'Artifact pull failed. Check ADB connectivity and use a short output path on Windows.'
    }
    $inventory = & $adb devices
    if ($LASTEXITCODE -ne 0 -or -not ($inventory -match "^$Serial\s+device$")) { throw 'Requested emulator is not ready.' }
    foreach ($port in @(9099, 8080)) {
        $socket = [System.Net.Sockets.TcpClient]::new()
        try {
            if (-not $socket.ConnectAsync('127.0.0.1', $port).Wait(2000)) { throw "Local Firebase port $port unavailable." }
        } finally { $socket.Dispose() }
    }
    & .\gradlew.bat :app:assembleBenchmark :macrobenchmark:assembleBenchmark --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Benchmark build failed.' }
    [xml]$manifest = Get-Content app/build/intermediates/merged_manifests/benchmark/processBenchmarkManifest/AndroidManifest.xml
    $android = 'http://schemas.android.com/apk/res/android'
    if ($manifest.manifest.package -ne 'com.comp90018.deadline.emulator' -or
        $manifest.manifest.application.GetAttribute('name', $android) -ne 'com.comp90018.deadline.testing.EmulatorApplication' -or
        $manifest.manifest.application.GetAttribute('debuggable', $android) -eq 'true') {
        throw 'Target must be the isolated, non-debuggable benchmark APK.'
    }
    & $adb -s $Serial install -r app/build/outputs/apk/benchmark/app-benchmark.apk
    if ($LASTEXITCODE -ne 0) { throw 'Target install failed.' }
    & $adb -s $Serial install -r macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk
    if ($LASTEXITCODE -ne 0) { throw 'Driver install failed.' }
    $class = 'com.comp90018.deadline.macrobenchmark.GameBenchmarks'
    $runner = 'com.comp90018.deadline.macrobenchmark/androidx.test.runner.AndroidJUnitRunner'
    $remote = '/sdcard/Android/media/com.comp90018.deadline.macrobenchmark'
    foreach ($phase in @('cold', 'frames')) {
        $destination = Join-Path $OutputDirectory $phase
        New-Item -ItemType Directory -Force $destination | Out-Null
        $arguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-e', 'androidx.benchmark.suppressErrors', 'EMULATOR',
            '-e', 'qaBenchmarkIterations', "$Iterations", '-e', 'qaRunLabel', $RunLabel)
        if ($phase -eq 'cold') {
            $arguments += @('-e', 'class', "$class#coldStartup")
        } else {
            $arguments += @('-e', 'androidx.benchmark.fullTracing.enable', 'true', '-e', 'class',
                "$class#generatedLevel1Interactions,$class#generatedLevel6Interactions,$class#generatedLevel6HudTicks")
        }
        $result = & $adb @arguments $runner 2>&1
        $result | Set-Content (Join-Path $destination 'instrumentation.log')
        # am instrument can return exit code 0 on JUnit failures; require its success summary.
        if ($LASTEXITCODE -ne 0 -or -not ($result -match '^OK \(\d+ tests?\)')) { throw "Benchmark $phase failed; inspect instrumentation.log." }
        $names = & $adb -s $Serial shell ls $remote
        if ($LASTEXITCODE -ne 0) { throw 'Could not list benchmark outputs.' }
        foreach ($name in $names) {
            if ($name -match '^GameBenchmarks_[a-zA-Z0-9_.-]+\.perfetto-trace$' -or $name -eq 'com.comp90018.deadline.macrobenchmark-benchmarkData.json') {
                Copy-BenchmarkArtifact "$remote/$name" $destination
            }
        }
    }
    Copy-BenchmarkArtifact "/sdcard/Android/data/com.comp90018.deadline.emulator/files/issue48-runtime-$RunLabel.csv" $OutputDirectory
} finally {
    if (Get-Variable adb -ErrorAction SilentlyContinue) {
        & $adb -s $Serial shell cmd deviceidle tempwhitelist -r com.comp90018.deadline.emulator
    }
    Pop-Location
}
