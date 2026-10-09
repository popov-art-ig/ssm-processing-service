package ru.coordination.approval.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.coordination.approval.api.dto.decision.DecideRequest;
import ru.coordination.approval.api.dto.decision.DecisionDto;
import ru.coordination.approval.api.dto.process.*;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.template.SlotTemplate;
import ru.coordination.approval.domain.template.SlotType;
import ru.coordination.approval.domain.template.StageTemplate;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;

/**
 * Сквозной интеграционный тест REST API на реальном Postgres (Testcontainers).
 *
 * <p>Приведён в соответствие с фактическим API и Spring Boot 4.0:
 * <ul>
 *   <li>{@code TestRestTemplate} переехал в {@code spring-boot-resttestclient}
 *       ({@code org.springframework.boot.resttestclient});</li>
 *   <li>шаблон создаётся с этапом и слотом участника — иначе маршрут вырождается в процесс
 *       без этапов и {@code startProcess} нечего активировать;</li>
 *   <li>{@code CreateProcessRequest} больше не принимает {@code templateId} — шаблон
 *       подбирается {@code MatchService} по правилам применимости.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class ProcessApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("coordination_test")
            .withUsername("test")
            .withPassword("test");

    @LocalServerPort
    private int port;

    /**
     * Создаётся вручную, а не через {@code @AutoConfigureTestRestTemplate}: тот регистрирует
     * {@code WebDriverContextCustomizer}, который протекает в общий кэш контекстов и роняет
     * остальные {@code @SpringBootTest} (находка сборки).
     */
    private TestRestTemplate restTemplate;

    @BeforeEach
    void setUpRestTemplate() {
        restTemplate = new TestRestTemplate();
        restTemplate.getRestTemplate().setUriTemplateHandler(
                new org.springframework.web.util.DefaultUriBuilderFactory("http://localhost:" + port));
    }

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private ru.coordination.approval.service.ProcessService processService;

    @Test
    void fullCycle_createProcessFromTemplate_startProcess_decideParticipant() {
        UUID adminId = UUID.randomUUID();
        UUID initiatorId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();

        // 1. Создать и опубликовать шаблон напрямую через репозиторий
        Template template = createAndPublishTemplate(adminId, approverId);
        assertThat(template.getId()).isNotNull();

        // 2. Создать процесс через API — шаблон подбирается автоподбором (правил нет => universal)
        CreateProcessRequest createRequest = new CreateProcessRequest(
                "CONTRACT",
                null,
                UUID.randomUUID(),
                java.util.Map.of("amount", 1500000),
                initiatorId
        );

        ResponseEntity<ProcessDto> createResponse = restTemplate.postForEntity(
                "/api/v1/processes", createRequest, ProcessDto.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProcessDto process = createResponse.getBody();
        assertThat(process).isNotNull();
        assertThat(process.status()).isEqualTo("Draft");
        assertThat(process.stages()).hasSize(1);

        // 3. Запустить процесс
        ResponseEntity<ProcessDto> startResponse = restTemplate.postForEntity(
                "/api/v1/processes/" + process.id() + "/start",
                new StartProcessRequest(initiatorId),
                ProcessDto.class);

        if (!startResponse.getStatusCode().is2xxSuccessful()) {
            throw new AssertionError("startProcess returned " + startResponse.getStatusCode()
                    + " | processStatusAfter=" + processService.getById(process.id()).getStatus());
        }

        assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProcessDto startedProcess = startResponse.getBody();
        assertThat(startedProcess).isNotNull();
        assertThat(startedProcess.status()).isEqualTo("InProgress");

        // 4. Первый этап активирован, у него есть итерация и участник
        StageDto firstStage = startedProcess.stages().get(0);
        assertThat(firstStage.status()).isEqualTo("Active");
        assertThat(firstStage.iterations()).isNotEmpty();

        IterationDto firstIteration = firstStage.iterations().get(0);
        assertThat(firstIteration.participants()).isNotEmpty();
        ParticipantDto participant = firstIteration.participants().get(0);
        assertThat(participant.userId()).isEqualTo(approverId);
        assertThat(participant.status()).isEqualTo("Assigned");

        // 5. Принять решение
        ResponseEntity<DecisionDto> decideResponse = restTemplate.postForEntity(
                "/api/v1/processes/" + process.id()
                        + "/stages/" + firstStage.id()
                        + "/participants/" + participant.id() + "/decide",
                new DecideRequest("APPROVE", "LGTM", approverId),
                DecisionDto.class);

        assertThat(decideResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        DecisionDto decision = decideResponse.getBody();
        assertThat(decision).isNotNull();
        assertThat(decision.decision()).isEqualTo("APPROVE");

        // 6. Единственный участник единственного этапа => этап и процесс закрываются
        ResponseEntity<ProcessDto> finalResponse = restTemplate.getForEntity(
                "/api/v1/processes/" + process.id(), ProcessDto.class);

        assertThat(finalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProcessDto finalProcess = finalResponse.getBody();
        assertThat(finalProcess).isNotNull();
        assertThat(finalProcess.stages().get(0).status()).isEqualTo("Approved");
        assertThat(finalProcess.status()).isEqualTo("Approved");
    }

    @Test
    void getProcess_unknownId_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/processes/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getMyTasks_returnsOk() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/approval-view/tasks/my?userId=" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getMyProcesses_returnsOk() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/approval-view/processes/my?userId=" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Шаблон с одним обязательным этапом и одним слотом-участником, привязанным к конкретному
     * пользователю ({@code userId}, без {@code acceptableRoles}), — так
     * {@code RouteGenerationService} создаёт ровно одного участника.
     */
    private Template createAndPublishTemplate(UUID adminId, UUID approverId) {
        Instant now = Instant.now();

        Template template = Template.builder()
                .id(UUID.randomUUID())
                .name("Test Contract Template " + UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.PUBLISHED)
                .version(1)
                .processIterationEnabled(false)
                .requiresResponsibleApproval(false)
                .createdAt(now)
                .createdBy(adminId)
                .updatedAt(now)
                .publishedAt(now)
                .publishedBy(adminId)
                .stages(new ArrayList<>())
                .applicabilityRules(new ArrayList<>())
                .build();

        StageTemplate stage = StageTemplate.builder()
                .id(UUID.randomUUID())
                .template(template)
                .orderIdx(1)
                .name("Проверка")
                .stageType(StageType.APPROVAL)
                .duration(5)
                .decisionMode(DecisionMode.AND)
                .executionOrder(ExecutionOrder.PARALLEL)
                .mandatory(true)
                .orderMandatory(true)
                .allowedReturnStages(List.of(1))
                .createdAt(now)
                .actorSlots(new ArrayList<>())
                .build();

        SlotTemplate slot = SlotTemplate.builder()
                .id(UUID.randomUUID())
                .stageTemplate(stage)
                .slotType(SlotType.ACTOR)
                .orderIdx(1)
                .userId(approverId)
                .acceptableRoles(new ArrayList<>())
                .required(true)
                .createdAt(now)
                .build();

        stage.getActorSlots().add(slot);
        template.getStages().add(stage);

        return templateRepository.save(template);
    }
}
