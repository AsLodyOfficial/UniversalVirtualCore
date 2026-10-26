$objdump = 'C:\Users\Ritu Chaudhary\AppData\Local\Android\Sdk\ndk\26.1.10909125\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-objdump.exe'
$so = 'C:\Users\Ritu Chaudhary\.gemini\antigravity\scratch\UniversalVirtualCore\core\src\main\jniLibs\arm64-v8a\libalok.so'
& $objdump -d $so | Select-String -Pattern "3c8|3c0" -Context 3, 5 | Select-Object -First 30
