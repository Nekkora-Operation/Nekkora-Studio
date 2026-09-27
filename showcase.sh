#!/usr/bin/env sh
# Compila (sem testes) e abre o Nekkora Design System Showcase. Argumentos são repassados.
set -e
cd "$(dirname "$0")"
./mvnw -q -DskipTests package
exec java -jar nekkora-swing-showcase/target/nekkora-showcase.jar "$@"
