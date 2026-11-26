$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$releaseId = Get-Date -Format 'yyyyMMdd-HHmmss'
$inputDir = Join-Path $root "target\portable-build-$releaseId\input"
$stageDir = Join-Path $root "target\portable-build-$releaseId\staging"
$outputDir = Join-Path $root "target\distribution\$releaseId"
# Use the JDK behind java, not unrelated jar/jpackage installations on PATH.
$javaSettings = cmd.exe /c "java -XshowSettings:properties -version 2>&1"
$jdkHome = (($javaSettings | Select-String '^\s*java.home = (.+)$').Matches.Groups[1].Value).Trim()
if (-not $jdkHome -or -not (Test-Path (Join-Path $jdkHome 'bin\jpackage.exe'))) { throw 'A full JDK 21 is required.' }
if (-not ((Get-Content (Join-Path $jdkHome 'release')) -match '^JAVA_VERSION="21[.]')) { throw 'Use JDK 21 to build the portable client.' }
$env:JAVA_HOME = $jdkHome
$resolvedOutput = [IO.Path]::GetFullPath($outputDir)
$resolvedTarget = [IO.Path]::GetFullPath((Join-Path $root 'target'))
if (-not $resolvedOutput.StartsWith($resolvedTarget + [IO.Path]::DirectorySeparatorChar)) { throw 'Unsafe output path.' }
& (Join-Path $root 'mvnw.cmd') -q package dependency:copy-dependencies "-DincludeScope=runtime" `
    "-DincludeGroupIds=com.fasterxml.jackson.core,org.openjfx" "-DoutputDirectory=$inputDir"
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
New-Item -ItemType Directory -Path $stageDir -Force | Out-Null
$classes = Join-Path $root 'target\classes'
foreach ($relative in @('com\poker\client', 'com\poker\protocol', 'com\poker\model', 'com\poker\ui')) {
    $destination = Join-Path $stageDir (Split-Path $relative -Parent)
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $classes $relative) -Destination $destination -Recurse
}
foreach ($relative in @('com\poker\game\Pot.class', 'com\poker\game\Street.class',
    'com\poker\game\HandPhase.class', 'com\poker\common\DealTiming.class',
    'com\poker\server\table\SeatSnapshot.class', 'com\poker\server\table\TableSnapshot.class',
    'com\poker\server\table\TableAction.class',
    'com\poker\PokerClientLauncher.class', 'com\poker\PokerJavaFxClientMain.class')) {
    $destination = Join-Path $stageDir (Split-Path $relative -Parent)
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $classes $relative) -Destination $destination
}
& (Join-Path $jdkHome 'bin\jar.exe') --create --file (Join-Path $inputDir 'PokerClient.jar') -C $stageDir .
if ($LASTEXITCODE -ne 0) { throw 'Client JAR build failed.' }
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
& (Join-Path $jdkHome 'bin\jpackage.exe') --type app-image --name PokerClient --input $inputDir `
    --main-jar PokerClient.jar --main-class com.poker.PokerClientLauncher `
    --dest $outputDir --java-options '-Dfile.encoding=UTF-8'
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }
$zip = Join-Path $outputDir "PokerClient-Windows-x64-$releaseId.zip"
$contents = @((Join-Path $outputDir 'PokerClient'))
$guide = Get-ChildItem (Join-Path $root 'docs\Huong_dan_may_khach_Poker_*.docx') | Sort-Object Name -Descending | Select-Object -First 1
if ($guide) {
    Copy-Item -LiteralPath $guide.FullName -Destination $outputDir
    $contents += Join-Path $outputDir $guide.Name
}
Compress-Archive -Path $contents -DestinationPath $zip
Get-FileHash -LiteralPath $zip -Algorithm SHA256 | Format-List
Write-Host "Portable client: $zip"
