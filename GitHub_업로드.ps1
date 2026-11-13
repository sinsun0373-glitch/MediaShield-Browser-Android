# MediaShieldBrowser private GitHub repository uploader.
# Run from PowerShell in this folder after reviewing this script.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
Write-Host 'MediaShield Browser - GitHub Actions APK 자동 빌드 등록' -ForegroundColor Cyan

foreach ($tool in @('git', 'gh')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Write-Host "필요한 프로그램이 없습니다: $tool" -ForegroundColor Red
        Write-Host 'Git: https://git-scm.com/downloads/win'
        Write-Host 'GitHub CLI: https://cli.github.com/'
        throw "Missing $tool"
    }
}

& gh auth status 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'GitHub 계정 로그인이 필요합니다. 브라우저에서 인증해 주세요.'
    & gh auth login --web
    if ($LASTEXITCODE -ne 0) { throw 'GitHub CLI login failed' }
}

$default = 'MediaShield-Browser-Android'
$name = Read-Host "만들 비공개 GitHub 저장소 이름 (Enter = $default)"
if ([string]::IsNullOrWhiteSpace($name)) { $name = $default }
if ($name -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$') {
    throw 'Repository name must use letters, numbers, dot, dash or underscore.'
}

if (Test-Path '.git') {
    throw '현재 폴더에 이미 Git 저장소가 있습니다. 기존 저장소를 덮어쓰지 않도록 중단했습니다.'
}

$owner = (& gh api user --jq .login).Trim()
$userId = (& gh api user --jq .id).Trim()
if ($LASTEXITCODE -ne 0 -or -not $owner) { throw 'Could not get GitHub account information.' }
Write-Host "대상 저장소: $owner/$name (Private)" -ForegroundColor Yellow
$answer = Read-Host '이 계정에 비공개 저장소를 만들고 전체 소스 코드를 올릴까요? (Y/N)'
if ($answer -notin @('Y','y','yes','YES')) { Write-Host '취소했습니다.'; exit 0 }

& git init -b main
if ($LASTEXITCODE -ne 0) { throw 'git init failed' }
& git add -A
if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
& git -c "user.name=$owner" -c "user.email=$userId+$owner@users.noreply.github.com" commit -m 'Initial MediaShield Android app and GitHub Actions APK build'
if ($LASTEXITCODE -ne 0) { throw 'git commit failed' }
& gh repo create "$owner/$name" --private --source=. --remote=origin --push
if ($LASTEXITCODE -ne 0) { throw 'GitHub repo creation or push failed' }
Write-Host '저장소 업로드 성공. 첫 GitHub Actions 빌드가 자동으로 시작됩니다.' -ForegroundColor Green
$actionsUrl = "https://github.com/$owner/$name/actions"
Write-Host "APK 다운로드: $actionsUrl"
Start-Process $actionsUrl
