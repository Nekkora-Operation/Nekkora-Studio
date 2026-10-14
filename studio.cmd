@echo off
rem Compila (sem testes) e abre o Nekkora Studio. Argumentos sao repassados (ex.: --theme=porcelain-light).
cd /d "%~dp0"
call mvnw.cmd -q -DskipTests package || exit /b 1
java -jar nekkora-studio-app\target\nekkora-studio.jar %*
