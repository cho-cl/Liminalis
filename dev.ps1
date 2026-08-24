<#
    Builds Liminalis and drops the jar into the local test server.

    Usage:
      .\dev.ps1           build, run tests, deploy the jar
      .\dev.ps1 -Run      the above, then start the test server
      .\dev.ps1 -SkipTests    deploy without running the test suite
#>
param(
    [switch]$Run,
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

$mvnArgs = @('-B', 'clean', 'package')
if ($SkipTests) { $mvnArgs += '-DskipTests' }

Write-Host 'Building...' -ForegroundColor Cyan
& mvn @mvnArgs
if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE" }

$jar = Get-ChildItem -Path (Join-Path $root 'liminalis-plugin\target') -Filter 'Liminalis-*.jar' |
    Where-Object { $_.Name -notlike 'original-*' } |
    Select-Object -First 1
if (-not $jar) { throw 'No plugin jar found in liminalis-plugin\target' }

$plugins = Join-Path $root 'server\plugins'
if (-not (Test-Path $plugins)) { New-Item -ItemType Directory -Force $plugins | Out-Null }

# Remove older builds so the server never loads two copies of the plugin.
Get-ChildItem $plugins -Filter 'Liminalis-*.jar' | Remove-Item -Force
Copy-Item $jar.FullName $plugins
Write-Host "Deployed $($jar.Name) -> server\plugins" -ForegroundColor Green

if ($Run) {
    $server = Get-ChildItem -Path (Join-Path $root 'server') -Filter 'paper-*.jar' | Select-Object -First 1
    if (-not $server) { throw 'No Paper server jar found in server\' }
    Push-Location (Join-Path $root 'server')
    try {
        & java -Xmx2G -XX:+UseG1GC -jar $server.Name nogui
    } finally {
        Pop-Location
    }
}
