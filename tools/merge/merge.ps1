# 把 主模组 + 资源包 + 附属包 合并成一个完整的模组 jar（保留主模组的 META-INF，不会被覆盖）
# 用法：把 jujutsuenhancement-*.jar 全部放在本脚本同一个文件夹，双击 merge.bat
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jars = Get-ChildItem $dir -Filter 'jujutsuenhancement-*.jar' | Where-Object { $_.Name -notmatch 'merged' }
$main = $jars | Where-Object { $_.Name -notmatch '-(assets\d+|skin)\.jar$' } | Select-Object -First 1
if (-not $main) { Write-Host '找不到主模组 jar（jujutsuenhancement-0.1.0.jar）'; exit 1 }
$others = $jars | Where-Object { $_.FullName -ne $main.FullName }
$out = Join-Path $dir 'jujutsuenhancement-merged.jar'
Copy-Item $main.FullName $out -Force
$zip = [System.IO.Compression.ZipFile]::Open($out, 'Update')
$have = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($e in $zip.Entries) { [void]$have.Add($e.FullName) }
foreach ($j in $others) {
    Write-Host ('合并 ' + $j.Name)
    $src = [System.IO.Compression.ZipFile]::OpenRead($j.FullName)
    foreach ($e in $src.Entries) {
        if ($e.FullName.EndsWith('/')) { continue }
        if ($e.FullName.StartsWith('META-INF/') -or $e.FullName -eq 'pack.mcmeta') { continue }
        if ($have.Contains($e.FullName)) { continue }
        $n = $zip.CreateEntry($e.FullName, 'Optimal')
        $in = $e.Open(); $os = $n.Open(); $in.CopyTo($os); $os.Dispose(); $in.Dispose()
        [void]$have.Add($e.FullName)
    }
    $src.Dispose()
}
$zip.Dispose()
Write-Host ('完成：' + $out + '  （放进 mods 文件夹，并删除其它 jujutsuenhancement*.jar）')
