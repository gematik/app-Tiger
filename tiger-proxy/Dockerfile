FROM adoptopenjdk/maven-openjdk11:latest

EXPOSE 6666
RUN mkdir /app
COPY target/*standalone.jar /app/tiger-proxy.jar
WORKDIR /app
HEALTHCHECK CMD curl --fail http://localhost:6666/rbel | grep UP || exit 1
CMD ["java", "-jar", "tiger-proxy.jar", "-p", "6666"]
