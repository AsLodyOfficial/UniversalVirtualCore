$adb = 'C:\Users\Ritu Chaudhary\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb -s 10BE3L0QMZ000DZ logcat -c
Write-Host "=== Tapping START button (284, 1246) ==="
& $adb -s 10BE3L0QMZ000DZ shell input tap 284 1246
Start-Sleep -Seconds 3

Write-Host "=== Dumping UI after tapping START ==="
& $adb -s 10BE3L0QMZ000DZ shell uiautomator dump /sdcard/window_dump3.xml
& $adb -s 10BE3L0QMZ000DZ shell cat /sdcard/window_dump3.xml

Write-Host "=== Checking Processes ==="
& $adb -s 10BE3L0QMZ000DZ shell ps -ef | Select-String -Pattern "com.zynoxadmin|pubg"

Write-Host "=== Capturing Logcat ==="
& $adb -s 10BE3L0QMZ000DZ logcat -d -v time | Select-String -Pattern "com.zynoxadmin|Alok|VirtualCore|VAppManager|BActivityManager|BPackageManager|AndroidRuntime|FATAL|SIGSEGV|pubg"
