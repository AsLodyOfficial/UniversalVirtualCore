Add-Type -AssemblyName System.IO.Compression.FileSystem

$zip = [System.IO.Compression.ZipFile]::OpenRead('C:\Users\Ritu Chaudhary\Downloads\ZUZMOD.apk')
$entry = $zip.GetEntry('lib/arm64-v8a/libzynoxadmin.so')
$dst = "$env:TEMP\libzynoxadmin.so"
[System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dst, $true)
$zip.Dispose()

$readelf = 'C:\Users\Ritu Chaudhary\AppData\Local\Android\Sdk\ndk\24.0.8215888\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe'
& $readelf -d $dst
& $readelf -s $dst | Select-String -Pattern 'JNI_OnLoad|Java_'
