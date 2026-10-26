$adb = 'C:\Users\Ritu Chaudhary\AppData\Local\Android\Sdk\platform-tools\adb.exe'
Write-Host "=== 1. Checking Device & Package ==="
& $adb devices
& $adb -s 10BE3L0QMZ000DZ shell dumpsys package com.zynoxadmin | Select-String -Pattern "lastUpdateTime|versionName"

Write-Host "=== 2. Launching com.zynoxadmin/.LogAct ==="
& $adb -s 10BE3L0QMZ000DZ shell am force-stop com.zynoxadmin
& $adb -s 10BE3L0QMZ000DZ logcat -c
& $adb -s 10BE3L0QMZ000DZ shell am start -n com.zynoxadmin/.LogAct
Start-Sleep -Seconds 3

Write-Host "=== 3. Checking Process & Dumping UI ==="
& $adb -s 10BE3L0QMZ000DZ shell ps -ef | Select-String -Pattern "com.zynoxadmin"
& $adb -s 10BE3L0QMZ000DZ shell uiautomator dump /sdcard/window_dump.xml
& $adb -s 10BE3L0QMZ000DZ shell cat /sdcard/window_dump.xml

Write-Host "=== 4. Initial Logcat ==="
& $adb -s 10BE3L0QMZ000DZ logcat -d -v time | Select-String -Pattern "com.zynoxadmin|Alok|VirtualCore|HiddenApiCompat|AndroidRuntime|FATAL|SIGSEGV"
