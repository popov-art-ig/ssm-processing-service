package ru.coordination.approval.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.coordination.approval.api.dto.decision.DecideRequest;
import ru.coordination.approval.api.dto.decision.DecisionDto;
import ru.coordination.approval.api.dto.process.*;
import ru.coordination.approval.api.dto.template.*;
import ru.coordination.approval.domain.common.*;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.engine.TransitionEngine;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.locations=classpath:db/migration,classpath:db/test-migration"
)
@Testcontainers
@ActiveProfiles("test")
class ProcessApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("approval_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private TransitionEngine transitionEngine;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullCycle_createProcessFromTemplate_startProcess_decideParticipants_completeProcess() {
        UUID adminId = UUID.randomUUID();
        UUID initiatorId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();

        // 1. Создать и опубликовать шаблон напрямую через репозиторий
        Template template = createAndPublishTemplate(adminId, approverId);

        // 2. Создать процесс через API
        CreateProcessRequest createRequest = new CreateProcessRequest(
                "CONTRACT",
                null,
                UUID.randomUUID(),
                Map.of("amount", 1500000),
                initiatorId
        );

        ResponseEntity<ProcessDto> createResponse = restTemplate.postForEntity(
                "/api/v1/processes",
                createRequest,
                ProcessDto.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProcessDto process = createResponse.getBody();
        assertThat(process).isNotNull();
        assertThat(process.status()).isEqualTo("Draft");
        assertThat(process.stages()).hasSize(1);

        // 3. Запустить процесс
        StartProcessRequest startRequest = new StartProcessRequest(initiatorId);
        ResponseEntity<ProcessDto> startResponse = restTemplate.postForEntity(
                "/api/v1/processes/" + process.id() + "/start",
                startRequest,
                ProcessDto.class
        );

        assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProcessDto startedProcess = startResponse.getBody();
        assertThat(startedProcess).isNotNull();
        assertThat(startedProcess.status()).isEqualTo("InProgress");

        // 4. Получить первый этап
        StageDto firstStage = startedProcess.stages().get(0);
        assertThat(firstStage.status()).isEqualTo("Active");
        assertThat(firstStage.iterations()).hasSizeGreaterThan(0);

        // 5. Получить первого участника
        IterationDto firstIteration = firstStage.iterations().get(0);
        assertThat(firstIteration.participants()).hasSizeGreaterThan(0);
        ParticipantDto participant = firstIteration.participants().get(0);
        assertThat(participant.status()).isEqualTo("Assigned");

        // 6. Принять решение
        DecideRequest decideRequest = new DecideRequest("APPROVE", "LGTM", participant.userId());
        ResponseEntity<DecisionDto> decideResponse = restTemplate.postForEntity(
                "/api/v1/processes/" + process.id() +
                        "/stages/" + firstStage.id() +
                        "/participants/" + participant.id() + "/decide",
                decideRequest,
                DecisionDto.class
        );

        assertThat(decideResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        DecisionDto decision = decideResponse.getBody();
        assertThat(decision).isNotNull();
        assertThat(decision.decision()).isEqualTo("APPROVE");

        // 7. Проверить финальное состояние процесса
        ResponseEntity<ProcessDto> finalResponse = restTemplate.getForEntity(
                "/api/v1/processes/" + process.id(),
                ProcessDto.class
        );

        assertThat(finalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProcessDto finalProcess = finalResponse.getBody();
        assertThat(finalProcess).isNotNull();

        // Этап должен завершиться если это был единственный участник
        StageDto finalStage = finalProcess.stages().get(0);
        if (finalStage.iterations().get(0).participants().size() == 1) {
            assertThat(finalStage.status()).isIn("Approved", "Completed");
        }
    }

    @Test
    void getMyTasks_returnsAssignedParticipants() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/approval-view/tasks/my?userId=" + userId,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getMyProcesses_returnsUserProcesses() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/approval-view/processes/my?userId=" + userId,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Template createAndPublishTemplate(UUID adminId, UUID approverId) {
        Template template = Template.builder()
                .name("Test Contract Template")
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.PUBLISHED)
                .version(1)
                .createdAt(Instant.now())
                .createdBy(adminId)
                .updatedAt(Instant.now())
                .publishedAt(Instant.now())
                .build();

        // Сохранить через репозиторий
        return templateRepository.save(template);
    }
}
