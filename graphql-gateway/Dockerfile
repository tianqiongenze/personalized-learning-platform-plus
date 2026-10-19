FROM 10.167.2.175:30500/base/eclipse-temurin:25-jre
WORKDIR /app
COPY target/graphql-gateway-1.0.0-SNAPSHOT.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseZGC -XX:+UseContainerSupport"
# Dev-only startup flags (default empty; e.g. JAVA_OPTS_DEV="-XX:TieredStopAtLevel=1 -noverify")
ENV JAVA_OPTS_DEV=""
EXPOSE 8097
ENTRYPOINT ["sh","-c","java $JAVA_OPTS $JAVA_OPTS_DEV -jar app.jar"]
