@echo off
rem As BENCH13.BAT, but seeding SecureRandom from timing jitter instead of
rem Java 1.3's own (slow) seeding. Results go to FAST13.TXT.
call SETJAVA.BAT
echo Running pastebench13 on Java 1.3 with --fastseed...
%JAVA13% -Xmx32m -jar PB13.JAR --fastseed @BENCH13.CFG > FAST13.TXT
echo Done. Results are in FAST13.TXT
