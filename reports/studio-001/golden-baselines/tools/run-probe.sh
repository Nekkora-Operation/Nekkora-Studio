#!/bin/sh
mkdir -p /work && cd /work && tar -xf /in/src.tar && chmod +x mvnw
./mvnw -B -ntp -q -pl nekkora-swing-ui -am install -DskipTests > /out/probe-build.log 2>&1
./mvnw -B -ntp -q -pl nekkora-swing-ui dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -Dmdep.includeScope=runtime >> /out/probe-build.log 2>&1
java -Djava.awt.headless=true -cp "$(cat /tmp/cp.txt):nekkora-swing-ui/target/classes:nekkora-design-tokens/target/classes" /in/probe/SoraProbe.java > /out/$1.csv 2>&1
