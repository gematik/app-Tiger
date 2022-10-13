FROM adoptopenjdk/maven-openjdk11:latest

EXPOSE 8080
RUN mkdir /app
COPY target/tiger-standalone-proxy*-SNAPSHOT.jar /app/tiger-proxy.jar
WORKDIR /app
HEALTHCHECK CMD curl --fail http://localhost:8080/rbel | grep UP || exit 1
CMD ["java", "-jar", "tiger-proxy.jar", "-p", "8080"]
