@echo off
if not defined POKER_HOST set POKER_HOST=127.0.0.1
if not defined POKER_PORT set POKER_PORT=19090
call "%~dp0mvnw.cmd" -q -Dexec.mainClass=com.poker.PokerCliClientMain exec:java

