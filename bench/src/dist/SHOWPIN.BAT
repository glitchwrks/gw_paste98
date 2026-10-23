@echo off
rem Connects once and prints the server's SPKI pin WITHOUT verifying it.
rem Compare it with the pin computed from the key on the server (README.md).
java -Xmx32m -jar SMALL.JAR --show-pin --skip-crypto --host pastes.glitchwrks.com --port 443
