package ru.coordination.approval.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.coordination.approval.api.dto.decision.DecideRequest;
import ru.coordination.approval.api.dto.decision.DecisionDto;
import ru.coordination.approval.api.exception.ErrorResponse;
import ru.coordination.approval.api.exception.GlobalExceptionHandler;
import ru.coordination.approval.api.mapper.DecisionMapper;
import ru.coordination.approval.api.service.DecisionQueryService;
import ru.coordination.approval.domain.process.Decision;
import ru.coordination.approval.domain.process.Participant;

/**
 * Web-срез тестов для решений участников.
 *
 * <p>Ранее тест не компилировался: импортировал {@code api.service.DecisionService}
 * (не существовал), {@code @MockBean} и {@code @WebMvcTest} из удалённых/переехавших в
 * Spring Boot 4.0 пакетов, а билдер {@link Decision} вызывал несуществующие методы
 * {@code decision()}/{@code decidedAt()}/{@code decidedBy()} — это поля
 * {@link Participant}, а не {@link Decision}.
 */
@WebMvcTest({DecisionController.class, ProcessDecisionController.class})
@Import(GlobalExceptionHandler.class)
class DecisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Spring Boot 4: срез @WebMvcTest больше не предоставляет Jackson 2 ObjectMapper как бин.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private DecisionQueryService decisionQueryService;

    @MockitoBean
    private DecisionMapper decisionMapper;

    @Test
    void decide_success() throws Exception {
        UUID processId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        DecideRequest request = new DecideRequest("APPROVE", "Looks good", UUID.randomUUID());

        Decision mockDecision = createMockDecision(participantId, "APPROVE", "Looks good");
        DecisionDto mockDto = createMockDecisionDto(mockDecision, participantId);

        when(decisionQueryService.decide(
                eq(processId), eq(stageId), eq(participantId), any(), any(), any()))
                .thenReturn(mockDecision);
        when(decisionMapper.toDto(mockDecision)).thenReturn(mockDto);

        mockMvc.perform(post("/api/v1/processes/{processId}/stages/{stageId}/participants/{participantId}/decide",
                        processId, stageId, participantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"));
    }

    @Test
    void decide_invalidDecision_returns400() throws Exception {
        UUID processId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        DecideRequest request = new DecideRequest("", "Comment", UUID.randomUUID());

        mockMvc.perform(post("/api/v1/processes/{processId}/stages/{stageId}/participants/{participantId}/decide",
                        processId, stageId, participantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDecision_success() throws Exception {
        UUID participantId = UUID.randomUUID();

        Decision mockDecision = createMockDecision(participantId, "APPROVE", "Looks good");
        DecisionDto mockDto = createMockDecisionDto(mockDecision, participantId);

        when(decisionQueryService.getByParticipantId(participantId)).thenReturn(mockDecision);
        when(decisionMapper.toDto(mockDecision)).thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/processes/{processId}/stages/{stageId}/participants/{participantId}/decision",
                        UUID.randomUUID(), UUID.randomUUID(), participantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantId").value(participantId.toString()));
    }

    @Test
    void listDecisions_success() throws Exception {
        UUID processId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        Decision mockDecision = createMockDecision(participantId, "APPROVE", "Looks good");
        DecisionDto mockDto = createMockDecisionDto(mockDecision, participantId);

        when(decisionQueryService.listByProcessId(processId)).thenReturn(List.of(mockDecision));
        when(decisionMapper.toDto(mockDecision)).thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/processes/{processId}/decisions", processId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].decision").value("APPROVE"));
    }

    private Decision createMockDecision(UUID participantId, String result, String comment) {
        Participant participant = Participant.builder()
                .id(participantId)
                .userId(UUID.randomUUID())
                .status("Decided")
                .build();

        return Decision.builder()
                .id(UUID.randomUUID())
                .participant(participant)
                .result(result)
                .comment(comment)
                .auto(false)
                .recordedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }

    private DecisionDto createMockDecisionDto(Decision decision, UUID participantId) {
        return new DecisionDto(
                decision.getId(),
                participantId,
                decision.getResult(),
                decision.getComment(),
                decision.getRecordedAt(),
                null
        );
    }
}
