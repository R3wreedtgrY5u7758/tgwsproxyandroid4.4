@echo off
setlocal
REM Сборка libtgwsproxy.so для Android 4.4 (API 19, armeabi-v7a) под Windows.
REM Нужно: Rust (rustup) + Android NDK r25c (в r26+ поддержка API 19 удалена).
REM
REM   set ANDROID_NDK_HOME=C:\Android\android-ndk-r25c
REM   build_so_api19.bat

if not defined ANDROID_NDK_HOME (
    echo ERROR: задайте ANDROID_NDK_HOME - путь к NDK r25c
    exit /b 1
)

set "NDK_BIN=%ANDROID_NDK_HOME%\toolchains\llvm\prebuilt\windows-x86_64\bin"
set "CLANG=%NDK_BIN%\armv7a-linux-androideabi19-clang.cmd"

if not exist "%CLANG%" (
    echo ERROR: не найден %CLANG%
    echo        Нужен именно NDK r25c - в r26 и новее API 19 не поддерживается.
    exit /b 1
)

where cargo >nul 2>nul
if errorlevel 1 (
    echo ERROR: cargo не найден. Установите Rust с https://rustup.rs
    exit /b 1
)

cd /d "%~dp0"

rustup target add armv7-linux-androideabi
if errorlevel 1 exit /b 1

set "CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER=%CLANG%"
set "CC_armv7_linux_androideabi=%CLANG%"
set "AR_armv7_linux_androideabi=%NDK_BIN%\llvm-ar.exe"

echo === cargo build (armv7-linux-androideabi, API 19) ===
cargo build --release --target armv7-linux-androideabi
if errorlevel 1 (
    echo BUILD FAILED
    exit /b 1
)

if not exist "app\src\main\jniLibs\armeabi-v7a" mkdir "app\src\main\jniLibs\armeabi-v7a"
copy /y "target\armv7-linux-androideabi\release\libtgwsproxy.so" "app\src\main\jniLibs\armeabi-v7a\libtgwsproxy.so" >nul
if errorlevel 1 (
    echo COPY FAILED
    exit /b 1
)

echo.
echo OK: app\src\main\jniLibs\armeabi-v7a\libtgwsproxy.so
echo.
echo ВАЖНО: проверьте совместимость символов с API 19 (нужен bash/WSL):
echo   ./check_so_api19.sh app/src/main/jniLibs/armeabi-v7a/libtgwsproxy.so
echo.
echo Дальше: откройте проект в Android Studio и соберите APK.
exit /b 0
