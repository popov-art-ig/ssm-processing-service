package ru.coordination.approval.domain.process;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Доступ к {@link Remark} — замечаниям к процессу и их жизненному циклу (PHASE-11). Наличие
 * необработанных замечаний — предусловие возврата процесса в работу, поэтому отчёт
 * {@code existsByProcessIdAndStatusIn} используется guard'ом, а не загрузкой коллекции.
 */
public interface RemarkRepository extends JpaRepository<Remark, UUID> {

    /**
     * Есть ли у процесса хотя бы одно замечание в одном из перечисленных статусов.
     * Проверка одним {@code exists}-запросом — вызывается из guard'ов, где важен только факт.
     */
    boolean existsByProcessIdAndStatusIn(UUID processId, List<String> statuses);

    /** Все замечания процесса (в порядке вставки) — для карточки согласования. */
    List<Remark> findByProcessId(UUID processId);

    /**
     * Замечания процесса в конкретном статусе. Статус — строковый код из {@code status_registry}
     * ({@code entityType = REMARK}): {@code Draft}, {@code Active}, {@code InProgress},
     * {@code Processed}, {@code Rejected}, {@code Proposed}, {@code NotRequired}
     * (значения засеяны миграцией V24).
     */
    List<Remark> findByProcessIdAndStatus(UUID processId, String status);
}
