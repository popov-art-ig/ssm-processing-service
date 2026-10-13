package ru.coordination.approval.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.coordination.approval.api.dto.process.*;
import ru.coordination.approval.api.dto.view.ProcessSummaryDto;
import ru.coordination.approval.api.exception.EntityNotFoundException;
import ru.coordination.approval.api.exception.GlobalExceptionHandler;
import ru.coordination.approval.api.service.ProcessViewService;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.service.ProcessService;

/**
 * Web-срез тестов для {@link ProcessController}.
 *
 * <p>Приведён в соответствие с фактическим API сервисного слоя и Spring Boot 4.0:
 * <ul>
 *   <li>{@code @WebMvcTest} переехал в {@code spring-boot-webmvc-test}
 *       ({@code org.springframework.boot.webmvc.test.autoconfigure});</li>
 *   <li>{@code @MockBean} удалён — используется
 *       {@code org.springframework.test.context.bean.override.mockito.MockitoBean};</li>
 *   <li>используется {@link ProcessService} из пакета {@code service}, а не несуществовавшего
 *       {@code api.service}.</li>
 * </ul>
 */
@WebMvcTest(ProcessController.class)
@Import(GlobalExceptionHandler.class)
class ProcessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Spring Boot 4: срез @WebMvcTest больше не предоставляет Jackson 2 ObjectMapper как бин
    // (JacksonAutoConfiguration вне среза), поэтому создаём его напрямую.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ProcessService processService;

    @MockitoBean
    private ProcessViewService processViewService;

    @Test
    void createProcess_success() throws Exception {
        CreateProcessRequest request = new CreateProcessRequest(
                "CONTRACT",
                null,
                UUID.randomUUID(),
                Map.of("amount", 1500000),
                UUID.randomUUID()
        );

        ProcessInstance mockProcess = createMockProcess();
        ProcessDto mockDto = createMockProcessDto(mockProcess);

        when(processService.createProcess(any(CreateProcessRequest.class))).thenReturn(mockProcess);
        when(processViewService.getById(mockProcess.getId())).thenReturn(mockDto);

        mockMvc.perform(post("/api/v1/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(mockProcess.getId().toString()))
                .andExpect(jsonPath("$.status").value("Draft"));
    }

    @Test
    void createProcess_invalidRequest_returns400() throws Exception {
        CreateProcessRequest request = new CreateProcessRequest(
                "",  // пустой entityType — нарушает @NotBlank
                null,
                UUID.randomUUID(),
                Map.of(),
                UUID.randomUUID()
        );

        mockMvc.perform(post("/api/v1/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProcess_success() throws Exception {
        UUID processId = UUID.randomUUID();
        ProcessInstance mockProcess = createMockProcess();
        ProcessDto mockDto = createMockProcessDto(mockProcess);

        when(processViewService.getById(processId)).thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/processes/{processId}", processId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mockProcess.getId().toString()));
    }

    @Test
    void getProcess_notFound_returns404() throws Exception {
        UUID processId = UUID.randomUUID();

        when(processViewService.getById(processId))
                .thenThrow(new EntityNotFoundException("Process not found: " + processId));

        mockMvc.perform(get("/api/v1/processes/{processId}", processId))
                .andExpect(status().isNotFound());
    }

    @Test
    void startProcess_success() throws Exception {
        UUID processId = UUID.randomUUID();
        StartProcessRequest request = new StartProcessRequest(UUID.randomUUID());

        ProcessInstance started = createMockProcess();
        started.setStatus("InProgress");
        ProcessDto mockDto = createMockProcessDto(started);

        when(processService.startProcess(eq(processId), any()))
                .thenReturn(TransitionResult.performed("Draft", "InProgress", "StartProcess", List.of()));
        when(processViewService.getById(processId)).thenReturn(mockDto);

        mockMvc.perform(post("/api/v1/processes/{processId}/start", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("InProgress"));
    }

    @Test
    void recallProcess_success() throws Exception {
        UUID processId = UUID.randomUUID();
        RecallProcessRequest request = new RecallProcessRequest(UUID.randomUUID(), "Обнаружены ошибки");

        ProcessInstance recalled = createMockProcess();
        recalled.setStatus("Recalled");
        ProcessDto mockDto = createMockProcessDto(recalled);

        when(processService.recall(eq(processId), any(), any()))
                .thenReturn(TransitionResult.performed("InProgress", "Recalled", "RecallProcess", List.of()));
        when(processViewService.getById(processId)).thenReturn(mockDto);

        mockMvc.perform(post("/api/v1/processes/{processId}/recall", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Recalled"));
    }

    @Test
    void resumeProcess_success() throws Exception {
        UUID processId = UUID.randomUUID();
        UUID targetStageId = UUID.randomUUID();

        ProcessInstance process = createMockProcess();
        process.setStatus("InProgress");
        // Контракт resume оперирует targetStageId, а TransitionEngine — orderIdx целевого этапа,
        // поэтому контроллер восстанавливает индекс по этапу процесса: этап должен существовать.
        StageInstance targetStage = StageInstance.builder()
                .id(targetStageId)
                .process(process)
                .orderIdx(1)
                .originalOrderIdx(1)
                .stageType(StageType.APPROVAL)
                .duration(3)
                .status("Pending")
                .createdAt(Instant.now())
                .build();
        process.getStages().add(targetStage);
        ProcessDto mockDto = createMockProcessDto(process);

        when(processService.getById(processId)).thenReturn(process);
        when(processService.resume(eq(processId), any(), any()))
                .thenReturn(TransitionResult.performed("OnRework", "InProgress", "ResumeProcess", List.of()));
        when(processViewService.getById(processId)).thenReturn(mockDto);

        mockMvc.perform(post("/api/v1/processes/{processId}/resume", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResumeProcessRequest(targetStageId, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("InProgress"));
    }

    @Test
    void listProcesses_withFilters_success() throws Exception {
        ProcessInstance mockProcess = createMockProcess();
        ProcessDto mockDto = createMockProcessDto(mockProcess);
        Page<ProcessDto> dtoPage = new PageImpl<>(List.of(mockDto));

        when(processViewService.list(eq("InProgress"), any(), any(), eq(0), eq(20)))
                .thenReturn(dtoPage);

        mockMvc.perform(get("/api/v1/processes")
                        .param("status", "InProgress")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(mockProcess.getId().toString()));
    }

    /**
     * {@code view=summary} в нижнем регистре (так параметр пишут в запросах) уходит в краткое
     * представление: в ответе нет этапов, зато есть имя текущего этапа, и полный маппинг не
     * вызывается вовсе.
     */
    @Test
    void listProcesses_summaryView_returnsShortCards() throws Exception {
        UUID processId = UUID.randomUUID();
        ProcessSummaryDto summary = new ProcessSummaryDto(
                processId, "CONTRACT", UUID.randomUUID(), "InProgress", Instant.now(), null, "Проверка");

        when(processViewService.listSummaries(any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/v1/processes").param("view", "summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(processId.toString()))
                .andExpect(jsonPath("$.content[0].currentStageName").value("Проверка"))
                .andExpect(jsonPath("$.content[0].stages").doesNotExist());

        verify(processViewService).listSummaries(null, null, null, 0, 20);
        verify(processViewService, never()).list(any(), any(), any(), anyInt(), anyInt());
    }

    /** Без параметра {@code view} поведение прежнее: полный процесс с этапами. */
    @Test
    void listProcesses_withoutView_returnsFullProcesses() throws Exception {
        ProcessInstance mockProcess = createMockProcess();

        when(processViewService.list(any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(createMockProcessDto(mockProcess))));

        mockMvc.perform(get("/api/v1/processes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].stages").isArray());

        verify(processViewService, never()).listSummaries(any(), any(), any(), anyInt(), anyInt());
    }

    /**
     * Неизвестное представление — это {@code 400}, а не молчаливый откат к полному ответу:
     * опечатка в параметре не должна выглядеть как другой объём данных.
     */
    @Test
    void listProcesses_unknownView_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/processes").param("view", "detailed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    /**
     * Параметр, который не преобразуется к типу ({@code initiatorId} не UUID), тоже {@code 400}.
     * До появления ветки для {@code MethodArgumentTypeMismatchException} такой запрос получал
     * {@code 500}: клиент видел внутреннюю ошибку на свою же опечатку.
     */
    @Test
    void listProcesses_invalidUuidFilter_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/processes").param("initiatorId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    private ProcessInstance createMockProcess() {
        return ProcessInstance.builder()
                .id(UUID.randomUUID())
                .entityType("CONTRACT")
                .entityId(UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .status("Draft")
                .initiatorId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
    }

    private ProcessDto createMockProcessDto(ProcessInstance process) {
        return new ProcessDto(
                process.getId(),
                process.getEntityType(),
                process.getEntitySubtype(),
                process.getEntityId(),
                process.getProcessType().name(),
                process.getStatus(),
                process.getInitiatorId(),
                process.getResponsibleUserId(),
                process.getCreatedAt(),
                process.getStartedAt(),
                process.getCompletedAt(),
                List.of()
        );
    }
}
