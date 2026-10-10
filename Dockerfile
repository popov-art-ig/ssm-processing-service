# Локальный образ для рантайм-проверки.
#
# Почему не полная сборка внутри образа: стадия `gradle build` требует доступа к
# repo.maven.apache.org из контейнера, и в этом окружении Docker не проходит TLS-handshake
# («Remote host terminated the handshake»). Jar собирается локально (`./gradlew bootJar`),
# где кэш зависимостей уже прогрет, а образ только упаковывает результат.
#
# Продакшн-сборка образа — `./gradlew bootBuildImage` (см. README): она использует
# buildpacks и не требует Dockerfile.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Пользователь без root для запуска приложения.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --chown=spring:spring build/libs/ssm-processing-service-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
