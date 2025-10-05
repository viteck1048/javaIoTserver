echo "1. reestr"
set "BODY=reestr=false&login=<login>&password=<password>"
set "COOKIE="

powershell -Command ^
"$response = Invoke-WebRequest -Uri 'https://localhost:443' -Method 'POST' -Body '%BODY%' -SkipCertificateCheck; ^
if ($response.Headers['Set-Cookie']) { ^
    $cookie = $response.Headers['Set-Cookie']; ^
    Write-Host "Cookie: $cookie"; ^
}"

set "COOKIE=%ERRORLEVEL%"

echo "2. https://***/?command=exit old_servak"
powershell -Command ^
"Invoke-WebRequest -Uri 'https://localhost/old_servak?command=exit' -Headers @{Cookie='%COOKIE%'}"

echo "3. https://***/?command=exit relay_servak"
powershell -Command ^
"Invoke-WebRequest -Uri 'https://localhost/relay_servak?command=exit' -Headers @{Cookie='%COOKIE%'}"

echo "4. java-server process kill"
powershell -Command ^
" "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*Servak*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"^"

echo "5. command=exit old_servak"
for /f "usebackq delims=" %%A in (`powershell -Command "(wsl hostname -I).Split(' ')[0]"`) do set ip_wsl=%%A

powershell -Command ^
"Invoke-WebRequest -Uri 'http://%ip_wsl%:8080/?command=exit' "

echo "6. command=exit relay_servak"
powershell -Command ^
"Invoke-WebRequest -Uri 'http://localhost:8081/?command=exit' "

::echo "4"
::powershell -Command ^
::" "Get-Process | Where-Object { $_.MainWindowTitle -eq 'Java Server' } | ForEach-Object { Stop-Process -Id $_.Id -Force }"^"

::"reestr=false&login=bhygftdbtsa!()6&password=nyYbyby7&54Hgrf"
