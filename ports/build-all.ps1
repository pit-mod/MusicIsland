param(
    [Parameter(Mandatory=$true)][string]$Jdk8,
    [Parameter(Mandatory=$true)][string]$Jdk21,
    [Parameter(Mandatory=$true)][string]$Jdk25
)
$ErrorActionPreference='Stop'
$musicRoot=Split-Path $PSScriptRoot -Parent
$musicOldJava=$env:JAVA_HOME
function Invoke-MusicBuild([string]$Directory,[string]$Java,[string]$Wrapper,[string[]]$Arguments) {
    $env:JAVA_HOME=(Resolve-Path -LiteralPath $Java).Path
    Push-Location -LiteralPath (Join-Path $musicRoot $Directory)
    try { & $Wrapper @Arguments; if($LASTEXITCODE -ne 0){throw "Build failed in $Directory"} }
    finally { Pop-Location }
}
try {
    Invoke-MusicBuild '.' $Jdk8 './gradlew.bat' @('build')
    foreach($musicVersion in @('1.9.4','1.10.2','1.11.2')) {
        Invoke-MusicBuild 'ports/forge-legacy' $Jdk8 '../../gradlew.bat' @("-Pmc=$musicVersion",'build')
    }
    Invoke-MusicBuild 'ports/forge-1.12.2' $Jdk8 '../../gradlew.bat' @('build')
    Invoke-MusicBuild 'ports/forge-1.13.2' $Jdk8 './gradlew.bat' @('build')
    $musicTargets=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'fabric/targets.json') -Raw | ConvertFrom-Json
    foreach($musicVersion in $musicTargets.PSObject.Properties.Name) {
        Invoke-MusicBuild 'ports/fabric' $Jdk21 './gradlew.bat' @("-Pmc=$musicVersion",'build')
    }
    Invoke-MusicBuild 'ports/fabric-26' $Jdk25 './gradlew.bat' @('build')
    $musicDestination=Join-Path $musicRoot 'build/releases/1.1.1'
    New-Item -ItemType Directory -Path $musicDestination -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $musicRoot 'build/libs/MusicIsland-1.1.0.jar') -Destination (Join-Path $musicDestination 'MusicIsland-forge-1.8.9-1.1.0.jar')
    foreach($musicPattern in @('ports/forge-legacy/build/*/libs/*.jar','ports/forge-1.12.2/build/libs/*.jar','ports/forge-1.13.2/build/libs/*.jar','ports/fabric/build/*/libs/*.jar','ports/fabric-26/build/libs/*.jar')) {
        Get-ChildItem -Path (Join-Path $musicRoot $musicPattern) | Where-Object { $_.Name -match '^MusicIsland-forge-.*-1\.1\.0\.jar$|^MusicIsland-fabric-.*-1\.1\.1\.jar$' } | Copy-Item -Destination $musicDestination
    }
    Write-Host "Release jars: $musicDestination"
} finally { $env:JAVA_HOME=$musicOldJava }
