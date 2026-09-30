package ru.coordination.approval.api.dto.admin;

import java.util.UUID;

public record RestoreProcessRequest(
        UUID actorId
) {
}
