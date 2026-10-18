FROM m.daocloud.io/docker.io/library/eclipse-temurin:25-jre
WORKDIR /app
COPY target/graphql-gateway-1.0.0-SNAPSHOT.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseZGC"
EXPOSE 8097
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
