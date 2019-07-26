#!/bin/bash

mvn clean compile exec:java \
    -D"exec.mainClass"="org.test.Application" \
    -D"exec.arguments"="http://lga-doubleverify03.pulse.prod/dv-iqc?partnerid=3775&url=https%3A%2F%2Fwww.reddit.com%2Fr%2FMachineLearning%2F&useragent=&ip="
