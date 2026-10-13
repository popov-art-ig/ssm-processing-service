package ru.coordination.approval.loadtest;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.repeat;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Сидер нагрузочных данных: создаёт и публикует шаблон, затем создаёт и запускает
 * {@code loadtest.seed.processes} процессов, а идентификаторы складывает в
 * {@code loadtest.seed.file} для сценариев чтения.
 *
 * <p><b>Почему сценарий Gatling, а не отдельный скрипт.</b> Сидирование идёт тем же HTTP API и
 * теми же проверками ответов, что и нагрузочный прогон: если API изменится, сидер упадёт вместе
 * с тестами, а не молча создаст данные, которых сценарии не найдут. Плюс сидер получает отчёт с
 * временами — видно, сколько стоит подготовка данных.
 *
 * <p><b>Почему не только SQL.</b> Прямая вставка строк быстрее, но обошла бы движок состояний:
 * процесс в {@code Draft} без сгенерированного маршрута, этапов и задач участников — это не то
 * состояние, которое читает UI, и сценарии чтения измеряли бы пустые агрегаты.
 *
 * <p>Запуск (после поднятия сервиса): {@code ./gradlew gatlingRun
 * --simulation=ru.coordination.approval.loadtest.SeedSimulation}.
 */
public class SeedSimulation extends Simulation {

    /**
     * Созданные пары в порядке создания. Список синхронизирован, хотя сценарий однопоточный
     * ({@code atOnceUsers(1)}): порядок строк в файле не должен зависеть от того, как JIT решит
     * переставить обращения к разделяемой коллекции.
     */
    private static final List<String[]> CREATED = Collections.synchronizedList(new ArrayList<>());

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.baseUrl())
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    /**
     * Создание и публикация шаблона. Публикация обязательна: {@code MatchService} рассматривает
     * только {@code PUBLISHED} (иначе {@code NoMatchingTemplateException} → 404 на создании
     * процесса), а {@code ValidateTemplateStructure} проверяет структуру именно в этот момент.
     */
    private final ChainBuilder createTemplate = exec(
            http("Сидер: создать шаблон")
                    .post("/api/v1/admin/templates")
                    .body(StringBody(TemplateJson.create(LoadTestConfig.templateName(),
                            LoadTestConfig.seedStages(), LoadTestConfig.entityType(),
                            LoadTestConfig.ADMIN_ID))).asJson()
                    .check(status().is(201), jsonPath("$.id").saveAs("templateId")))
            .exec(http("Сидер: опубликовать шаблон")
                    .post("/api/v1/admin/templates/#{templateId}/publish")
                    .body(StringBody(TemplateJson.actor(LoadTestConfig.ADMIN_ID))).asJson()
                    .check(status().is(200)));

    /**
     * Создание процесса из снимка сущности и его запуск ({@code Draft → InProgress} с активацией
     * первого этапа и выдачей задач участникам). Два вызова, а не один, потому что API разделяет
     * их: маршрут генерируется при создании, а активация этапов — при старте.
     */
    private final ChainBuilder createAndStartProcess = exec(
            session -> session.set("entityId", UUID.randomUUID().toString()))
            .exec(http("Сидер: создать процесс")
                    .post("/api/v1/processes")
                    .body(StringBody(TemplateJson.createProcess(LoadTestConfig.entityType(),
                            LoadTestConfig.INITIATOR_ID))).asJson()
                    .check(status().is(201),
                            jsonPath("$.id").saveAs("processId"),
                            jsonPath("$.entityId").saveAs("entityId")))
            .exec(http("Сидер: запустить процесс")
                    .post("/api/v1/processes/#{processId}/start")
                    .body(StringBody(TemplateJson.actor(LoadTestConfig.INITIATOR_ID))).asJson()
                    .check(status().is(200)))
            .exec(session -> {
                // Запись добавляется только при успешном создании: иначе в файл попал бы null,
                // и сценарии чтения падали бы на разборе данных вместо явного «сид неполный».
                String processId = session.getString("processId");
                String entityId = session.getString("entityId");
                if (processId != null && entityId != null) {
                    CREATED.add(new String[]{processId, entityId});
                }
                return session;
            });

    private final ScenarioBuilder seed = scenario("Сидер нагрузочных данных")
            .exec(createTemplate)
            .repeat(LoadTestConfig.seedProcesses()).on(createAndStartProcess);

    public SeedSimulation() {
        setUp(seed.injectOpen(atOnceUsers(1)).protocols(httpProtocol))
                // Сид обязан пройти целиком: частично созданные данные выглядят для сценариев
                // чтения как валидный, но неполный набор, и «зелёный» прогон читал бы меньше
                // процессов, чем заявлено в конфигурации.
                .assertions(global().failedRequests().count().lt(1L));
    }

    /**
     * Файл данных пишется один раз в конце прогона, а не построчно: частично записанный файл хуже
     * отсутствующего — сценарии чтения приняли бы его за полный набор и отчитались бы зелёным.
     */
    @Override
    public void after() {
        Path file = LoadTestConfig.seedFile();
        int expected = LoadTestConfig.seedProcesses();
        StringBuilder csv = new StringBuilder(SeedData.HEADER).append('\n');
        synchronized (CREATED) {
            for (String[] row : CREATED) {
                csv.append(row[0]).append(',').append(row[1]).append('\n');
            }
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, csv.toString());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Не удалось записать файл данных нагрузочного прогона: " + file, e);
        }
        if (CREATED.size() < expected) {
            System.err.printf("Сидер: создано %d процессов из %d — файл %s неполный%n",
                    CREATED.size(), expected, file);
        } else {
            System.out.printf("Сидер: создано %d процессов, данные в %s%n", CREATED.size(), file);
        }
    }
}
