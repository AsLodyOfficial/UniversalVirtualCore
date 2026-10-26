$adb = 'C:\Users\Ritu Chaudhary\AppData\Local\Android\Sdk\platform-tools\adb.exe'
Write-Host "=== 1. Recent Logcat from User's Launch ==="
& $adb -s 10BE3L0QMZ000DZ logcat -d -v time | Select-String -Pattern "com.zynoxadmin|Alok|VirtualCore|VAppManager|BActivityManager|BPackageManager|BActivityThread|JarManager|AndroidRuntime|FATAL|SIGSEGV|pubg|ProxyActivity" | Select-Object -Last 80

Write-Host "=== 2. Current Foreground Activity & UI ==="
& $adb -s 10BE3L0QMZ000DZ shell dumpsys window | Select-String -Pattern "mCurrentFocus|mFocusedApp"
& $adb -s 10BE3L0QMZ000DZ shell ps -ef | Select-String -Pattern "com.zynoxadmin|pubg"
