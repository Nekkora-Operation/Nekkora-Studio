#!/usr/bin/env sh
# Compila (sem testes) e abre o Nekkora Studio. Argumentos sao repassados (ex.: --theme=porcelain-light).
set -e
cd "$(dirname "$0")"
./mvnw -q -DskipTests package
exec java -jar nekkora-studio-app/target/nekkora-studio.jar "$@"
