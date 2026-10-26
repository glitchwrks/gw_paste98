@echo off
rem Sends a text file as a paste and prints its address. Run from this folder.
rem Usage: SENDFILE filename
rem The password comes from the PASTE98_PASSWORD environment variable (passed
rem to Java as a property, since Java 1.3 cannot read the environment), and the
rem username from the one remembered by the tray app (or add --user NAME).
if "%1"=="" goto usage
java -Dpaste98.password=%PASTE98_PASSWORD% -Xmx32m -jar GWPASTE.JAR --send %1 %2 %3
goto end
:usage
echo Usage: SENDFILE filename
:end
