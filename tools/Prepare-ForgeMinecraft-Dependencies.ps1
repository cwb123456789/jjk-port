$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$Java = Get-Command java -ErrorAction SilentlyContinue
if (-not $Java) { throw 'Java is not installed or not on PATH.' }
$JavaVersionLine = (& java -version 2>&1 | Select-Object -First 1).ToString()
if ($JavaVersionLine -notmatch 'version "([0-9]+)') { throw "Could not determine Java version: $JavaVersionLine" }
$JavaMajor = [int]$Matches[1]
if ($JavaMajor -lt 17) { throw "Java 17 or newer is required to run Gradle/ForgeGradle; found Java $JavaMajor." }

if (-not (Test-Path '.\gradlew.bat')) { throw 'gradlew.bat not found in project root.' }
New-Item -ItemType Directory -Force -Path '.\build\dependency-setup' | Out-Null
$Log = '.\build\dependency-setup\prepare-dependencies.log'
Write-Host "Java: $JavaVersionLine"
Write-Host 'Resolving Gradle wrapper, ForgeGradle, Minecraft mappings, Forge userdev and declared mod dependencies.'
Write-Host "First run needs internet access. Full log: $Log"
& .\gradlew.bat --no-daemon --refresh-dependencies dependencies compileJava 2>&1 | Tee-Object -FilePath $Log
if ($LASTEXITCODE -ne 0) {
    throw "Dependency preparation failed (exit $LASTEXITCODE). Check $Log for the first missing URL/artifact."
}
Write-Host 'Dependency resolution and compileJava completed.'
Write-Host "Gradle cache: $(if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $HOME '.gradle' })"
