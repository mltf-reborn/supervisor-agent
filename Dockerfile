FROM eclipse-temurin:25-jdk
VOLUME /tmp
EXPOSE 8080
ARG VERSION
ARG JAR_FILE=build/libs/supervisor-agent-${VERSION}.jar
ADD ${JAR_FILE} app/app.jar
ENTRYPOINT ["java","-jar","app/app.jar"]