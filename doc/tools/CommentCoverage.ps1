<#
.SYNOPSIS
    Оценка покрытия исходного кода Javadoc-комментариями.

.DESCRIPTION
    Проходит по .java-файлам указанного дерева и для каждого файла определяет:
      * задокументировано ли объявление типа (class/interface/enum/record);
      * сколько объявлений методов/конструкторов задокументировано;
      * сколько полей (включая компоненты record) задокументировано.

    Проверка намеренно приблизительная: она не компилирует код и не разбирает
    синтаксис полностью, а ищет ближайший блок /** ... */ перед объявлением.
    Этого достаточно, чтобы отслеживать динамику покрытия между ревизиями.

    См. doc/CODE_COMMENTS.md — соглашение о комментариях, под которое написан скрипт.

.PARAMETER Root
    Корень поиска. По умолчанию src/main/java.

.PARAMETER Details
    Печатать таблицу по каждому файлу, а не только сводку.

.PARAMETER ByPackage
    Печатать сводку по пакетам — удобно для отчёта о покрытии.

.PARAMETER ListMissingTypes
    Печатать файлы, в которых объявление типа не задокументировано.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File doc/tools/CommentCoverage.ps1
    powershell -ExecutionPolicy Bypass -File doc/tools/CommentCoverage.ps1 -Details
    powershell -ExecutionPolicy Bypass -File doc/tools/CommentCoverage.ps1 -ByPackage
#>
[CmdletBinding()]
param(
    [string]$Root = "src/main/java",
    [switch]$Details,
    [switch]$ByPackage,
    [switch]$ListMissingTypes
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $Root)) {
    throw "Path not found: $Root"
}

# Является ли строка объявлением типа.
$typeRegex = '^\s*(?:public|protected|private|final|abstract|sealed|non-sealed|static)?[\s]*(?:public|protected|private|final|abstract|sealed|non-sealed|static[\s])*\s*(class|interface|enum|record|@interface)\s+\w+'
# Является ли строка объявлением метода/конструктора.
$methodRegex = '^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public|protected|private|static|final|synchronized|abstract|default|native)?[\s\w<>,\.\[\]\?]*\s+\w+\s*\([^;]*\)\s*(?:\{|throws)'
$fieldRegex = '^\s*(?:/\*\*.*\*/\s*)?(?:public|protected|private|static|final|transient|volatile|\s)+[\w<>,\[\]\.\?]+\s+\w+\s*(?:=[^;]*)?;'
$controlFlowRegex = '\b(if|for|while|switch|catch|return|new|throw|else|assert)\b\s*\('

$fileStats = @()

foreach ($file in Get-ChildItem -Recurse -File -Filter *.java $Root) {
    $lines = Get-Content -LiteralPath $file.FullName
    $hasTypeDoc = $false
    $types = 0
    $methods = 0
    $methodsDoc = 0
    $fields = 0
    $fieldsDoc = 0
    $hasFileDoc = ($lines | Select-Object -First 60 | Where-Object { $_ -match '/\*\*' } | Measure-Object).Count -gt 0

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]

        # Javadoc считается относящимся к объявлению, если он находится выше блока аннотаций:
        # Swagger-аннотации контроллеров и @Transactional/@Override у методов занимают до
        # нескольких десятков строк. Помимо аннотаций пропускаются строки-продолжения аннотаций,
        # закрывающие скобки блоков и пустые строки.
        $docAbove = $false
        for ($j = $i - 1; $j -ge 0 -and $j -ge ($i - 45); $j--) {
            $prev = $lines[$j]
            if ($prev -match '\*/') { $docAbove = $true; break }
            if ($prev -match '^\s*$') { continue }
            if ($prev -match '^\s*(@|//|\*|/\*)') { continue }
            if ($prev -match '^\s*(content|schema|value|required|example|description)\s*=') { continue }
            if ($prev -match '^\s*\)\s*[,;]?\s*$' -or $prev -match '^\s*\}\s*\)\s*;?\s*$') { continue }
            break
        }

        if ($line -match $typeRegex) {
            $types++
            if ($docAbove) { $hasTypeDoc = $true }
            continue
        }

        if ($line -match $methodRegex -and $line -notmatch $controlFlowRegex) {
            $methods++
            if ($docAbove) { $methodsDoc++ }
            continue
        }

        if ($line -match $fieldRegex -and $line -notmatch $typeRegex -and $line -notmatch $methodRegex) {
            $fields++
            if ($docAbove) { $fieldsDoc++ }
        }
    }

    $fileStats += [PSCustomObject]@{
        File        = $file.FullName.Substring((Resolve-Path $Root).Path.Length).TrimStart('\', '/')
        Package     = $file.DirectoryName.Substring((Resolve-Path $Root).Path.Length).TrimStart('\', '/').Replace('\', '.').Replace('/', '.')
        TypeDoc     = $hasTypeDoc
        Types       = $types
        Methods     = $methods
        MethodsDoc  = $methodsDoc
        Fields      = $fields
        FieldsDoc   = $fieldsDoc
        AnyDoc      = $hasFileDoc
    }
}

$types = ($fileStats | Measure-Object -Property Types -Sum).Sum
$typeDocs = ($fileStats | Where-Object TypeDoc | Measure-Object).Count
$methods = ($fileStats | Measure-Object -Property Methods -Sum).Sum
$methodsDoc = ($fileStats | Measure-Object -Property MethodsDoc -Sum).Sum
$fields = ($fileStats | Measure-Object -Property Fields -Sum).Sum
$fieldsDoc = ($fileStats | Measure-Object -Property FieldsDoc -Sum).Sum

if ($Details) {
    $fileStats |
        Sort-Object { ($_.Methods - $_.MethodsDoc) + $_.Fields - $_.FieldsDoc } -Descending |
        Format-Table File, TypeDoc, Methods, MethodsDoc, Fields, FieldsDoc -AutoSize |
        Out-String -Width 200 |
        Write-Host
}

function Format-Pct([int]$part, [int]$total) {
    if ($total -eq 0) { return "n/a" }
    return "{0,6:N1}%" -f (100.0 * $part / $total)
}

if ($ListMissingTypes) {
    $missing = $fileStats | Where-Object { -not $_.TypeDoc }
    Write-Host ""
    Write-Host ("Types without javadoc: {0}" -f $missing.Count)
    $missing | Sort-Object File | ForEach-Object { Write-Host ("  {0}" -f $_.File) }
}

if ($ByPackage) {
    $fileStats |
        Group-Object Package |
        ForEach-Object {
            $g = $_.Group
            [PSCustomObject]@{
                Package    = $_.Name
                Files      = $g.Count
                TypesDoc   = ($g | Where-Object TypeDoc | Measure-Object).Count
                Types      = ($g | Measure-Object -Property Types -Sum).Sum
                Methods    = ($g | Measure-Object -Property Methods -Sum).Sum
                MethodsDoc = ($g | Measure-Object -Property MethodsDoc -Sum).Sum
                Fields     = ($g | Measure-Object -Property Fields -Sum).Sum
                FieldsDoc  = ($g | Measure-Object -Property FieldsDoc -Sum).Sum
            }
        } |
        Sort-Object Package |
        Format-Table Package, Files, Types, TypesDoc, Methods, MethodsDoc, Fields, FieldsDoc -AutoSize |
        Out-String -Width 200 |
        Write-Host
}

Write-Host ""
Write-Host "Javadoc coverage: $Root"
Write-Host ("  files              : {0}" -f $fileStats.Count)
Write-Host ("  type declarations  : {0,4} ({1} documented)" -f $types, $typeDocs)
Write-Host ("  methods            : {0,4} ({1} documented) {2}" -f $methods, $methodsDoc, (Format-Pct $methodsDoc $methods))
Write-Host ("  fields             : {0,4} ({1} documented) {2}" -f $fields, $fieldsDoc, (Format-Pct $fieldsDoc $fields))
if ($methods -gt 0) {
    Write-Host ("  method coverage    : {0}" -f (Format-Pct $methodsDoc $methods))
}
