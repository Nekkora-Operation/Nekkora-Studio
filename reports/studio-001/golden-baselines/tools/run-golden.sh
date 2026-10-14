#!/bin/sh
mkdir -p /work && cd /work && tar -xf /in/src.tar && chmod +x mvnw
(java -version 2>&1 | sed -n 2p; head -1 /etc/os-release) > /out/env.txt
./mvnw -B -ntp test -pl nekkora-swing-ui -am -Dtest=GoldenScreenshotTest -Dsurefire.failIfNoSpecifiedTests=false > /out/mvn.log 2>&1
grep -h "dos pixels" /out/mvn.log | grep AssertionFailed | sed 's/org.opentest4j.AssertionFailedError: //; s/ Veja.*//' >> /out/env.txt
grep -q "dos pixels" /out/mvn.log || grep -E "Tests run:.*Golden|BUILD" /out/mvn.log | tail -3 >> /out/env.txt
cp nekkora-swing-ui/src/test/resources/golden/linux/*.actual.png /out/ 2>/dev/null; true
