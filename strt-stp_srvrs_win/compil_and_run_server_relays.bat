@echo off
setlocal

:: Налаштування директорій
set PATH_D=%1
if "%PATH_D%"=="" set PATH_D=%cd%
else cd %PATH_D%

set SOURCE_DIR=%PATH_D%\source
set OBJ_DIR=%PATH_D%\source\obj
set OUTPUT_EXE=%PATH_D%\server_relays.exe

:: Шляхи компілятора та бібліотек
set PATH=c:\Program Files (x86)\Microsoft Visual Studio 12.0\VC\bin\;%PATH%
set INCLUDE=C:\Program Files (x86)\Microsoft Visual Studio 12.0\VC\include\;C:\Program Files (x86)\Microsoft SDKs\Windows\v7.1A\Include\;
set LIB=%PATH_D%\source\lib;C:\Program Files (x86)\Microsoft Visual Studio 12.0\VC\lib\;C:\Program Files (x86)\Microsoft SDKs\Windows\v7.1A\Lib\;

:: Параметри компілятора та лінкера
set CL_OPTIONS=/c /Fo"%OBJ_DIR%\\" /EHsc /W3 /O2 /nologo
set LINK_OPTIONS=/OUT:"%OUTPUT_EXE%"

:: Створити папку для обʼєктників
if not exist "%OBJ_DIR%" mkdir "%OBJ_DIR%"
echo.
echo %PATH_D%
echo.  
:: Компіляція всіх .c і .cpp файлів
for %%f in ("%SOURCE_DIR%\*.c" "%SOURCE_DIR%\*.cpp") do (
    ::echo Compiling %%f...
    cl.exe %%f %CL_OPTIONS%
    if errorlevel 1 (
        echo Error compiling %%f, exiting.
        exit /b 1
    )
)

:: Перевірка чи запущений server_relays.exe — і вбити його
tasklist /FI "IMAGENAME eq server_relays.exe" | find /I "server_relays.exe" >nul
if not errorlevel 1 (
    echo Killing existing server_relays.exe...
    taskkill /F /IM server_relays.exe >nul
)

:: Лінкування
echo.
echo Linking...
link.exe "%OBJ_DIR%\*.obj" %LINK_OPTIONS%
if errorlevel 1 (
    echo ERROR LINKING
    echo RUN OLD VERSION
)

:: Прибрати обʼєктники (якщо не треба)
:: del /q "%OBJ_DIR%\*.obj"

:: Запуск
echo Running executable...
cd %PATH_D%
"%OUTPUT_EXE%"
if errorlevel 1 (
    echo Error executing, exiting.
    exit /b 1
)

endlocal
