@echo off
setlocal
set "ROOT=%~dp0"
set "JDK=%JAVA_HOME%"
if "%JDK%"=="" (
  echo [!] JAVA_HOME is not set. Install JDK 17 and point JAVA_HOME to it.
  pause
  exit /b 1
)
set "OUT=%ROOT%tools\jvmtest-out"
if not exist "%OUT%" mkdir "%OUT%"

echo === Compiling PostureDetector self test (pure JVM, no Android SDK) ===
"%JDK%\bin\javac.exe" -encoding UTF-8 -d "%OUT%" "%ROOT%app\src\main\java\com\example\posture\PostureDetector.java" "%ROOT%jvmtest\PostureDetectorSelfTest.java"
if errorlevel 1 (
  echo COMPILE FAILED
  pause
  exit /b 1
)

echo === Running 17 tests ===
"%JDK%\bin\java.exe" -Dfile.encoding=UTF-8 -cp "%OUT%" com.example.posture.PostureDetectorSelfTest
pause
