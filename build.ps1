# Builds the Slay the Spire Auto API mod.
#
# Two modes:
#  1. REAL build (preferred): compiles against the actual game jar
#     (desktop-1.0.jar) and ModTheSpire.jar, then installs the mod into the
#     game's mods folder.
#  2. OFFLINE check build: if the game jars are not found, compiles against
#     the minimal API stubs in ./stubs so the sources and jar can still be
#     produced and verified. The resulting jar only references game classes
#     that were cross-checked against CommunicationMod, and it resolves them
#     against the real game at runtime.
#
# Usage:
#   .\build.ps1                          (auto-detect game + JDK)
#   .\build.ps1 -GameDir "F:\SteamLibrary\steamapps\common\SlayTheSpire"
#   .\build.ps1 -JdkHome "C:\path\to\jdk"

param(
    [string]$GameDir = "",
    [string]$JdkHome = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $root

# ---------------------------------------------------------------- JDK
function Find-Javac {
    if ($JdkHome) {
        $c = Join-Path $JdkHome "bin\javac.exe"
        if (Test-Path $c) { return $c }
    }
    $cmd = Get-Command javac -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    if ($env:JAVA_HOME) {
        $c = Join-Path $env:JAVA_HOME "bin\javac.exe"
        if (Test-Path $c) { return $c }
    }
    foreach ($p in @(
        "C:\Program Files\Java",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Microsoft",
        "C:\Program Files\Amazon Corretto",
        "C:\Program Files\Zulu"
    )) {
        if (Test-Path $p) {
            $hit = Get-ChildItem $p -Recurse -Filter "javac.exe" -ErrorAction SilentlyContinue |
                Select-Object -First 1
            if ($hit) { return $hit.FullName }
        }
    }
    $tools = Join-Path $repoRoot "tools"
    if (Test-Path $tools) {
        $hit = Get-ChildItem $tools -Directory -Filter "jdk*" -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName "bin\javac.exe" } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
        if ($hit) { return $hit }
    }
    return ""
}

# ---------------------------------------------------------------- game
function Get-SteamLibraries {
    $steamPaths = @()
    foreach ($key in @(
        "HKCU:\Software\Valve\Steam",
        "HKLM:\SOFTWARE\WOW6432Node\Valve\Steam",
        "HKLM:\SOFTWARE\Valve\Steam"
    )) {
        if (Test-Path $key) {
            $prop = if ($key -like "HKCU:*") { "SteamPath" } else { "InstallPath" }
            $v = (Get-ItemProperty $key -ErrorAction SilentlyContinue).$prop
            if ($v) { $steamPaths += $v }
        }
    }
    $libs = @()
    foreach ($steam in ($steamPaths | Select-Object -Unique)) {
        $libs += $steam
        $vdf = Join-Path $steam "steamapps\libraryfolders.vdf"
        if (Test-Path $vdf) {
            foreach ($line in Get-Content $vdf) {
                if ($line -match '"path"\s+"(.*)"') {
                    $libs += $matches[1]
                }
            }
        }
    }
    return ($libs | Select-Object -Unique)
}

function Find-GameDir {
    $candidates = @()
    foreach ($lib in (Get-SteamLibraries)) {
        $candidates += Join-Path $lib "steamapps\common\SlayTheSpire"
    }
    foreach ($c in ($candidates | Select-Object -Unique)) {
        if (Test-Path (Join-Path $c "desktop-1.0.jar")) { return $c }
    }
    return ""
}

# ModTheSpire jar: prefer the game's mods folder, fall back to the
# Steam Workshop edition (workshop/content/646570/1605060445).
function Find-MtsJar([string]$gameDir) {
    $m = Join-Path $gameDir "mods\ModTheSpire.jar"
    if (Test-Path $m) { return $m }
    foreach ($lib in (Get-SteamLibraries)) {
        $w = Join-Path $lib "steamapps\workshop\content\646570\1605060445\ModTheSpire.jar"
        if (Test-Path $w) { return $w }
    }
    return ""
}

# ---------------------------------------------------------------- main
$javac = Find-Javac
if (-not $javac) {
    Write-Host "ERROR: javac not found. Install a JDK (Temurin 8 or 17) or pass -JdkHome."
    exit 1
}

if (-not $GameDir) {
    $GameDir = Find-GameDir
}

$gameJar = ""
$mtsJar = ""
$modsDir = ""
$mode = "OFFLINE-CHECK"
if ($GameDir) {
    $g = Join-Path $GameDir "desktop-1.0.jar"
    $modsDir = Join-Path $GameDir "mods"
    $m = Find-MtsJar $GameDir
    if ((Test-Path $g) -and $m) {
        $gameJar = $g
        $mtsJar = $m
        $mode = "REAL"
    } elseif (Test-Path $g) {
        Write-Host "WARNING: found the game jar but no ModTheSpire.jar."
        Write-Host "         Subscribe to the Steam Workshop item (id 1605060445) or put"
        Write-Host "         ModTheSpire.jar into $modsDir for a real build."
    }
}

Write-Host "Build mode : $mode"
Write-Host "javac      : $javac"

# JDK 9+ supports --release 8 (bytecode compatible with the game's Java 8 runtime)
$verOut = & $javac -version 2>&1 | Out-String
$major = 8
if ($verOut -match 'javac\s+(?:1\.)?(\d+)') { $major = [int]$Matches[1] }
$releaseArgs = if ($major -ge 9) { @("--release", "8") } else { @("-source", "8", "-target", "8") }

$classes = Join-Path $root "build\classes"
if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$src = @(Get-ChildItem (Join-Path $root "src\main\java") -Recurse -Filter "*.java" |
    ForEach-Object { $_.FullName })

if ($mode -eq "REAL") {
    Write-Host "game jar   : $gameJar"
    Write-Host "MTS jar    : $mtsJar"
    & $javac -encoding UTF-8 @releaseArgs -cp "$gameJar;$mtsJar" -d $classes @src
} else {
    Write-Host "stubs dir  : $(Join-Path $root 'stubs')  (offline compile check)"
    $stubs = @(Get-ChildItem (Join-Path $root "stubs") -Recurse -Filter "*.java" |
        ForEach-Object { $_.FullName })
    & $javac -encoding UTF-8 @releaseArgs -d $classes @src @stubs
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed."
    exit 1
}

# Pack the mod jar: only our package + ModTheSpire.json (never the stubs).
Copy-Item (Join-Path $root "src\main\resources\ModTheSpire.json") $classes -Force
$jarTool = Join-Path (Split-Path -Parent $javac) "jar.exe"
$outDir = Join-Path $root "build"
$outJar = Join-Path $outDir "SlayTheSpireAutoAPI.jar"
if (Test-Path $outJar) { Remove-Item -Force $outJar }
& $jarTool cf $outJar -C $classes stsapi -C $classes ModTheSpire.json
if ($LASTEXITCODE -ne 0) {
    Write-Host "Jar packaging failed."
    exit 1
}

Write-Host ""
Write-Host "Built: $outJar"

if ($mode -eq "REAL" -and $modsDir -and (Test-Path $modsDir)) {
    $installed = Join-Path $modsDir "SlayTheSpireAutoAPI.jar"
    Copy-Item $outJar $installed -Force
    Write-Host "Installed: $installed"
    Write-Host "Launch the game through ModTheSpire.jar and enable 'Slay the Spire Auto API'."
} else {
    Write-Host "To install: put SlayTheSpireAutoAPI.jar into <game>\mods\ and run ModTheSpire.jar."
}
Write-Host "After launch, test with:  curl http://127.0.0.1:8080/api/state?pretty=1"
