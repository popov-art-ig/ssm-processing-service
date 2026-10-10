<#
.SYNOPSIS
    Проверка, что изменения в .java-файлах касаются только комментариев.

.DESCRIPTION
    Скрипт сравнивает версию файла из git-ревизии (по умолчанию HEAD) с рабочей копией
    после удаления всех комментариев. Если нормализованный код совпадает, значит правки
    в файле не изменили исполняемый код — менялись только комментарии.

    Это контроль для ветки документации (doc/CODE_COMMENTS.md §9): логику в ней менять
    нельзя. Эвристика «удалить /* ... */ и // ...» не разбирает строковые литералы, но для
    проверки «код не изменился» этого достаточно: если бы код изменился, нормализованные
    версии почти наверняка разошлись бы и скрипт сообщил бы о файле.

.PARAMETER Base
    Git-ревизия для сравнения. По умолчанию HEAD.

.PARAMETER Paths
    Пути/шаблоны для проверки. По умолчанию src/main/java.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File doc/tools/VerifyCommentsOnly.ps1
    powershell -ExecutionPolicy Bypass -File doc/tools/VerifyCommentsOnly.ps1 -Base main
#>
[CmdletBinding()]
param(
    [string]$Base = "HEAD",
    [string[]]$Paths = @("src/main/java", "src/test/java")
)

$ErrorActionPreference = "Stop"

# git вызывается напрямую (не через PowerShell-конвейер) и читается как UTF-8: при обычном
# `& git ...` Windows PowerShell декодирует вывод в кодировке консоли (CP866/CP1251), из-за
# чего русские строковые литералы в коде выглядели как расхождение. Рабочая копия читается
# тоже с явным UTF-8: Get-Content в PowerShell 5.1 без -Encoding читает как ANSI.
$gitExe = (Get-Command git).Source
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Invoke-Git([string[]]$arguments) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $gitExe
    $psi.Arguments = ($arguments | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }) -join ' '
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.StandardOutputEncoding = $utf8
    $psi.StandardErrorEncoding = $utf8
    $proc = [System.Diagnostics.Process]::Start($psi)
    $stdout = $proc.StandardOutput.ReadToEnd()
    $proc.StandardError.ReadToEnd() | Out-Null
    $proc.WaitForExit()
    return [PSCustomObject]@{ ExitCode = $proc.ExitCode; Output = $stdout }
}

function Get-GitFileContent([string]$revision, [string]$path) {
    $result = Invoke-Git @('show', "${revision}:$path")
    if ($result.ExitCode -ne 0) { return $null }
    return $result.Output
}

function Read-SourceFile([string]$path) {
    return [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $path).Path, $utf8)
}

function Remove-SourceComments([string]$text) {
    if ($null -eq $text) { return "" }
    # Комментарии заменяются пробелами, а не удаляются: структура строк сохраняется, поэтому
    # любое реальное изменение кода видно как расхождение. Пустые строки и отступы затем
    # отбрасываются — на исполняемый код они не влияют.
    $t = [regex]::Replace($text, '/\*[^*]*\*+(?:[^/*][^*]*\*+)*/', ' ')
    $t = [regex]::Replace($t, '(?m)//[^\r\n]*', ' ')
    $lines = $t -split "\r?\n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
    ($lines -join "`n")
}

$changed = @()
foreach ($p in $Paths) {
    $result = Invoke-Git @('diff', '--name-only', $Base, '--', $p)
    if ($result.ExitCode -ne 0) { throw "git diff failed for $p" }
    $files = $result.Output -split "\r?\n" | Where-Object { $_ -ne '' }
    if ($files) { $changed += $files }
}

$changed = $changed | Where-Object { $_ -match '\.java$' } | Sort-Object -Unique
if (-not $changed) {
    Write-Host "No changed .java files vs $Base"
    exit 0
}

$codeChanges = @()
$commentOnly = 0

foreach ($file in $changed) {
    if (-not (Test-Path -LiteralPath $file)) {
        # Удалённый файл — это изменение кода по определению.
        $codeChanges += "$file (deleted)"
        continue
    }

    $baseText = Get-GitFileContent -revision $Base -path $file
    if ($null -eq $baseText) {
        # Файла нет в базовой ревизии — новый файл, сравнивать не с чем.
        $codeChanges += "$file (new file)"
        continue
    }

    $baseNorm = Remove-SourceComments $baseText
    $workNorm = Remove-SourceComments (Read-SourceFile $file)

    if ($baseNorm -eq $workNorm) {
        $commentOnly++
    } else {
        $codeChanges += $file
    }
}

Write-Host ""
Write-Host "Comments-only check vs $Base"
Write-Host ("  files changed      : {0}" -f $changed.Count)
Write-Host ("  comments only      : {0}" -f $commentOnly)
Write-Host ("  code also changed  : {0}" -f $codeChanges.Count)

if ($codeChanges.Count -gt 0) {
    Write-Host ""
    Write-Host "Files where non-comment code differs (review required):"
    $codeChanges | ForEach-Object { Write-Host "  $_" }
    exit 2
}

exit 0
