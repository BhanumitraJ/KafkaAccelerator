FROM adoptopenjdk/openjdk11:alpine-jre
LABEL maintainer = "test"

ENV JAVA_OPTS=""
VOLUME /tmp

ADD https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=com.datadoghq&a=dd-java-agent&v=LATEST ./dd-java-agent.jar

ADD target/test-adapter-service.jar app.jar
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS $DD_AGENT_ARGS -Djava.security.egd=file:/dev/./urandom -jar /app.jar" ]
