$adb = 'C:\Users\Ritu Chaudhary\AppData\Local\Android\Sdk\platform-tools\adb.exe'
Write-Host "=== Tapping OK button (540, 1415) ==="
& $adb -s 10BE3L0QMZ000DZ shell input tap 540 1415
Start-Sleep -Seconds 1
& $adb -s 10BE3L0QMZ000DZ shell uiautomator dump /sdcard/window_dump2.xml
& $adb -s 10BE3L0QMZ000DZ shell cat /sdcard/window_dump2.xml
