#!/bin/bash

export MAVEN_OPTS="-Xms4096m -Xmx4096m -XX:+UseG1GC -XX:MaxGCPauseMillis=1000"

mvn clean compile

mvn exec:java \
    -D"exec.mainClass"="org.test.Application"