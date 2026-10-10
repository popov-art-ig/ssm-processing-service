package ru.coordination.approval.api.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.api.dto.process.ProcessDto;
import ru.coordination.approval.api.mapper.ProcessMapper;
import ru.coordination.approval.service.ProcessService;

/**
 * Чтение процесса в представление API.
 *
 * <p>Вынесено в отдельный публичный бин, потому что {@code @Transactional} на private-методе
 * контроллера не действует: Spring создаёт прокси только вокруг публичных методов, и вызов
 * {@code this.toDto(...)} внутри контроллера шёл мимо прокси — транзакция не открывалась,
 * сессия закрывалась, и маппинг падал с {@code LazyInitializationException} на
 * {@code ProcessInstance.stages}.
 *
 * <p>Транзакция строго read-only и охватывает <b>только</b> чтение и маппинг. Помечать read-only
 * сам write-эндпоинт нельзя: {@code readOnly} ставит {@code FlushMode.MANUAL} на всю транзакцию,
 * и изменения статусов перестают сбрасываться в БД.
 */
@Service
@RequiredArgsConstructor
public class ProcessViewService {

    private final ProcessService processService;
    private final ProcessMapper processMapper;

    @Transactional(readOnly = true)
    public ProcessDto getById(UUID processId) {
        return processMapper.toDto(processService.getById(processId));
    }

    @Transactional(readOnly = true)
    public Page<ProcessDto> list(
            String status, UUID initiatorId, String entityType, int page, int size) {
        return processService.listProcesses(status, initiatorId, entityType, page, size)
                .map(processMapper::toDto);
    }
}
