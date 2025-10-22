cd "C:\gdrive\cpp\servak_na_linux_2_java"
javac -classpath "lib/*" -sourcepath ".;source/" Servak.java
$ip_wsl = (wsl hostname -I).Split(" ")[0]
java -cp ".;source;lib/*" Servak -p ip_liraCalc_server=$ip_wsl
