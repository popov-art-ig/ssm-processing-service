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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.coordination.approval.api.dto.decision.DecideRequest;
import ru.coordination.approval.api.dto.decision.DecisionDto;
import ru.coordination.approval.api.mapper.DecisionMapper;
import ru.coordination.approval.api.service.DecisionService;
import ru.coordination.approval.domain.process.Decision;

@WebMvcTest(DecisionController.class)
class DecisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DecisionService decisionService;

    @MockBean
    private DecisionMapper decisionMapper;

    @Test
    void decide_success() throws Exception {
        UUID processId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        DecideRequest request = new DecideRequest(
                "APPROVE",
                "Looks good",
                UUID.randomUUID()
        );

        Decision mockDecision = createMockDecision(participantId);
        DecisionDto mockDto = createMockDecisionDto(mockDecision);

        when(decisionService.decide(eq(processId), eq(stageId), eq(participantId), any(), any(), any()))
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

        DecideRequest request = new DecideRequest(
                "",  // пустое решение
                "Comment",
                UUID.randomUUID()
        );

        mockMvc.perform(post("/api/v1/processes/{processId}/stages/{stageId}/participants/{participantId}/decide",
                        processId, stageId, participantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDecision_success() throws Exception {
        UUID participantId = UUID.randomUUID();

        Decision mockDecision = createMockDecision(participantId);
        DecisionDto mockDto = createMockDecisionDto(mockDecision);

        when(decisionService.getByParticipantId(participantId)).thenReturn(mockDecision);
        when(decisionMapper.toDto(mockDecision)).thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/processes/{processId}/stages/{stageId}/participants/{participantId}/decision",
                        UUID.randomUUID(), UUID.randomUUID(), participantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantId").value(participantId.toString()));
    }

    @Test
    void listDecisions_success() throws Exception {
        UUID processId = UUID.randomUUID();

        Decision mockDecision = createMockDecision(UUID.randomUUID());
        DecisionDto mockDto = createMockDecisionDto(mockDecision);

        when(decisionService.listByProcessId(processId)).thenReturn(List.of(mockDecision));
        when(decisionMapper.toDto(mockDecision)).thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/processes/{processId}/decisions", processId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].decision").value("APPROVE"));
    }

    private Decision createMockDecision(UUID participantId) {
        return Decision.builder()
                .id(UUID.randomUUID())
                .decision("APPROVE")
                .comment("Looks good")
                .decidedAt(Instant.now())
                .decidedBy(UUID.randomUUID())
                .build();
    }

    private DecisionDto createMockDecisionDto(Decision decision) {
        return new DecisionDto(
                decision.getId(),
                UUID.randomUUID(),
                decision.getDecision(),
                decision.getComment(),
                decision.getDecidedAt(),
                decision.getDecidedBy()
        );
    }
}
