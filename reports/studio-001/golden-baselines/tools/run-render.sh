#!/bin/sh
mkdir -p /work && cd /work && tar -xf /in/src.tar && chmod +x mvnw
./mvnw -B -ntp -q test -pl nekkora-swing-ui -am -Dtest=GoldenScreenshotTest -Dsurefire.failIfNoSpecifiedTests=false -Dnekkora.golden.update=true > /out/render.log 2>&1
cp nekkora-swing-ui/src/test/resources/golden/linux/*.png /out/
