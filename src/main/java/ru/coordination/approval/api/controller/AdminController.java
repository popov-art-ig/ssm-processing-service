package ru.coordination.approval.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.coordination.approval.api.dto.process.ProcessDto;
import ru.coordination.approval.api.dto.process.RestoreProcessRequest;
import ru.coordination.approval.api.exception.ErrorResponse;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "API для административных операций")
public class AdminController {

    @PostMapping("/processes/{processId}/restore")
    @Operation(summary = "Восстановить процесс из архива",
               description = "Восстанавливает архивированный процесс в предыдущий статус")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Процесс восстановлен",
                     content = @Content(schema = @Schema(implementation = ProcessDto.class))),
        @ApiResponse(responseCode = "404", description = "Процесс не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Процесс не находится в архиве",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProcessDto> restoreProcess(
            @Parameter(description = "ID процесса", required = true)
            @PathVariable UUID processId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ID актора, восстанавливающего процесс")
            @Valid @RequestBody RestoreProcessRequest request) {
        // TODO: Implement process restoration from archive
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
