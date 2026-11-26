@echo off
if not defined POKER_DB_URL set POKER_DB_URL=jdbc:mariadb://127.0.0.1:13306/poker_game
call "%~dp0mvnw.cmd" -q -Dexec.mainClass=com.poker.PokerAdminSetupMain exec:java
pause
