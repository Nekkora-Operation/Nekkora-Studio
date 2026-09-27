@echo off
rem Compila (sem testes) e abre o Nekkora Design System Showcase. Argumentos sao repassados.
cd /d "%~dp0"
call mvnw.cmd -q -DskipTests package || exit /b 1
java -jar nekkora-swing-showcase\target\nekkora-showcase.jar %*
