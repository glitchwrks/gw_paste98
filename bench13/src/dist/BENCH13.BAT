@echo off
rem Full run on Java 1.3: startup, offline crypto, then TLS tests from BENCH13.CFG.
rem Run from this folder. Results go to BENCH13.TXT.
call SETJAVA.BAT
echo Running pastebench13 on Java 1.3; this can take a few minutes...
%JAVA13% -Xmx32m -jar PB13.JAR @BENCH13.CFG > BENCH13.TXT
echo Done. Results are in BENCH13.TXT
