$ndkPath = 'C:\Users\Ritu Chaudhary\AppData\Local\Android\Sdk\ndk'
if (Test-Path $ndkPath) {
    Get-ChildItem -Recurse $ndkPath -Filter '*objdump*.exe' | Select-Object FullName
} else {
    Write-Host 'No NDK in standard path'
}
