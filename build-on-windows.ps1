$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$cache = Join-Path $PSScriptRoot '.gradle-cache'
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = $cache }
try { $javacVersion = (& javac -version 2>&1 | Out-String).Trim() } catch { throw '请先安装 JDK 21，并把其 bin 目录加入 PATH。' }
if ($javacVersion -notmatch '^javac 21([. ]|$)') { throw "需要 JDK 21，当前是 $javacVersion" }
& .\gradlew.bat --no-daemon assemble
if ($LASTEXITCODE -ne 0) { throw 'Fabric 构建失败；请保留完整错误日志供修复。' }
Write-Host '构建完成。请检查 build/libs 下与当前项目版本对应的 ptcg-craft-*.jar（安装时不要选择 sources.jar）。'
