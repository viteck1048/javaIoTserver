powershell -Command ^
" ^
if (!$a) { ^
	wt -w 0 new-tab --title 'Java Server' -- powershell -NoExit -File 'C:\Users\vitec\Desktop\start_java_server.ps1' ^
}; ^
if (!$r) { ^
	wt -w 0 new-tab --title 'C++ WinAPI Relays Server' cmd '/c C:\Users\vitec\Desktop\compil_and_run_server_relays.bat c:\gdrive\cpp\servak_na_winapi_relays\ ' ^
}; ^
if (!$b) { ^
	wsl --setdefault Ubuntu; ^
	wt -w 0 new-tab --title 'C++ WSL LiraCalc Server' bash -i -c "'sleep 3 && /mnt/c/Users/vitec/Desktop/start_wsl_server.sh' ^"; ^
	Start-Sleep -Seconds 15; ^
	wsl --setdefault Debian ^
}"
