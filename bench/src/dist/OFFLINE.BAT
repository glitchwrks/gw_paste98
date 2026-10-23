@echo off
rem Offline only: startup, RNG seeding and crypto timings. No network needed.
rem Run from this folder. Results go to OFFLINE.TXT.
echo Running offline timings, this can take a minute on old hardware...
java -Xmx32m -jar SMALL.JAR --offline > OFFLINE.TXT
echo Done. Results are in OFFLINE.TXT
