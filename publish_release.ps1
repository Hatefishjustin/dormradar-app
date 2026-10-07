# DormRadar release publisher: upload APK to Gitee Release (for in-app auto update).
# Usage (PowerShell, from repo root):
#   .\publish_release.ps1 -ApkPath "app\build\outputs\apk\debug\app-debug.apk" -Version "v0.6.1" -Code 12 -NotesFile "notes.txt"
# NotesFile must be UTF-8 text (release body, shown in the update dialog).
# APK asset is auto-renamed to dormradar-<version-without-v>-<code>.apk (App parses versionCode from it).
# Token is hardcoded here + in UpdateConfig.kt (repo is PRIVATE; reset it on Gitee if leaked).

param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][int]$Code,
    [string]$NotesFile = "notes.txt"
)

$ErrorActionPreference = "Stop"

$Owner = "hatefishjustin"
$Repo = "dormradar-update"
$Token = "219e1a35842b42531dbd4553f51b68f5"
$Api = "https://gitee.com/api/v5/repos/$Owner/$Repo"

$ver = $Version.TrimStart("v")
$apkName = "dormradar-$ver-$Code.apk"

if (-not (Test-Path $ApkPath)) { Write-Error "APK not found: $ApkPath"; exit 1 }
if (-not (Test-Path $NotesFile)) { Write-Error "Notes file not found: $NotesFile"; exit 1 }

Write-Host "[1/3] Creating Release $Version (tag=$ver) ..."
$createJson = & curl.exe -s -m 60 -X POST "$Api/releases?access_token=$Token" `
    -d "access_token=$Token" `
    -d "tag_name=$ver" `
    -d "name=$Version" `
    --data-urlencode "body@$NotesFile" `
    -d "target_commitish=master"
$release = $createJson | ConvertFrom-Json
if (-not $release.id) { Write-Error "Create release failed: $createJson"; exit 1 }
$releaseId = $release.id
Write-Host "      ok, release id=$releaseId"

Write-Host "[2/3] Uploading $ApkPath as $apkName ..."
$uploadJson = & curl.exe -s -m 600 -X POST "$Api/releases/$releaseId/attach_files?access_token=$Token" `
    -F "file=@$ApkPath;filename=$apkName;type=application/vnd.android.package-archive"
$upload = $uploadJson | ConvertFrom-Json
if ($upload.error_message) { Write-Error "Upload failed: $($upload.error_message)"; exit 1 }
Write-Host "      ok"

Write-Host "[3/3] Done. App will detect $Version (code $Code) on next launch."
