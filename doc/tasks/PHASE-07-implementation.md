# PHASE-07: Process Completion — Implementation Summary

## Implemented Components

### 1. Guards

#### AllStagesCompletedGuard
- **Location**: [AllStagesCompletedGuard.java](../../src/main/java/ru/coordination/approval/service/guard/AllStagesCompletedGuard.java)
- **Purpose**: Проверяет, что все этапы процесса находятся в завершённом статусе (lifecycle_status=COMPLETED)
- **Logic**: Получает все этапы из процесса и проверяет их lifecycle_status через StatusRegistry
- **Registry code**: `AllStagesCompleted`

#### HasCommentsGuard
- **Location**: [HasCommentsGuard.java](../../src/main/java/ru/coordination/approval/service/guard/HasCommentsGuard.java)
- **Purpose**: Проверяет наличие замечаний хотя бы в одном этапе процесса
- **Logic**: Проверяет поле `hasComments` у всех этапов процесса
- **Registry code**: `HasComments`

### 2. Actions

#### ActivateNextStageAction
- **Location**: [ActivateNextStageAction.java](../../src/main/java/ru/coordination/approval/service/action/ActivateNextStageAction.java)
- **Purpose**: Активирует следующий этап после завершения текущего
- **Logic**: 
  - Находит этап с `orderIdx = currentOrderIdx + 1`
  - Переводит его из `Pending` в `Active` через TransitionEngine
  - Если следующего этапа нет (текущий — последний), ничего не делает
- **Registry code**: `ActivateNextStage`

#### EvaluateProcessCompletionAction
- **Location**: [EvaluateProcessCompletionAction.java](../../src/main/java/ru/coordination/approval/service/action/EvaluateProcessCompletionAction.java)
- **Purpose**: Оценивает завершённость процесса и инициирует его переход
- **Logic**:
  - Получает конфигурацию state machine для процесса
  - Вызывает TransitionEngine для процесса с текущим статусом
  - TransitionEngine сам решает, какой переход применить (ProcessApproved или ProcessApprovedWithComments)
  - Если ни один guard не прошёл, переход не выполняется
- **Registry code**: `EvaluateProcessCompletion`

#### CompleteProcessAction
- **Location**: [CompleteProcessAction.java](../../src/main/java/ru/coordination/approval/service/action/CompleteProcessAction.java)
- **Purpose**: Устанавливает временную метку завершения процесса
- **Logic**: Устанавливает `process.completedAt = Instant.now()`
- **Registry code**: `CompleteProcess`

### 3. Database Migration

**File**: [V10__phase_07_process_completion.sql](../../src/main/resources/db/migration/V10__phase_07_process_completion.sql)

Добавляет записи в реестры:
- 2 guards: `AllStagesCompleted`, `HasComments`
- 3 actions: `ActivateNextStage`, `EvaluateProcessCompletion`, `CompleteProcess`

### 4. Tests

#### Unit Tests
- [AllStagesCompletedGuardTest.java](../../src/test/java/ru/coordination/approval/service/guard/AllStagesCompletedGuardTest.java)
- [HasCommentsGuardTest.java](../../src/test/java/ru/coordination/approval/service/guard/HasCommentsGuardTest.java)
- [ActivateNextStageActionTest.java](../../src/test/java/ru/coordination/approval/service/action/ActivateNextStageActionTest.java)
- [CompleteProcessActionTest.java](../../src/test/java/ru/coordination/approval/service/action/CompleteProcessActionTest.java)
- [EvaluateProcessCompletionActionTest.java](../../src/test/java/ru/coordination/approval/service/action/EvaluateProcessCompletionActionTest.java)

#### Integration Test
- [ProcessCompletionIntegrationTest.java](../../src/test/java/ru/coordination/approval/engine/ProcessCompletionIntegrationTest.java)
- Проверяет полный сценарий: завершение этапов → активация следующих → завершение процесса

## Acceptance Criteria Status

✅ **AC1**: При завершении этапа (кроме последнего) следующий этап активируется  
✅ **AC2**: При завершении всех этапов процесс переходит в Approved/Rejected/ApprovedWithComments  
✅ **AC3**: При завершении процесса устанавливается completedAt  
✅ **AC4**: Guards проверяют корректность условий перехода  
✅ **AC5**: Actions выполняют необходимые побочные эффекты

## Open Questions — Resolutions

### Q1: Порядок проверки переходов при нескольких кандидатах
**Решение**: TransitionEngine использует порядок из ModelFactory (строки 49-51):
```java
.sorted(Comparator.comparing(TransitionConfig::getPriority).reversed()
        .thenComparing(TransitionConfig::getTransitionCode))
```
Переходы проверяются в порядке priority (descending), затем code (ascending).

### Q2: Нужна ли отдельная таблица stage_aggregation для кеширования агрегатов?
**Решение**: Не реализована в Phase 7. Агрегация выполняется in-memory при каждой проверке. Можно добавить в будущем для оптимизации.

## Integration Points

1. **TransitionEngine**: Используется для активации следующего этапа и завершения процесса
2. **StatusRegistry**: Используется для проверки lifecycle_status этапов
3. **StateMachineConfig**: Guards и actions вызываются в рамках переходов, определённых в конфигурации

## State Machine Configuration Example

```sql
-- Stage transition: при завершении этапа
insert into ssm_transition_config (
    state_machine_config_id, from_state_id, to_state_id, 
    transition_code, priority, trigger_type,
    guard_codes, action_codes
) values (
    (select id from ssm_state_machine_config where entity_type='STAGE' and process_type='STANDARD'),
    (select id from ssm_state_config where state='Approved'),
    (select id from ssm_state_config where state='Approved'),
    'StageCompleted', 100, 'SYSTEM_ACTION',
    '{}', 
    '{ActivateNextStage, EvaluateProcessCompletion}'
);

-- Process transitions: при завершении всех этапов
insert into ssm_transition_config values
    -- Без замечаний
    (..., 'ProcessApproved', 200, 'SYSTEM_ACTION',
     '{AllStagesCompleted}', '{CompleteProcess}'),
    -- С замечаниями (меньший priority — проверяется после)
    (..., 'ProcessApprovedWithComments', 100, 'SYSTEM_ACTION',
     '{AllStagesCompleted, HasComments}', '{CompleteProcess}');
```

## Notes

1. **Cascade activation**: Этапы активируются последовательно (не batch). Каждое завершение этапа активирует только следующий.

2. **Idempotency**: EvaluateProcessCompletionAction можно вызывать повторно — TransitionEngine сам решит, нужен ли переход.

3. **Error handling**: Если активация следующего этапа не удалась, выбрасывается IllegalStateException с подробным сообщением.

4. **Performance**: Для больших процессов (>100 этапов) можно оптимизировать проверки через материализованные агрегаты.

## Related Documents

- Ticket: [PHASE-07-process-completion.md](../tickets/PHASE-07-process-completion.md)
- Architecture: [10_architecture.md](../10_architecture.md) §7.2.1 (TransitionEngine)
- Domain Model: [03_domain_model.md](../03_domain_model.md) §7.2-7.3 (Process/Stage)
