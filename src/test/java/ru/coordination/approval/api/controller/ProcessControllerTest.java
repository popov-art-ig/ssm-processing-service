package ru.coordination.approval.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.coordination.approval.api.dto.process.*;
import ru.coordination.approval.api.mapper.ProcessMapper;
import ru.coordination.approval.api.service.ProcessService;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;

@WebMvcTest(ProcessController.class)
class ProcessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProcessService processService;

    @MockBean
    private ProcessRepository processRepository;

    @MockBean
    private ProcessMapper processMapper;

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

        when(processService.createProcess(any(), any(), any(), any(), any()))
                .thenReturn(mockProcess);
        when(processMapper.toDto(mockProcess)).thenReturn(mockDto);

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
                "",  // пустой entityType
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

        when(processService.getById(processId)).thenReturn(mockProcess);
        when(processMapper.toDto(mockProcess)).thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/processes/{processId}", processId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mockProcess.getId().toString()));
    }

    @Test
    void getProcess_notFound_returns404() throws Exception {
        UUID processId = UUID.randomUUID();

        when(processService.getById(processId))
                .thenThrow(new ru.coordination.approval.api.exception.EntityNotFoundException("Process not found"));

        mockMvc.perform(get("/api/v1/processes/{processId}", processId))
                .andExpect(status().isNotFound());
    }

    @Test
    void startProcess_success() throws Exception {
        UUID processId = UUID.randomUUID();
        StartProcessRequest request = new StartProcessRequest(UUID.randomUUID());

        ProcessInstance mockProcess = createMockProcess();
        mockProcess.setStatus("InProgress");
        ProcessDto mockDto = createMockProcessDto(mockProcess);

        when(processService.startProcess(eq(processId), any())).thenReturn(mockProcess);
        when(processMapper.toDto(mockProcess)).thenReturn(mockDto);

        mockMvc.perform(post("/api/v1/processes/{processId}/start", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("InProgress"));
    }

    @Test
    void recallProcess_success() throws Exception {
        UUID processId = UUID.randomUUID();
        RecallProcessRequest request = new RecallProcessRequest(UUID.randomUUID());

        ProcessInstance mockProcess = createMockProcess();
        mockProcess.setStatus("Recalled");
        ProcessDto mockDto = createMockProcessDto(mockProcess);

        when(processService.recall(eq(processId), any())).thenReturn(mockProcess);
        when(processMapper.toDto(mockProcess)).thenReturn(mockDto);

        mockMvc.perform(post("/api/v1/processes/{processId}/recall", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Recalled"));
    }

    @Test
    void resumeProcess_success() throws Exception {
        UUID processId = UUID.randomUUID();
        ResumeProcessRequest request = new ResumeProcessRequest(UUID.randomUUID(), UUID.randomUUID());

        ProcessInstance mockProcess = createMockProcess();
        mockProcess.setStatus("InProgress");
        ProcessDto mockDto = createMockProcessDto(mockProcess);

        when(processService.resume(eq(processId), any(), any())).thenReturn(mockProcess);
        when(processMapper.toDto(mockProcess)).thenReturn(mockDto);

        mockMvc.perform(post("/api/v1/processes/{processId}/resume", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("InProgress"));
    }

    @Test
    void listProcesses_withFilters_success() throws Exception {
        ProcessInstance mockProcess = createMockProcess();
        ProcessDto mockDto = createMockProcessDto(mockProcess);
        Page<ProcessInstance> mockPage = new PageImpl<>(List.of(mockProcess));
        Page<ProcessDto> dtoPage = new PageImpl<>(List.of(mockDto));

        when(processRepository.findAll(any(Pageable.class))).thenReturn(mockPage);
        when(processMapper.toDto(mockProcess)).thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/processes")
                        .param("status", "InProgress")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(mockProcess.getId().toString()));
    }

    private ProcessInstance createMockProcess() {
        ProcessInstance process = ProcessInstance.builder()
                .id(UUID.randomUUID())
                .entityType("CONTRACT")
                .entityId(UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .status("Draft")
                .initiatorId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
        return process;
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
