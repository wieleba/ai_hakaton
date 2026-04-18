FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

RUN apk add --no-cache wget unzip && \
    wget https://services.gradle.org/distributions/gradle-8.7-bin.zip && \
    unzip gradle-8.7-bin.zip && \
    rm gradle-8.7-bin.zip && \
    mv gradle-8.7 /opt/gradle && \
    ln -s /opt/gradle/bin/gradle /usr/local/bin/gradle

ENV GRADLE_HOME=/opt/gradle
ENV PATH=$GRADLE_HOME/bin:$PATH

COPY build.gradle settings.gradle ./
COPY src src/

RUN gradle build -x test --no-daemon

EXPOSE 8080

CMD ["java", "-jar", "build/libs/chat-server-1.0.0.jar"]
