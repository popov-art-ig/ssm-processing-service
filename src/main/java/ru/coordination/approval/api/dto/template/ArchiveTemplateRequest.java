package ru.coordination.approval.api.dto.template;

import java.util.UUID;

/**
 * Тело запроса на архивацию шаблона ({@code 07_api_contract.md} §10.8) — переход
 * {@code Deprecated → Archived}, который не пройдёт, пока на шаблоне есть активные процессы
 * (guard {@code NoActiveProcesses}).
 *
 * @param actorId кто архивирует; может быть {@code null}, потому что отдельного актора для
 *                архивации контракт не требует
 */
public record ArchiveTemplateRequest(
        UUID actorId
) {
}
