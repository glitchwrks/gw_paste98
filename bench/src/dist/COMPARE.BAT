@echo off
rem Cold-start comparison for the first handshake after launch.
rem Uses BENCH.CFG for host and pin. Run from this folder. Results: COMPARE.TXT
rem For the cleanest cold-start numbers, reboot before running this.
echo Running comparison, this takes a few minutes...
java -version
echo pastebench comparison > COMPARE.TXT
echo. >> COMPARE.TXT
echo ##### 0: RSA key validation cost on its own (offline timings) >> COMPARE.TXT
java -Xmx32m -jar SMALL.JAR --offline --budget-ms 500 >> COMPARE.TXT
echo ##### 1: SMALL.JAR, BC validates the RSA key itself, with profile >> COMPARE.TXT
java -Xmx32m -jar SMALL.JAR @BENCH.CFG --skip-crypto --resume 0 --no-prevalidate --profile 20 >> COMPARE.TXT
echo ##### 2: SMALL.JAR, pinned key pre-validated, with profile >> COMPARE.TXT
java -Xmx32m -jar SMALL.JAR @BENCH.CFG --skip-crypto --resume 0 --profile 20 >> COMPARE.TXT
echo ##### 3: SMALL0.JAR (stored), pinned key pre-validated >> COMPARE.TXT
java -Xmx32m -jar SMALL0.JAR @BENCH.CFG --skip-crypto --resume 0 >> COMPARE.TXT
echo ##### 4: full jars, pinned key pre-validated >> COMPARE.TXT
java -Xmx32m -jar FULL\pastebench.jar @BENCH.CFG --skip-crypto --resume 0 >> COMPARE.TXT
echo ##### 5: resident-client warm-up, 5 s of simulated typing >> COMPARE.TXT
java -Xmx32m -jar SMALL.JAR @BENCH.CFG --skip-crypto --warmup 5000 >> COMPARE.TXT
echo ##### 2b: SMALL.JAR again (warm disk cache) >> COMPARE.TXT
java -Xmx32m -jar SMALL.JAR @BENCH.CFG --skip-crypto --resume 0 >> COMPARE.TXT
echo ##### 3b: SMALL0.JAR again (warm disk cache) >> COMPARE.TXT
java -Xmx32m -jar SMALL0.JAR @BENCH.CFG --skip-crypto --resume 0 >> COMPARE.TXT
echo Done. Results are in COMPARE.TXT
