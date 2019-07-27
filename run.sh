#!/bin/bash

export MAVEN_OPTS="-Xms2048m -Xmx2048m -XX:+UseG1GC -XX:MaxGCPauseMillis=20"

mvn clean compile exec:java \
    -D"exec.mainClass"="org.test.Application"