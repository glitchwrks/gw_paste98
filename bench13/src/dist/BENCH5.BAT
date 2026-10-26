@echo off
rem The same run on the Java found on the PATH (normally Java 5), for comparison.
rem Results go to BENCH5.TXT.
echo Running pastebench13 on the default Java; this can take a few minutes...
java -Xmx32m -jar PB13.JAR @BENCH13.CFG > BENCH5.TXT
echo Done. Results are in BENCH5.TXT
