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
set CL_OPTIONS=/c /Fo"%OBJ_DIR%\\" /EHsc /W3 /nologo /Zi /Od
set LINK_OPTIONS=/OUT:"%OUTPUT_EXE%" /DEBUG

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

:: Запуск з дампом пам'яті при краші
set DUMP_DIR=%PATH_D%\dumps
set DUMP_FILE=%DUMP_DIR%\server_relays_%date:~-4,4%%date:~-7,2%%date:~0,2%_%time:~0,2%%time:~3,2%.dmp

:: Створюємо директорію для дампів, якщо вона не існує
if not exist "%DUMP_DIR%" mkdir "%DUMP_DIR%"

:: Запуск з дампом пам'яті при краші
set DUMP_DIR=%PATH_D%\dumps
set DUMP_FILE=%DUMP_DIR%\server_relays_%date:~-4,4%%date:~-7,2%%date:~0,2%_%time:~0,2%%time:~3,2%.dmp

:: Створюємо директорію для дампів, якщо вона не існує
if not exist "%DUMP_DIR%" mkdir "%DUMP_DIR%"

:: Шляхи до дебагера
set DEBUGGER="C:\Program Files (x86)\Windows Kits\10\Debuggers\x64\cdb.exe"

:: Перевіряємо чи є cdb.exe
if not exist %DEBUGGER% (
    echo cdb.exe not found at %DEBUGGER%. Try running without a debugger.
    echo Running executable...
    @echo on
    cd %PATH_D%
    "%OUTPUT_EXE%"
    if errorlevel 1 (
        echo Error executing, exiting.
        exit /b 1
    )
) else (
    echo Running executable with debugger...
    cd %PATH_D%
    %DEBUGGER% -g -G -lines -srcpath %PATH_D% -y "SRV*c:\symbols*https://msdl.microsoft.com/download/symbols" "%OUTPUT_EXE%"
)

endlocal
