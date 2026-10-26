$so = 'C:\Users\Ritu Chaudhary\.gemini\antigravity\scratch\UniversalVirtualCore\core\src\main\jniLibs\arm64-v8a\libalok.so'
$bytes = [System.IO.File]::ReadAllBytes($so)
$str = [System.Text.Encoding]::ASCII.GetString($bytes)
$regex = [System.Text.RegularExpressions.Regex]::new('[a-zA-Z0-9_\-\./]{4,}')
$matches = $regex.Matches($str)
$list = [System.Collections.Generic.List[string]]::new()
foreach ($m in $matches) {
    $v = $m.Value
    if ($v.Length -ge 5 -and ($v -match 'hook|open|stat|access|alok|path|virtual|io|redirect')) {
        $list.Add($v)
    }
}
$list | Select-Object -Unique | Select-Object -First 80
