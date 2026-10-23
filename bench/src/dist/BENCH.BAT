@echo off
rem Full run: startup, offline crypto timings, then TLS tests using BENCH.CFG.
rem Run from this folder. Results go to BENCH.TXT.
echo Running pastebench, this can take a few minutes on old hardware...
java -Xmx32m -jar SMALL.JAR @BENCH.CFG > BENCH.TXT
echo Done. Results are in BENCH.TXT
