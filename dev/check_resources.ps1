$ErrorActionPreference = 'Continue'
$dir = Join-Path $PSScriptRoot "..\app\src\main\res\drawable"
$bad = 0
foreach ($file in Get-ChildItem (Join-Path $dir "ic_axi_qk_*.xml")) {
    try {
        $doc = New-Object System.Xml.XmlDocument
        $doc.Load($file.FullName)
        $paths = $doc.SelectNodes("//path")
        $root = $doc.DocumentElement
        if ($root.Name -ne "vector") { throw "root is $root" }
        Write-Output ("OK   " + $file.Name + "  paths=" + $paths.Count)
    } catch {
        $bad++
        Write-Output ("FAIL " + $file.Name + "  " + $_.Exception.Message)
    }
}
Write-Output ("checked=" + (Get-ChildItem (Join-Path $dir "ic_axi_qk_*.xml")).Count + " invalid=" + $bad)

# Layout XML sanity check: every file touched by this change must parse.
foreach ($rel in @(
    "..\app\src\main\res\layout\dialog_game_menu.xml",
    "..\app\src\main\res\layout\dialog_game_menu_function.xml",
    "..\app\src\main\res\layout\dialog_game_menu_keyboard_group_add.xml",
    "..\app\src\main\res\layout\item_game_menu_quick_key.xml",
    "..\app\src\main\res\xml\preferences.xml",
    # v4: shared opaque dialog panel (8 dialogs share it)
    "..\app\src\main\res\drawable\bg_update_dialog_panel.xml"
)) {
    $path = Join-Path $PSScriptRoot $rel
    try {
        $doc = New-Object System.Xml.XmlDocument
        $doc.Load($path)
        Write-Output ("OK   " + (Split-Path $path -Leaf))
    } catch {
        $bad++
        Write-Output ("FAIL " + (Split-Path $path -Leaf) + "  " + $_.Exception.Message)
    }
}
Write-Output ("total invalid=" + $bad)
