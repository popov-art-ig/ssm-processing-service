<#
.SYNOPSIS
    Checks that every {@link ...} tag in the sources points to a real target.

.DESCRIPTION
    Javadoc {@link} tags are not validated by the compiler: a reference to a missing type or
    member silently becomes plain text, so dead links live in comments for years. This script
    indexes the types and members declared under src/main/java and validates:

      * {@link Type}              - the type is declared in the project or imported by the file;
      * {@link Type#member}       - the type is known and declares such a field/method;
      * {@link #member}           - the member is declared in the same file;
      * {@link Type#member(args)} - only the member name is checked (signatures are ignored).

    Targets outside the project (JDK, Spring, Lombok) are not validated: a short name that does
    not resolve in the project is accepted when it appears in the import block of the file.
    Lombok accessors, enum constants, record components and Spring Data repository methods are
    part of the index, because references to them are legitimate.

    NOTE: this file is intentionally ASCII-only and is stored with a UTF-8 BOM. Windows
    PowerShell 5.1 decodes BOM-less files as ANSI, which mangles non-ASCII characters inside
    string literals and turns the script into a syntax error.

.PARAMETER Root
    Search root. Defaults to src/main/java.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File doc/tools/VerifyJavadocLinks.ps1
#>
[CmdletBinding()]
param(
    [string]$Root = "src/main/java"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $Root)) { throw "Path not found: $Root" }

$utf8 = New-Object System.Text.UTF8Encoding($false)

# Index: simple type name -> set of fully qualified names; fully qualified name -> set of members.
$typesByName = @{}
$membersByType = @{}
$superTypes = @{}
$fileInfo = @{}

$files = Get-ChildItem -Recurse -File -Filter *.java $Root
foreach ($f in $files) {
    $text = [System.IO.File]::ReadAllText($f.FullName, $utf8)
    $pkg = ""
    if ($text -match '(?m)^\s*package\s+([\w\.]+)\s*;') { $pkg = $Matches[1] }

    $imports = @()
    foreach ($m in [regex]::Matches($text, '(?m)^\s*import\s+(?:static\s+)?([\w\.\*]+)\s*;')) {
        $imports += $m.Groups[1].Value
    }

    $simpleNames = @()
    foreach ($m in [regex]::Matches($text, '(?m)^\s*(?:public\s+|final\s+|abstract\s+|sealed\s+|non-sealed\s+)*(?:class|interface|enum|record|@interface)\s+(\w+)')) {
        $simpleNames += $m.Groups[1].Value
    }

    $members = New-Object System.Collections.Generic.HashSet[string]
    $memberLines = $text -split "\r?\n"

    # Declaration may span lines ("private final Map<String, List<String>>\n loadGuards(...)" and
    # "@Override\npublic boolean evaluate(...)"), so the signature is searched in a sliding window
    # of code lines: separators and blanks are skipped if the declaration continues on the next
    # line. A line ending with ';' or '}' closes the window.
    for ($i = 0; $i -lt $memberLines.Count; $i++) {
        $startLine = $memberLines[$i]
        if ($startLine -match '^\s*(?:\*|//|/\*|@|$)') { continue }
        $window = $startLine
        $j = $i
        while ($j + 1 -lt $memberLines.Count -and $window -notmatch '\)\s*(?:\{|throws|;)\s*$' -and $window -notmatch ';\s*$' -and $window -notmatch '\{\s*$') {
            $j++
            $next = $memberLines[$j]
            if ($next -match '^\s*(?:\*|//|/\*|@)') { continue }
            $window = ($window.TrimEnd() + ' ' + $next.Trim())
            if ($next -match ';\s*$') { break }
        }

        # Method or constructor.
        if ($window -match '(?:(?:public|protected|private|static|final|synchronized|abstract|default|native|strictfp)\s+)*(?:<[^>]+>\s+)?[\w<>,\[\]\.\?]+\s+(\w+)\s*\(') {
            [void]$members.Add($Matches[1])
        }
        # Field.
        if ($window -match '^\s*(?:(?:public|protected|private|static|final|transient|volatile)\s+)+[\w<>,\[\]\.\?]+\s+(\w+)\s*(?:=|;)') {
            [void]$members.Add($Matches[1])
        }

        # Enum constants: a line inside an enum body that starts with an uppercase identifier,
        # optionally followed by arguments or a comma. Brace depth is counted from the start of
        # the file, so only real body lines qualify. The captured name is stored before any
        # further -match, because each successful -match overwrites $Matches.
        $constantName = $null
        if ($startLine -match '^\s*([A-Z][A-Z0-9_]*)\s*(?:,|;|\()?\s*$' -or $startLine -match '^\s*([A-Z][A-Z0-9_]*)\s*\(') {
            $constantName = $Matches[1]
        }
        if ($constantName) {
            $before = ($memberLines[0..($i - 1)] -join "`n")
            $opens = ([regex]::Matches($before, '\{')).Count
            $closes = ([regex]::Matches($before, '\}')).Count
            if ($opens -gt $closes -and $before -match '(?ms)^\s*(?:public\s+)?enum\s+\w+') {
                [void]$members.Add($constantName)
            }
        }
    }

    # Record components.
    foreach ($m in [regex]::Matches($text, '(?ms)^\s*(?:public\s+)?record\s+\w+\s*\(([^)]*)\)')) {
        foreach ($p in ($m.Groups[1].Value -split ',')) {
            $name = ($p -replace '^\s*[\w<>,\[\]\.\?]+\s+', '').Trim()
            if ($name -match '^\w+$') { [void]$members.Add($name) }
        }
    }
    # Lombok-generated accessors for declared fields.
    if ($text -match '(?m)^\s*@(Getter|Setter|Data|Value|Builder|SuperBuilder|With)\b') {
        foreach ($m in [regex]::Matches($text, '(?m)^\s*(?:private|protected|public)\s+(?!static)([\w<>,\[\]\.\?]+)\s+(\w+)\s*(?:=|;)')) {
            $fieldName = $m.Groups[2].Value
            $capitalized = $fieldName.Substring(0, 1).ToUpper() + $fieldName.Substring(1)
            [void]$members.Add($fieldName)
            [void]$members.Add("get$capitalized")
            [void]$members.Add("set$capitalized")
            [void]$members.Add("is$capitalized")
        }
    }
    # Spring Data repository methods are inherited, not declared.
    if ($text -match 'extends\s+[\w,\s]*(JpaRepository|CrudRepository|PagingAndSortingRepository|JpaSpecificationExecutor|Repository)') {
        foreach ($n in @('save', 'saveAll', 'saveAndFlush', 'findById', 'findAll', 'existsById', 'count', 'delete', 'deleteById', 'deleteAll', 'flush', 'getById', 'getReferenceById')) {
            [void]$members.Add($n)
        }
    }

    foreach ($simple in $simpleNames) {
        $full = if ($pkg) { "$pkg.$simple" } else { $simple }
        if (-not $typesByName.ContainsKey($simple)) { $typesByName[$simple] = New-Object System.Collections.Generic.HashSet[string] }
        [void]$typesByName[$simple].Add($full)
        $membersByType[$full] = $members

        # Direct supertypes: references to inherited members (getId() from BaseEntity, the CRUD
        # methods of a repository) must resolve, so the parent member sets are merged into the
        # child below, after the whole index is built.
        $header = [regex]::Match($text, '(?ms)^\s*(?:public\s+|final\s+|abstract\s+|sealed\s+|non-sealed\s+)*(?:class|interface|enum|record)\s+' + [regex]::Escape($simple) + '\b[^{]*')
        if ($header.Success -and $header.Value -match '\b(?:extends|implements)\s+([^{]+)') {
            $parents = [regex]::Matches($header.Value, '\b(?:extends|implements)\s+([^{]+)')
            foreach ($p in $parents) {
                foreach ($candidate in ($p.Groups[1].Value -split ',')) {
                    $parentSimple = (($candidate -split '<')[0].Trim() -split '\.')[-1]
                    if ($parentSimple -match '^\w+$') {
                        if (-not $superTypes.ContainsKey($full)) { $superTypes[$full] = New-Object System.Collections.Generic.HashSet[string] }
                        [void]$superTypes[$full].Add($parentSimple)
                    }
                }
            }
        }
    }

    $fileInfo[$f.FullName] = [PSCustomObject]@{
        Package     = $pkg
        Imports     = $imports
        SimpleTypes = $simpleNames
        LocalNames  = @($simpleNames) + @($members)
    }
}

# Merge inherited members (one level of the project hierarchy is enough here: BaseEntity and the
# Spring Data interfaces; deeper chains are resolved by repeating the pass).
for ($pass = 0; $pass -lt 3; $pass++) {
    foreach ($child in $superTypes.Keys) {
        foreach ($parentSimple in $superTypes[$child]) {
            if ($typesByName.ContainsKey($parentSimple)) {
                foreach ($parentFull in $typesByName[$parentSimple]) {
                    if ($membersByType.ContainsKey($parentFull)) {
                        foreach ($member in $membersByType[$parentFull]) { [void]$membersByType[$child].Add($member) }
                    }
                }
            }
        }
    }
}

$problems = @()
$checked = 0
$rootPath = (Resolve-Path $Root).Path

foreach ($f in $files) {
    $text = [System.IO.File]::ReadAllText($f.FullName, $utf8)
    $info = $fileInfo[$f.FullName]
    $rel = $f.FullName.Replace($rootPath + '\', '')

    foreach ($m in [regex]::Matches($text, '\{@link(?:plain)?\s+([^}]+?)\s*\}')) {
        $target = $m.Groups[1].Value.Trim()
        # Drop the optional link label ("Type#member some text") - keep the target only.
        if ($target -match '\s') { $target = ($target -split '\s+')[0] }
        $checked++

        $typePart = $null
        $memberPart = $null
        if ($target.StartsWith('#')) {
            $memberPart = $target.Substring(1)
        } elseif ($target -match '#') {
            $parts = $target -split '#', 2
            $typePart = $parts[0]
            $memberPart = $parts[1]
        } else {
            $typePart = $target
        }

        if ($memberPart) { $memberPart = ($memberPart -split '\(')[0].Trim() }

        # 1) Member of the same file.
        if (-not $typePart) {
            if ($memberPart -and ($info.LocalNames -notcontains $memberPart)) {
                $problems += [PSCustomObject]@{ File = $rel; Target = $target; Reason = "no member '$memberPart' in this file" }
            }
            continue
        }

        # 2) Type declared in the project.
        $simpleType = ($typePart -split '\.')[-1]
        $resolved = $false
        if ($typesByName.ContainsKey($simpleType)) {
            $resolved = $true
            if ($memberPart) {
                $found = $false
                foreach ($full in $typesByName[$simpleType]) {
                    if ($membersByType[$full] -and $membersByType[$full].Contains($memberPart)) { $found = $true; break }
                }
                if (-not $found) {
                    $problems += [PSCustomObject]@{ File = $rel; Target = $target; Reason = "type '$simpleType' has no member '$memberPart'" }
                }
            }
        }

        # 3) External type: accepted when the file imports it, when it is written by a fully
        # qualified name (explicit enough to trust: types outside the project are not indexed),
        # or when the reference has no package part at all (same package or unindexed JDK type).
        if (-not $resolved) {
            $isImported = $false
            if ($typePart -notmatch '\.') { $isImported = $true }
            if ($typePart -match '\.' -and ([regex]::Matches($typePart, '\.')).Count -ge 2) { $isImported = $true }
            foreach ($imp in $info.Imports) {
                $impSimple = ($imp -split '\.')[-1]
                if ($impSimple -eq $simpleType) { $isImported = $true; break }
                if ($imp.EndsWith('*') -and $typePart -match '\.') { $isImported = $true; break }
            }
            if (-not $isImported) {
                $problems += [PSCustomObject]@{ File = $rel; Target = $target; Reason = "type '$simpleType' is neither declared in the project nor imported" }
            }
        }
    }
}

Write-Host ""
Write-Host ("Javadoc @link check: {0} links in {1} files" -f $checked, $files.Count)
Write-Host ("Unresolved            : {0}" -f $problems.Count)

if ($problems.Count -gt 0) {
    Write-Host ""
    $problems | Sort-Object File | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
    exit 2
}

exit 0
