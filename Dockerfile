# Многоэтапная сборка для минимизации размера образа
FROM gradle:8.14-jdk21-alpine AS build
WORKDIR /app

# Копируем build files
COPY build.gradle settings.gradle ./

# Скачиваем зависимости (кэшируется отдельным слоем)
RUN gradle dependencies --no-daemon

# Копируем исходный код
COPY src src

# Собираем приложение
RUN gradle build -x test --no-daemon

# Финальный образ
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Создаем пользователя для запуска приложения (безопасность)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Копируем JAR из build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Порт приложения (Spring Boot по умолчанию 8080)
EXPOSE 8080

# Запуск приложения
ENTRYPOINT ["java", "-jar", "app.jar"]
