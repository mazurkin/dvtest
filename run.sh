#!/bin/bash

mvn clean compile exec:java \
    -D"exec.mainClass"="org.test.Application"