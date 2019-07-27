#!/bin/sh

CURRENT_DIR=$(dirname $(readlink -f $0))

for LIB in ${CURRENT_DIR}/../lib/*.jar
do
    CLASSPATH=${CLASSPATH}:${LIB}
done

# https://docs.oracle.com/javase/8/docs/technotes/guides/net/properties.html

exec ${JAVA_HOME}/bin/java \
    -Xms2048m \
    -Xmx2048m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=100 \
    -XX:-OmitStackTraceInFastThrow \
    -Dnetworkaddress.cache.ttl=600 \
    -Dnetworkaddress.cache.negative.ttl=600 \
    -Dsun.net.client.defaultConnectTimeout=30000 \
    -Dsun.net.client.defaultReadTimeout=30000 \
    -Dsun.net.http.retryPost=false \
    -Djava.net.preferIPv4Stack=true \
    -Duser.timezone=America/New_York \
    -Duser.country=US \
    -Duser.language=en \
    -cp ${CLASSPATH} \
    "org.test.Application"
