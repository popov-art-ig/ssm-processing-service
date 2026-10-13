package ru.coordination.approval.loadtest;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.UUID;

/**
 * Профиль «жизненный цикл»: создание процесса из снимка сущности, чтение, запуск (активация первого
 * этапа и выдача задач участникам) и отзыв.
 *
 * <p><b>Зачем отдельный профиль.</b> Это самая дорогая запись в сервисе: создание процесса
 * подбирает шаблон и генерирует маршрут (несколько вставок на процесс), запуск внутри той же
 * транзакции активирует этап и создаёт по строке задач на каждого участника
 * ({@code AssignStageTasks} → {@code AssignParticipantTasks}). Если потолок системы задан записью,
 * видно будет именно здесь, а профиль чтения этого не покажет.
 *
 * <p><b>Почему {@code recall}, а не {@code resume}.</b> Возврат на доработку ({@code resume})
 * достижим только из состояния {@code OnRework}, куда процесс попадает после отклонения решения
 * участником. Решения — отдельный профиль (следующий этап, {@code doc/LOAD_TESTING.md} §«Что дальше»),
 * и делать вид, что мы проверили {@code resume}, вызвав его из недостижимого состояния, нельзя:
 * отказ guard'а вернёт 200 с прежним статусом, и прогон отчитается зелёным.
 *
 * <p><b>Почему {@code reason} обязателен в отзыве.</b> Guard {@code IsRecallRequest} отличает отзыв
 * от возобновления по наличию {@code reason} в параметрах перехода. Без причины переход не
 * выполняется, а контроллер всё равно отвечает 200 — то есть проверка статуса после отзыва
 * поймала бы расхождение, но лучше не создавать его вовсе.
 *
 * <p>Запуск: {@code ./gradlew gatlingRun --simulation=ru.coordination.approval.loadtest.LifecycleSimulation}.
 */
public class LifecycleSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.baseUrl())
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    /**
     * Каждая итерация работает со своей сущностью: {@code entityId} — единственная часть снимка,
     * которая обязана быть уникальной, иначе процессы разных итераций ссылались бы на один документ
     * и «процессы по сущности» перестали бы быть однозначными.
     */
    private final ChainBuilder pickData = exec(
            session -> session.set("entityId", UUID.randomUUID().toString()));

    private final ScenarioBuilder lifecycle = scenario("Жизненный цикл процесса")
            .exec(pickData)
            .exec(http("Цикл: создать процесс")
                    .post("/api/v1/processes")
                    .body(StringBody("{\"entityType\":\"" + LoadTestConfig.entityType()
                            + "\",\"entitySubtype\":null,\"entityId\":\"#{entityId}\",\"attributes\":{},"
                            + "\"initiatorId\":\"" + LoadTestConfig.INITIATOR_ID + "\"}")).asJson()
                    .check(status().is(201),
                            jsonPath("$.id").saveAs("processId"),
                            jsonPath("$.status").is("Draft")))
            .exec(http("Цикл: получить созданный процесс")
                    .get("/api/v1/processes/#{processId}")
                    .check(status().is(200), jsonPath("$.stages").exists()))
            .exec(http("Цикл: запустить процесс")
                    .post("/api/v1/processes/#{processId}/start")
                    .body(StringBody("{\"actorId\":\"" + LoadTestConfig.INITIATOR_ID + "\"}")).asJson()
                    // Проверяется и статус, и наличие участников: успешный ответ без задач означал бы,
                    // что активация этапа не сработала, а такой прогон «зелёный» только по коду ответа.
                    .check(status().is(200),
                            jsonPath("$.status").is("InProgress"),
                            jsonPath("$.stages[0].iterations[0].participants[0].id").exists()))
            .exec(http("Цикл: получить запущенный процесс")
                    .get("/api/v1/processes/#{processId}")
                    .check(status().is(200), jsonPath("$.status").is("InProgress")))
            .exec(http("Цикл: отозвать процесс")
                    .post("/api/v1/processes/#{processId}/recall")
                    .body(StringBody("{\"actorId\":\"" + LoadTestConfig.INITIATOR_ID
                            + "\",\"reason\":\"Нагрузочный прогон\"}")).asJson()
                    .check(status().is(200), jsonPath("$.status").is("Recalled")));

    public LifecycleSimulation() {
        setUp(lifecycle.injectOpen(
                        rampUsersPerSec(1).to(LoadTestConfig.writeUsersPerSec())
                                .during(LoadTestConfig.writeDurationSeconds()))
                .protocols(httpProtocol))
                .assertions(
                        // Запас по ошибкам больше, чем на чтении: запись меняет состояние, и часть
                        // отказов — это осмысленные конфликты (409/404), а не дефекты. Порог
                        // подобран так, чтобы прогон падал на систематических отказах, а не на
                        // единичных.
                        global().failedRequests().percent().lt(2.0),
                        // Создание процесса — самая дорогая операция профиля: генерация маршрута и
                        // вставка этапов. Порог намеренно мягкий: цель первого этапа — увидеть
                        // реальную цифру, а не подтвердить выбранную заранее.
                        details("Цикл: создать процесс").responseTime().percentile3().lt(3000),
                        global().responseTime().percentile3().lt(2000));
    }
}
