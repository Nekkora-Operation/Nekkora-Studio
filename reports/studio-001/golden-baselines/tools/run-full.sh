#!/bin/sh
mkdir -p /work && cd /work && tar -xf /in/src.tar && chmod +x mvnw
(java -version 2>&1 | sed -n 2p; head -1 /etc/os-release) > /out/env.txt
./mvnw -B -ntp clean verify -Dmaven.test.failure.ignore=true > /out/mvn.log 2>&1
echo "exit=$?" >> /out/env.txt
