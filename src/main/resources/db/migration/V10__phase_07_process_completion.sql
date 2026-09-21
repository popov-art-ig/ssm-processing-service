-- PHASE-07: Завершение процесса — guards и actions для активации следующего этапа и завершения процесса

-- Guards
insert into ssm_guard_registry (code, display_name, description, handler, scope, active, created_at, updated_at)
values
    ('AllStagesCompleted', 'All stages completed', 'Проверяет, что все этапы процесса находятся в завершённом статусе (lifecycle_status=COMPLETED)', 'allStagesCompletedGuard', 'GLOBAL', true, now(), now()),
    ('HasComments', 'Has comments', 'Проверяет наличие замечаний хотя бы в одном этапе процесса (stage.has_comments=true)', 'hasCommentsGuard', 'GLOBAL', true, now(), now());

-- Actions
insert into ssm_action_registry (code, display_name, description, handler, scope, active, created_at, updated_at)
values
    ('ActivateNextStage', 'Activate next stage', 'Активирует следующий этап: находит этап с orderIdx+1 и переводит его из Pending в Active через TransitionEngine', 'activateNextStageAction', 'GLOBAL', true, now(), now()),
    ('EvaluateProcessCompletion', 'Evaluate process completion', 'Оценивает завершённость процесса: если все этапы завершены, инициирует переход процесса через TransitionEngine', 'evaluateProcessCompletionAction', 'GLOBAL', true, now(), now()),
    ('CompleteProcess', 'Complete process', 'Устанавливает временную метку completedAt для процесса', 'completeProcessAction', 'GLOBAL', true, now(), now());
