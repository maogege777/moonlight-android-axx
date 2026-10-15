# EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [ADDED] BEGIN
# Lightweight Java brace/paren balance check for machines without a JDK or Android SDK.
# It strips comments and string/char literals first, then counts (), {} and [] balance
# and the minimum nesting depth. It can only catch structural mistakes (missing or extra
# brackets); it is NOT a replacement for javac type checking.
#
# NOTE 1: this script's own comments are ASCII on purpose. PowerShell 5.1 loads a BOM-less
#         .ps1 using the system ANSI code page (GBK on Chinese Windows), which can swallow
#         the following byte after a UTF-8 multi-byte character and break the parser.
# NOTE 2: Java sources are read as UTF-8 explicitly for the same reason - Get-Content would
#         otherwise mis-decode Chinese string literals and report fake bracket mismatches.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File dev\check_java_balance.ps1 <file1.java> <file2.java>
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Files)

$ErrorActionPreference = 'Stop'
$bad = 0

function Strip-NonCode([string]$t) {
    $sb = New-Object System.Text.StringBuilder
    $i = 0; $n = $t.Length
    while ($i -lt $n) {
        $c = $t[$i]
        if ($c -eq '/' -and $i + 1 -lt $n -and $t[$i + 1] -eq '/') {
            while ($i -lt $n -and $t[$i] -ne "`n") { $i++ }
            continue
        }
        if ($c -eq '/' -and $i + 1 -lt $n -and $t[$i + 1] -eq '*') {
            $j = $t.IndexOf('*/', $i + 2)
            if ($j -lt 0) { break }
            $i = $j + 2
            continue
        }
        if ($c -eq '"' -or $c -eq "'") {
            $q = $c
            $i++
            while ($i -lt $n) {
                if ($t[$i] -eq '\') { $i += 2; continue }
                if ($t[$i] -eq $q) { $i++; break }
                $i++
            }
            continue
        }
        [void]$sb.Append($c)
        $i++
    }
    return $sb.ToString()
}

function Read-Utf8([string]$path) {
    return [System.IO.File]::ReadAllText($path, [System.Text.UTF8Encoding]::new($false))
}

foreach ($file in $Files) {
    if (-not (Test-Path $file)) { Write-Output ("MISS  " + $file); $bad++; continue }
    $s = Strip-NonCode (Read-Utf8 $file)

    $paren = 0; $brace = 0; $bracket = 0
    $depth = 0; $minDepth = 0
    foreach ($ch in $s.ToCharArray()) {
        if ($ch -eq '(') { $paren++; $depth++ }
        elseif ($ch -eq '{') { $brace++; $depth++ }
        elseif ($ch -eq '[') { $bracket++; $depth++ }
        elseif ($ch -eq ')') { $paren--; $depth-- }
        elseif ($ch -eq '}') { $brace--; $depth-- }
        elseif ($ch -eq ']') { $bracket--; $depth-- }
        if ($depth -lt $minDepth) { $minDepth = $depth }
    }

    $problems = @()
    if ($paren -ne 0) { $problems += ("paren=" + $paren) }
    if ($brace -ne 0) { $problems += ("brace=" + $brace) }
    if ($bracket -ne 0) { $problems += ("bracket=" + $bracket) }
    if ($depth -ne 0) { $problems += ("netDepth=" + $depth) }
    if ($minDepth -lt 0) { $problems += ("extraCloser(minDepth=" + $minDepth + ")") }

    if ($problems.Count -eq 0) {
        Write-Output ("OK    " + (Split-Path $file -Leaf))
    } else {
        $bad++
        Write-Output ("FAIL  " + (Split-Path $file -Leaf) + "  " + ($problems -join ' '))
    }
}
Write-Output ("checked=" + $Files.Count + " invalid=" + $bad)
# EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [ADDED] END
