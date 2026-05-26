$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME = Join-Path $repoRoot ".gradle-user-home"
$gradle = Join-Path $repoRoot ".tools\gradle-8.10.2\bin\gradle.bat"

Push-Location (Join-Path $repoRoot "backend")
try {
    & $gradle --no-daemon test
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
