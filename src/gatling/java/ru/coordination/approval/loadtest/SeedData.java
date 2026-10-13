package ru.coordination.approval.loadtest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Данные, созданные сидером ({@link SeedSimulation}), — пары «процесс — сущность» из файла
 * {@code loadtest.seed.file}.
 *
 * <p>Файл, а не общий статический список: сидер и сценарии — разные процессы JVM (каждый запуск
 * {@code gatlingRun} — отдельный форк), поэтому передать данные иначе, чем через файл, нельзя.
 *
 * <p>Выбор записи — round-robin через {@link AtomicInteger}, а не общий {@code Iterator}: итератор
 * не потокобезопасен, а виртуальные пользователи Gatling читают данные параллельно. Индекс
 * заворачивается через {@code floorMod}, поэтому нагрузка повторно обходит один и тот же набор
 * процессов — это и нужно: важно, чтобы запросы шли к уже прогретому кешу страниц Postgres, как в
 * реальном UI, а не к каждый раз новым строкам.
 */
public final class SeedData {

    /** Одна запись сида: по {@code entityId} читается approval-view, по {@code processId} — детали. */
    public record Entry(UUID processId, UUID entityId) {
    }

    /** Заголовок файла сида: он же — маркер строки, которую нужно пропустить при чтении. */
    public static final String HEADER = "processId,entityId";

    private static volatile SeedData instance;

    private final List<Entry> entries;
    private final AtomicInteger cursor = new AtomicInteger();

    private SeedData(List<Entry> entries) {
        this.entries = entries;
    }

    /** Ленивая инициализация: файл читается один раз на процесс генератора, при первом обращении. */
    public static SeedData get() {
        SeedData local = instance;
        if (local == null) {
            synchronized (SeedData.class) {
                local = instance;
                if (local == null) {
                    local = load();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Следующая запись по кругу. */
    public Entry next() {
        return entries.get(Math.floorMod(cursor.getAndIncrement(), entries.size()));
    }

    /** Сколько записей в сиде — попадает в отчёт прогона как контекст. */
    public int size() {
        return entries.size();
    }

    private static SeedData load() {
        Path file = LoadTestConfig.seedFile();
        if (!Files.exists(file)) {
            throw new IllegalStateException(
                    "Файл данных нагрузочного прогона не найден: " + file
                            + ". Сначала выполните сидирование: ./gradlew gatlingRun"
                            + " --simulation=ru.coordination.approval.loadtest.SeedSimulation");
        }
        try {
            List<Entry> loaded = new ArrayList<>();
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.equals(HEADER)) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length < 2) {
                    throw new IllegalStateException("Строка " + (i + 1) + " файла " + file
                            + " не соответствует формату " + HEADER + ": " + line);
                }
                loaded.add(new Entry(UUID.fromString(parts[0].trim()), UUID.fromString(parts[1].trim())));
            }
            if (loaded.isEmpty()) {
                throw new IllegalStateException("Файл данных нагрузочного прогона пуст: " + file
                        + ". Перезапустите сидирование: ./gradlew gatlingRun"
                        + " --simulation=ru.coordination.approval.loadtest.SeedSimulation");
            }
            return new SeedData(List.copyOf(loaded));
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать файл данных нагрузочного прогона: " + file, e);
        }
    }
}
