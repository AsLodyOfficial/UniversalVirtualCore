$adb = 'C:\Users\Ritu Chaudhary\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb -s 10BE3L0QMZ000DZ shell am force-stop com.zynoxadmin
& $adb -s 10BE3L0QMZ000DZ logcat -c
Write-Host "Starting com.zynoxadmin/.LogAct..."
& $adb -s 10BE3L0QMZ000DZ shell am start -n com.zynoxadmin/.LogAct
Start-Sleep -Seconds 5
Write-Host "--- LOGCAT CAPTURE (Alok/VirtualCore/Crash) ---"
& $adb -s 10BE3L0QMZ000DZ logcat -d -v time | Select-String -Pattern "com.zynoxadmin|Alok|VirtualCore|AndroidRuntime|DEBUG|fatal|FATAL|SIGSEGV|ANR in|libc"
Write-Host "--- PROCESS STATUS ---"
& $adb -s 10BE3L0QMZ000DZ shell ps -ef | Select-String -Pattern "com.zynoxadmin"
