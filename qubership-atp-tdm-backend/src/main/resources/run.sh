#!/bin/sh

if [ "${ATP_INTERNAL_GATEWAY_ENABLED:-false}" = "true" ]; then
  echo "Internal gateway integration is enabled."
  FEIGN_ATP_MAILSENDER_NAME=${FEIGN_ATP_INTERNAL_GATEWAY_NAME}
  FEIGN_ATP_HIGHCHARTS_NAME=${FEIGN_ATP_INTERNAL_GATEWAY_NAME}
else
  echo "Internal gateway integration is disabled."
  FEIGN_ATP_MAILSENDER_ROUTE=
  FEIGN_ATP_HIGHCHARTS_URL=
fi

JAVA_OPTIONS="${JAVA_OPTIONS} -Dspring.devtools.add-properties=false"
JAVA_OPTIONS="${JAVA_OPTIONS} -Dlog.graylog.on=${LOG_GRAYLOG_ON:-false}"
JAVA_OPTIONS="${JAVA_OPTIONS} -Djdbc.MinIdle=20 -Djdbc.MaxPoolSize=50"
JAVA_OPTIONS="${JAVA_OPTIONS} -Dspring.config.location=file:./config/application.properties"
JAVA_OPTIONS="${JAVA_OPTIONS} -Dspring.cloud.bootstrap.location=file:./config/bootstrap.properties"


/usr/bin/java ${JAVA_OPTIONS} --add-opens java.base/java.lang=ALL-UNNAMED -XX:+PrintFlagsFinal -XX:MaxRAM=${MAX_RAM:-1024m} -cp "./config/:./lib/*" org.qubership.atp.tdm.Main
