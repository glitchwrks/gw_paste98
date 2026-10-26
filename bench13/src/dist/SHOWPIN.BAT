@echo off
rem Prints the key pin of whatever server answers, using Java 1.3. NOT verified:
rem check it against a trusted source before putting it in BENCH13.CFG.
call SETJAVA.BAT
%JAVA13% -jar PB13.JAR --showpin @BENCH13.CFG
