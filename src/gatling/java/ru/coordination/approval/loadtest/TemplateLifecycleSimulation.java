package ru.coordination.approval.loadtest;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.nothingFor;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.UUID;

/**
 * Административный профиль: жизненный цикл шаблона — создание, публикация, чтение списка и форк.
 *
 * <p><b>Зачем отдельный профиль.</b> Это другая запись, чем процессы: создание шаблона пишет сам
 * шаблон, этапы шаблона и слоты, публикация прогоняет структуру через {@code ValidateTemplateStructure},
 * форк копирует структуру в новую версию (ADR-023). Ни один из этих путей не покрывается профилем
 * чтения, а деградация админских транзакций видна именно здесь.
 *
 * <p><b>Почему интенсивность низкая.</b> Шаблоны создаёт администратор, а не поток пользователей:
 * в проде это единицы операций в день. Профиль отвечает на вопрос «не деградировали ли админские
 * транзакции и не мешают ли они пользовательским чтениям», а не «какой у них потолок».
 *
 * <p><b>SLO на этот профиль не зафиксирован</b> (в отличие от UI-чтения, {@code doc/LOAD_TESTING.md}
 * §7.3): пороги ниже — грубые guardrail'ы от отказов и от секундных задержек. Форк создаёт по
 * строке шаблона на итерацию, поэтому набор данных растёт; имена начинаются с {@code loadtest-tpl-}
 * и удаляются тем же запросом очистки, что и остальные нагрузочные данные (§9).
 *
 * <p>Запуск: {@code ./gradlew gatlingRun
 * --simulation=ru.coordination.approval.loadtest.TemplateLifecycleSimulation}, интенсивность —
 * {@code -Ploadtest.template.usersPerSec} и {@code -Ploadtest.template.durationSeconds}.
 */
public class TemplateLifecycleSimulation extends Simulation {

    private static final String MEASURED_PREFIX = "Админ: ";
    private static final String WARMUP_PREFIX = "Прогрев: ";

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.baseUrl())
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    /**
     * Цепочка шага администратора. Имя шаблона уникально для каждой итерации: список шаблонов
     * фильтруется по имени, и повтор одного имени сделал бы шаг «создать» неотличимым от проверки
     * существующего.
     *
     * @param prefix префикс имён запросов: {@code Админ: } для замеряемой части, {@code Прогрев: }
     *               для прогрева, чтобы холодные запросы не попадали в целевые перцентили
     */
    private static io.gatling.javaapi.core.ChainBuilder templateChain(String prefix) {
        return exec(session -> session.set("templateName", "loadtest-tpl-" + UUID.randomUUID()))
                .exec(http(prefix + "шаблон: создать черновик")
                        .post("/api/v1/admin/templates")
                        .body(StringBody(TemplateJson.create("#{templateName}", 3, LoadTestConfig.entityType(),
                                LoadTestConfig.ADMIN_ID))).asJson()
                        .check(status().is(201),
                                jsonPath("$.id").saveAs("templateId"),
                                jsonPath("$.status").is("DRAFT")))
                .exec(http(prefix + "шаблон: опубликовать")
                        .post("/api/v1/admin/templates/#{templateId}/publish")
                        .body(StringBody(TemplateJson.actor(LoadTestConfig.ADMIN_ID))).asJson()
                        // Публикация — единственный шаг, который прогоняет структуру через движок:
                        // успешный 200 здесь означает, что валидация шаблона не стала узким местом.
                        .check(status().is(200), jsonPath("$.status").is("PUBLISHED")))
                .exec(http(prefix + "шаблон: список опубликованных")
                        .get("/api/v1/admin/templates")
                        .queryParam("status", "PUBLISHED")
                        .queryParam("size", "20")
                        // В отличие от списка процессов, здесь ответ — массив, а не страница:
                        // TemplateController.listTemplates возвращает List<TemplateDto>, и параметр
                        // size он игнорирует. Проверяем, что список непустой: в этом профиле
                        // шаблон публикуется итерацией раньше, чем запрашивается список.
                        .check(status().is(200), jsonPath("$[0].id").exists()))
                .exec(http(prefix + "шаблон: форк (новая версия)")
                        .post("/api/v1/admin/templates/#{templateId}/fork")
                        .body(StringBody(TemplateJson.actor(LoadTestConfig.ADMIN_ID))).asJson()
                        .check(status().is(201), jsonPath("$.id").exists()));
    }

    private final ScenarioBuilder templateLifecycle =
            scenario("Админ: жизненный цикл шаблона").exec(templateChain(MEASURED_PREFIX));

    private final ScenarioBuilder warmup =
            scenario("Админ: прогрев").exec(templateChain(WARMUP_PREFIX));

    public TemplateLifecycleSimulation() {
        // Прогрев отдельной сценой и последовательно (andThen): первый заход в админские
        // транзакции оплачивает JIT и наполнение кешей, и в замеряемые перцентили он попадать не
        // должен. Протокол задаётся каждой популяции — andThen его не наследует.
        setUp(warmup.injectOpen(constantUsersPerSec(LoadTestConfig.templateUsersPerSec()).during(3))
                        .protocols(httpProtocol)
                        .andThen(templateLifecycle
                                .injectOpen(constantUsersPerSec(LoadTestConfig.templateUsersPerSec())
                                        .during(LoadTestConfig.templateDurationSeconds()))
                                .protocols(httpProtocol)))
                .assertions(
                        // Админские операции не должны отказывать: 500 здесь — дефект, а не нагрузка.
                        global().failedRequests().percent().lt(1.0),
                        // Создание черновика пишет шаблон, этапы и слоты — самая тяжёлая операция
                        // профиля; порог мягкий, потому что SLO на неё не зафиксирован.
                        details(MEASURED_PREFIX + "шаблон: создать черновик").responseTime().percentile3().lt(1500),
                        global().responseTime().percentile3().lt(1500));
    }
}
