@echo off
setlocal
set "ROOT=%~dp0"

echo ============================================================
echo   Build needs JDK 17 + Android SDK 35 + Gradle 8.9.
echo   Easiest path: open this folder with Android Studio and
echo   run  Build ^>  Build Bundle(s) / APK(s)  ^>  Build APK(s).
echo   Or set JAVA_HOME / ANDROID_HOME and run gradlew assembleDebug.
echo ============================================================
echo.

if "%JAVA_HOME%"=="" (
  echo [!] JAVA_HOME is not set.
  pause
  exit /b 1
)

call "%ROOT%gradlew.bat" -p "%ROOT%" assembleDebug --no-daemon
if errorlevel 1 (
  echo BUILD FAILED - see log above
  pause
  exit /b 1
)
echo.
echo === OK ===
echo APK: %ROOT%app\build\outputs\apk\debug\app-debug.apk
pause
