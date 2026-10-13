package ru.coordination.approval.loadtest;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.pause;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Профиль «UI-чтение»: то, что делает экран согласования, — агрегированное представление по
 * сущности, список задач пользователя, карточка процесса и страница списка.
 *
 * <p><b>Что этот профиль проверяет.</b> Требование {@code 07_api_contract.md} §25.2 —
 * {@code GET /approval-view/entities/{entityId}} быстрее 500 мс на процессе с 10 этапами.
 * Проверяется именно агрегат: он собирает процесс, этапы, участников, решения, замечания и
 * комментарии, то есть первым упирается в N+1 и в размер ответа. Остальные запросы добавлены как
 * реалистичный фон: список процессов идёт через {@code Specification} + пагинацию, «мои задачи» —
 * через join участника с этапом и процессом.
 *
 * <p><b>Два профиля подачи нагрузки</b> (см. {@link LoadTestConfig#readMode()}):
 * <ul>
 *   <li>{@code ramp} — непрерывный разгон от 1 до {@code loadtest.read.usersPerSec}: нужен, чтобы
 *       найти потолок, то есть увидеть, на какой интенсивности ломается p95;</li>
 *   <li>{@code constant} — полка заданной интенсивности после прогрева: нужна, чтобы сравнивать
 *       конфигурации между собой (например, размер пула соединений) при одной и той же нагрузке.</li>
 * </ul>
 *
 * <p><b>Почему прогрев — отдельная сцена с другими именами запросов.</b> Первые секунды прогона
 * измеряют прогрев JIT и наполнение кешей, а не поведение сервиса под нагрузкой. Если прогрев
 * выполнять тем же сценарием (второй {@code injectOpen} того же {@code ScenarioBuilder}), Gatling
 * падает на дублировании имён сцен, а если просто продлить полку — холодные запросы попадут в те же
 * перцентили, что и установившиеся. Поэтому прогрев идёт отдельной сценой с префиксом
 * {@code Прогрев:} и в отчёте виден отдельной строкой, не искажая целевые метрики {@code UI: …}.
 *
 * <p><b>Почему данные берутся из сида, а не создаются на ходу.</b> Чтение только что созданного
 * процесса меряет пустые таблицы и холодный кеш, то есть не то, что видит UI в работе. Сид даёт
 * прогретый набор процессов с этапами и задачами ({@link SeedSimulation}).
 *
 * <p>Запуск: {@code ./gradlew gatlingRun --simulation=ru.coordination.approval.loadtest.UiReadSimulation}.
 */
public class UiReadSimulation extends Simulation {

    /** Имя целевого запроса: по нему проверяется порог §25.2, поэтому вынесено в константу. */
    private static final String APPROVAL_VIEW_REQUEST = "UI: approval-view по сущности";

    private static final String MEASURED_PREFIX = "UI: ";
    private static final String WARMUP_PREFIX = "Прогрев: ";

    /**
     * SLO профиля чтения, зафиксированный по фактическим замерам (doc/LOAD_TESTING.md §7.3):
     * рабочая полка — 40 пользователей в секунду (≈150 запросов/с) на пуле соединений 20,
     * набор 30 процессов × 10 этапов. Замеры на этой полке: агрегат p95 24 мс, p99 35 мс,
     * общий p95 112 мс, p99 224 мс, ошибок 0. Пороги выставлены с запасом примерно вдвое-втрое,
     * чтобы падение означало регрессию, а не шум прогона.
     *
     * <p>Пороги читаются из {@link LoadTestConfig#sloAggregateP95Ms()} и соседей, то есть
     * переопределяются свойствами {@code -Ploadtest.slo.*}: на другой машине (раннер CI, стенд)
     * абсолютные цифры иные, и базу нужно перемерить и зафиксировать заново — но осознанно, а не
     * потому что «в CI не проходит».
     */
    private static final double SLO_SHELF_USERS_PER_SEC = LoadTestConfig.sloShelfUsersPerSec();
    private static final int SLO_AGGREGATE_P95_MS = LoadTestConfig.sloAggregateP95Ms();
    private static final int SLO_AGGREGATE_P99_MS = LoadTestConfig.sloAggregateP99Ms();
    private static final int SLO_GLOBAL_P95_MS = LoadTestConfig.sloGlobalP95Ms();
    private static final int SLO_GLOBAL_P99_MS = LoadTestConfig.sloGlobalP99Ms();
    private static final double SLO_FAILED_PERCENT = LoadTestConfig.sloFailedPercent();

    /**
     * Выбор данных на итерацию: процесс, сущность и один из согласующих. Круговой обход
     * ({@link SeedData#next()}) вместо случайного — так за прогон гарантированно будут прочитаны
     * все процессы сида, а не только «популярные»; случайность здесь ничего не добавляет, потому что
     * данные однородные.
     */
    private static final ChainBuilder PICK_DATA = exec(session -> {
        SeedData.Entry entry = SeedData.get().next();
        UUID approver = LoadTestConfig.approverIds()
                .get(ThreadLocalRandom.current().nextInt(LoadTestConfig.approverIds().size()));
        return session
                .set("processId", entry.processId().toString())
                .set("entityId", entry.entityId().toString())
                .set("userId", approver.toString())
                .set("entityType", LoadTestConfig.entityType());
    });

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.baseUrl())
            .acceptHeader("application/json");

    private final ScenarioBuilder uiRead =
            scenario("UI-чтение").exec(readChain(MEASURED_PREFIX));

    private final ScenarioBuilder warmup =
            scenario("UI-чтение: прогрев").exec(readChain(WARMUP_PREFIX));

    /**
     * Сценарий экрана: агрегат по сущности (целевой запрос), задачи пользователя, карточка
     * процесса и страница списка. Паузы между запросами — «время на чтение экрана»: без них профиль
     * превращается в генератор максимального RPS, который не соответствует поведению UI и даёт
     * цифры, по которым нельзя планировать ёмкость.
     *
     * @param prefix префикс имён запросов: {@code UI: } для замеряемой части, {@code Прогрев: } для
     *               прогрева, чтобы метрики этих частей не смешивались в отчёте
     */
    private static ChainBuilder readChain(String prefix) {
        return exec(PICK_DATA)
                .exec(http(prefix + "approval-view по сущности")
                        .get("/api/v1/approval-view/entities/#{entityId}")
                        .queryParam("entityType", "#{entityType}")
                        .check(status().is(200), jsonPath("$.process.id").exists()))
                .pause(Duration.ofMillis(300))
                .exec(http(prefix + "мои задачи")
                        .get("/api/v1/approval-view/tasks/my")
                        .queryParam("userId", "#{userId}")
                        .queryParam("size", "20")
                        .check(status().is(200), jsonPath("$.content").exists()))
                .pause(Duration.ofMillis(200))
                .exec(http(prefix + "карточка процесса")
                        .get("/api/v1/processes/#{processId}")
                        .check(status().is(200), jsonPath("$.stages").exists()))
                .pause(Duration.ofMillis(200))
                .exec(http(prefix + "список процессов")
                        .get("/api/v1/processes")
                        .queryParam("status", "InProgress")
                        .queryParam("size", "20")
                        .check(status().is(200), jsonPath("$.content").exists()));
    }

    public UiReadSimulation() {
        var run = setUp(injection());
        if (isConstantMode()) {
            // Полка — это SLO-прогон: пороги жёсткие и взяты из замеров на рабочей полке.
            // Интенсивность задаётся снаружи, и если она ниже SLO-полки, прогон честно легче, а не
            // «зеленее»: пороги от неё не зависят.
            if (Math.abs(LoadTestConfig.readUsersPerSec() - SLO_SHELF_USERS_PER_SEC) > 0.001) {
                System.out.printf("ВНИМАНИЕ: полка %.1f польз./с отличается от SLO-полки %.0f польз./с —"
                                + " пороги SLO рассчитаны на %.0f%n",
                        LoadTestConfig.readUsersPerSec(), SLO_SHELF_USERS_PER_SEC, SLO_SHELF_USERS_PER_SEC);
            }
            run.assertions(
                    global().failedRequests().percent().lt(SLO_FAILED_PERCENT),
                    details(APPROVAL_VIEW_REQUEST).responseTime().percentile3().lt(SLO_AGGREGATE_P95_MS),
                    details(APPROVAL_VIEW_REQUEST).responseTime().percentile4().lt(SLO_AGGREGATE_P99_MS),
                    global().responseTime().percentile3().lt(SLO_GLOBAL_P95_MS),
                    global().responseTime().percentile4().lt(SLO_GLOBAL_P99_MS));
        } else {
            // Разгон ищет потолок и специально доводит систему до отказа, поэтому здесь только
            // грубые guardrail'ы: жёсткие пороги на разгоне падали бы всегда и ничего не сообщали.
            run.assertions(
                    // Ошибки чтения — это либо 500 (дефект), либо таймаут: на чтении их быть
                    // не должно, в отличие от профиля записи, где возможны конфликты состояний.
                    global().failedRequests().percent().lt(1.0),
                    // Требование §25.2 проверяется и здесь: на разгоне видно, при какой
                    // интенсивности агрегат перестаёт в него укладываться.
                    details(APPROVAL_VIEW_REQUEST).responseTime().percentile3().lt(500),
                    details(APPROVAL_VIEW_REQUEST).responseTime().percentile4().lt(1000),
                    global().responseTime().percentile3().lt(1000));
        }
    }

    private static boolean isConstantMode() {
        return "constant".equalsIgnoreCase(LoadTestConfig.readMode());
    }

    /**
     * Профиль подачи нагрузки.
     *
     * <p>{@code ramp} (по умолчанию) — непрерывный разгон: нужен, чтобы найти потолок, но для
     * сравнения конфигураций между собой он не годится — прогоны проходят через разные участки
     * нагрузки, и «лучшим» выглядит тот, что раньше закончился.
     *
     * <p>{@code constant} — полка заданной интенсивности, которой предшествует прогрев той же
     * интенсивности ({@code andThen} выстраивает популяции последовательно, а не параллельно).
     *
     * <p>{@code protocols} задаётся <b>каждой</b> популяции: {@code andThen} не наследует протокол
     * первой, и без этого у измеряемой части нет {@code baseUrl} — запросы падают с
     * «No baseUrl defined but provided url is relative», а отчёт показывает ошибки вместо замеров.
     */
    private PopulationBuilder injection() {
        double rate = LoadTestConfig.readUsersPerSec();
        int duration = LoadTestConfig.readDurationSeconds();
        if (isConstantMode()) {
            return warmup.injectOpen(constantUsersPerSec(rate)
                            .during(LoadTestConfig.readWarmupSeconds()))
                    .protocols(httpProtocol)
                    .andThen(uiRead.injectOpen(constantUsersPerSec(rate).during(duration))
                            .protocols(httpProtocol));
        }
        return uiRead.injectOpen(rampUsersPerSec(1).to(rate).during(duration)).protocols(httpProtocol);
    }
}
