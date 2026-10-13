package ru.coordination.approval.loadtest;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Конфигурация нагрузочного прогона (порядок запуска и разбор результатов — {@code doc/LOAD_TESTING.md}).
 *
 * <p>Значения приходят системными свойствами из Gradle-задачи {@code gatlingRun}: блок
 * {@code gatling { systemProperties = ... }} в {@code build.gradle} транслирует свойства
 * {@code -Ploadtest.*} в {@code loadtest.*}. Так прогон воспроизводим — одинаковый набор
 * {@code -P} даёт одинаковый профиль нагрузки, — а сценарии не зависят от переменных окружения
 * конкретной машины.
 *
 * <p><b>Почему идентификаторы фиксированы, а не случайны.</b> Сидер создаёт данные от имени
 * заранее известных пользователей, а сценарий чтения обращается к «своим задачам» по
 * {@code userId}. Случайные идентификаторы означали бы, что после каждого пересоздания данных
 * список задач пуст: запрос формально успешен (200 с пустой страницей), но проверяет не то, что
 * задумано. Фиксированные значения (нижние 12 hex-цифр читаются как роль: {@code ad}, {@code 1a},
 * {@code ab}) позволяют узнать нагрузочные данные в БД и не пересечься с боевыми.
 */
public final class LoadTestConfig {

    /** Автор шаблона и актор административных вызовов сидера. */
    public static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-00000000ad01");

    /** Инициатор процессов и актор запуска/отзыва: на принадлежность инициатору сервис не проверяет. */
    public static final UUID INITIATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000001a01");

    /** Согласующие: в шаблоне по одному слоту на этап, поэтому каждый получает задачи. */
    private static final List<UUID> APPROVER_IDS = List.of(
            UUID.fromString("00000000-0000-0000-0000-00000000ab01"),
            UUID.fromString("00000000-0000-0000-0000-00000000ab02"),
            UUID.fromString("00000000-0000-0000-0000-00000000ab03"));

    private static final DateTimeFormatter RUN_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private LoadTestConfig() {
    }

    /** Базовый адрес сервиса под нагрузкой (без завершающего слэша). */
    public static String baseUrl() {
        return property("loadtest.baseUrl", "http://localhost:8080");
    }

    /**
     * Тип сущности нагрузочных данных. Отдельный от боевых ({@code CONTRACT} и т. п.), чтобы
     * созданный шаблон матчился только на процессы прогона, а данные прогона можно было удалить
     * одним условием по {@code entity_type}.
     */
    public static String entityType() {
        return property("loadtest.entityType", "LOADTEST");
    }

    /** Количество процессов, которое создаёт сидер. */
    public static int seedProcesses() {
        return intProperty("loadtest.seed.processes", 200);
    }

    /**
     * Сколько этапов в шаблоне прогона. Требование {@code 07_api_contract.md} §25.2 говорит про
     * процесс с <b>десятью</b> этапами, поэтому три этапа — это только быстрый дымовой прогон;
     * для проверки самого требования нужен {@code -Ploadtest.seed.stages=10}.
     *
     * <p>Слоты этапов заполняются согласующими по кругу из {@link #approverIds()}: пользователей
     * трое, а этапов может быть больше — так у каждого остаются задачи, и
     * {@code /approval-view/tasks/my} не превращается в пустую страницу.
     */
    public static int seedStages() {
        return intProperty("loadtest.seed.stages", 3);
    }

    /**
     * Профиль нагрузки сценария чтения: {@code ramp} — непрерывный разгон (поиск потолка),
     * {@code constant} — полка постоянной интенсивности (сравнение конфигураций и будущие SLO-пороги).
     *
     * <p>Полка нужна именно для сравнения конфигураций: на разгоне прогоны с разным размером пула
     * проходят через разные участки нагрузки, и «лучший» результат получает тот, кто раньше
     * закончил. На полке разница видна как разница в задержке при одной и той же интенсивности.
     */
    public static String readMode() {
        return property("loadtest.read.mode", "ramp");
    }

    /** Сколько секунд не подавать нагрузку в режиме полки: без прогрева в замер попадает JIT. */
    public static int readWarmupSeconds() {
        return intProperty("loadtest.read.warmupSeconds", 10);
    }

    /** Файл с идентификаторами созданных процессов: его пишет сидер, читают сценарии чтения. */
    public static Path seedFile() {
        return Path.of(property("loadtest.seed.file", "build/loadtest/seed.csv"));
    }

    /** Интенсивность профиля «UI-чтение», пользователей в секунду (открытая модель). */
    public static double readUsersPerSec() {
        return doubleProperty("loadtest.read.usersPerSec", 20);
    }

    /** Длительность полки профиля «UI-чтение» в секундах. */
    public static int readDurationSeconds() {
        return intProperty("loadtest.read.durationSeconds", 120);
    }

    /**
     * Интенсивность профиля «жизненный цикл», пользователей в секунду. По умолчанию на порядок
     * ниже чтения: создание процесса и запуск этапа — это генерация маршрута и запись нескольких
     * таблиц в одной транзакции, то есть на единицу нагрузки они дороже на порядки.
     */
    public static double writeUsersPerSec() {
        return doubleProperty("loadtest.write.usersPerSec", 2);
    }

    /** Длительность полки профиля «жизненный цикл» в секундах. */
    public static int writeDurationSeconds() {
        return intProperty("loadtest.write.durationSeconds", 120);
    }

    /** Согласующие, которым выдаются задачи (по одному на этап шаблона). */
    public static List<UUID> approverIds() {
        return APPROVER_IDS;
    }

    /**
     * Интенсивность административного профиля (жизненный цикл шаблона), пользователей в секунду.
     *
     * <p>По умолчанию низкая осознанно: шаблоны создаёт администратор, а не поток пользователей,
     * и профиль нужен, чтобы проверить, что админские транзакции не деградируют вместе с ростом
     * данных, а не чтобы найти на них потолок.
     */
    public static double templateUsersPerSec() {
        return doubleProperty("loadtest.template.usersPerSec", 1);
    }

    /** Длительность административного профиля в секундах. */
    public static int templateDurationSeconds() {
        return intProperty("loadtest.template.durationSeconds", 30);
    }

    /**
     * Интенсивность SLO-полки, пользователей в секунду.
     *
     * <p>Пороги SLO ниже заданы для этой полки, поэтому сценарий предупреждает, если запустить
     * полку с другой интенсивностью.
     */
    public static double sloShelfUsersPerSec() {
        return doubleProperty("loadtest.slo.shelfUsersPerSec", 40);
    }

    /** Порог доли ошибок SLO, проценты. */
    public static double sloFailedPercent() {
        return doubleProperty("loadtest.slo.failedPercent", 0.1);
    }

    /**
     * Порог p95 целевого агрегата {@code /approval-view/entities}, мс.
     *
     * <p>Значения по умолчанию — измеренная база (doc/LOAD_TESTING.md §7.3): полка 40 польз./с,
     * пул 20, набор 30 процессов × 10 этапов дала p95 24 мс. Пороги настраиваются свойствами
     * {@code -Ploadtest.slo.*}, потому что на другой машине (например на раннере CI с 4 vCPU)
     * абсолютные значения будут иными: там базу надо перемерить и зафиксировать заново, а не
     * молча ослабить порог.
     */
    public static int sloAggregateP95Ms() {
        return intProperty("loadtest.slo.aggregateP95Ms", 150);
    }

    /** Порог p99 агрегата, мс; по умолчанию совпадает с требованием §25.2 (500 мс). */
    public static int sloAggregateP99Ms() {
        return intProperty("loadtest.slo.aggregateP99Ms", 500);
    }

    /** Порог p95 всего профиля чтения, мс. */
    public static int sloGlobalP95Ms() {
        return intProperty("loadtest.slo.globalP95Ms", 300);
    }

    /** Порог p99 всего профиля чтения, мс. */
    public static int sloGlobalP99Ms() {
        return intProperty("loadtest.slo.globalP99Ms", 800);
    }

    /**
     * Имя шаблона прогона. С отметкой времени, потому что «переиспользовать существующий шаблон»
     * пришлось бы через проверку наличия, а лишняя ветка в сидере — это лишний способ получить
     * неполные данные. Накопленные шаблоны {@code LOADTEST} безвредны: подбор идёт по применимости
     * и при равном приоритете выбирает самый свежий ({@code MatchService#findMatchingTemplates}).
     */
    public static String templateName() {
        return "loadtest-" + entityType().toLowerCase() + "-s" + seedStages() + "-"
                + RUN_STAMP.format(LocalDateTime.now());
    }

    private static String property(String name, String fallback) {
        return System.getProperty(name, fallback);
    }

    private static int intProperty(String name, int fallback) {
        return Integer.parseInt(property(name, Integer.toString(fallback)));
    }

    private static double doubleProperty(String name, double fallback) {
        return Double.parseDouble(property(name, Double.toString(fallback)));
    }
}
