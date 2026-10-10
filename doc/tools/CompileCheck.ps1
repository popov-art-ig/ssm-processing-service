<#
.SYNOPSIS
    Быстрая проверка компиляции main и test без сети, Docker и Gradle-демона.

.DESCRIPTION
    Скрипт собирает classpath из уже скачанных Gradle-зависимостей
    (%USERPROFILE%\.gradle\caches\modules-2\files-2.1), берёт JDK 21 из
    %USERPROFILE%\.gradle\jdks и компилирует src/main/java, а при -WithTests — ещё и
    src/test/java поверх скомпилированного main.

    Зачем это нужно: `./gradlew verifyTestCompilation` требует запуска Gradle-демона,
    доступа к ~/.gradle на запись и (при пустом кэше) сети. Для ветки документации
    (doc/CODE_COMMENTS.md §8) достаточно убедиться, что комментарии не сломали
    компиляцию — этот скрипт делает ровно это за считанные секунды и ничего не пишет
    за пределы временного каталога.

.PARAMETER WithTests
    Компилировать также src/test/java.

.PARAMETER Jdk
    Путь к JDK 21. По умолчанию ищется в %USERPROFILE%\.gradle\jdks.

.PARAMETER KeepOutput
    Не удалять каталоги с классами после проверки (для повторного анализа).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File doc/tools/CompileCheck.ps1
    powershell -ExecutionPolicy Bypass -File doc/tools/CompileCheck.ps1 -WithTests
#>
[CmdletBinding()]
param(
    [switch]$WithTests,
    [string]$Jdk,
    [switch]$KeepOutput
)

$ErrorActionPreference = "Stop"

function Find-Jdk21 {
    param([string]$Explicit)
    if ($Explicit) { return $Explicit }
    $root = Join-Path $env:USERPROFILE ".gradle\jdks"
    if (Test-Path $root) {
        $candidate = Get-ChildItem $root -Directory |
            Where-Object { $_.Name -match '21' -and (Test-Path (Join-Path $_.FullName 'bin\javac.exe')) } |
            Select-Object -First 1
        if ($candidate) { return $candidate.FullName }
    }
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
        return $env:JAVA_HOME
    }
    throw "JDK 21 not found. Pass -Jdk <path>."
}

function Get-DependencyJars {
    $root = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1"
    if (-not (Test-Path $root)) { throw "Gradle dependency cache not found: $root" }
    Get-ChildItem $root -Recurse -Filter *.jar |
        Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' }
}

function Invoke-Javac {
    param(
        [string]$JavacPath,
        [string]$ClassPath,
        [string]$ProcessorPath,
        [string]$Destination,
        [string[]]$Sources,
        [string]$ArgFileSuffix
    )

    if (Test-Path $Destination) { Remove-Item -Recurse -Force $Destination }
    New-Item -ItemType Directory -Path $Destination | Out-Null

    # javac на Windows не принимает длинную командную строку, поэтому аргументы идут
    # через @argfile. Обратные слэши заменяются на прямые: в argfile они экранируют символы.
    $argFile = Join-Path $env:TEMP "dsh-javac-$ArgFileSuffix.txt"
    $lines = @('-nowarn', '-encoding', 'UTF-8')
    if ($ProcessorPath) { $lines += ('-processorpath ' + $ProcessorPath) }
    $lines += ('-cp ' + $ClassPath)
    $lines += ('-d ' + $Destination)
    $lines += $Sources
    Set-Content -LiteralPath $argFile -Value $lines -Encoding ascii

    # javac пишет примечания и предупреждения в stderr. При $ErrorActionPreference='Stop'
    # PowerShell превращает их в terminating error и обрывает скрипт, поэтому на время вызова
    # понижаем строгость, а провал определяем только по коду возврата javac.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $JavacPath "@$argFile" 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    return [PSCustomObject]@{ ExitCode = $code; Output = $output; ArgFile = $argFile }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$jdkPath = Find-Jdk21 -Explicit $Jdk
$javac = Join-Path $jdkPath 'bin\javac.exe'
Write-Host "JDK      : $jdkPath"

$jars = Get-DependencyJars
$classPath = (($jars | ForEach-Object { $_.FullName.Replace('\', '/') }) -join ';')
$lombok = ($jars | Where-Object { $_.Name -like 'lombok-*.jar' } | Select-Object -First 1).FullName.Replace('\', '/')
if (-not $lombok) { throw "lombok jar not found in Gradle cache (annotation processing needs it)." }
Write-Host "jars     : $($jars.Count)"

$mainOut = (Join-Path $env:TEMP 'dsh-classes-main').Replace('\', '/')
$mainSources = Get-ChildItem -Recurse -File -Filter *.java (Join-Path $repoRoot 'src\main\java') |
    ForEach-Object { $_.FullName.Replace('\', '/') }

Write-Host "compiling: src/main/java ($($mainSources.Count) files)"
$main = Invoke-Javac -JavacPath $javac -ClassPath $classPath -ProcessorPath $lombok `
    -Destination $mainOut -Sources $mainSources -ArgFileSuffix 'main'

if ($main.ExitCode -ne 0) {
    Write-Host "MAIN COMPILE FAILED" -ForegroundColor Red
    $main.Output | Select-Object -First 60 | ForEach-Object { Write-Host $_ }
    exit 1
}
Write-Host "main     : OK" -ForegroundColor Green

if ($WithTests) {
    $testSources = Get-ChildItem -Recurse -File -Filter *.java (Join-Path $repoRoot 'src\test\java') |
        ForEach-Object { $_.FullName.Replace('\', '/') }
    $testOut = (Join-Path $env:TEMP 'dsh-classes-test').Replace('\', '/')
    Write-Host "compiling: src/test/java ($($testSources.Count) files)"
    $test = Invoke-Javac -JavacPath $javac -ClassPath ($mainOut + ';' + $classPath) -ProcessorPath $lombok `
        -Destination $testOut -Sources $testSources -ArgFileSuffix 'test'

    if ($test.ExitCode -ne 0) {
        Write-Host "TEST COMPILE FAILED" -ForegroundColor Red
        $test.Output | Select-Object -First 80 | ForEach-Object { Write-Host $_ }
        exit 1
    }
    # javac пишет предупреждения («unchecked or unsafe operations») в stderr; при
    # перенаправлении 2>&1 PowerShell оформляет их как объекты ошибок. Показываем их,
    # но провалом не считаем — критерий один: код возврата javac.
    $warnings = $test.Output | Where-Object { $_ -match 'warning|Note:' }
    if ($warnings) {
        $warnings | Select-Object -First 10 | ForEach-Object { Write-Host "test (warning): $_" -ForegroundColor DarkYellow }
    }
    Write-Host "test     : OK" -ForegroundColor Green
}

if (-not $KeepOutput) {
    foreach ($dir in @($mainOut, (Join-Path $env:TEMP 'dsh-classes-test'))) {
        if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
    }
}

Write-Host "COMPILE CHECK PASSED" -ForegroundColor Green
exit 0
