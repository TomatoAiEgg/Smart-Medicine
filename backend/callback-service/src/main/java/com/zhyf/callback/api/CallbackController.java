package com.zhyf.callback.api;

import com.zhyf.callback.application.CallbackCreateCommand;
import com.zhyf.callback.application.CallbackRecords;
import com.zhyf.callback.application.CallbackService;
import com.zhyf.common.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CallbackController {

    private final CallbackService callbackService;

    public CallbackController(CallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @PostMapping("/internal/callback-records")
    public ApiResponse<CallbackRecords.CallbackRecord> createCallback(@RequestBody CallbackCreateCommand command) {
        return ApiResponse.ok(callbackService.createCallback(command));
    }

    @GetMapping("/api/admin/callback-records")
    public ApiResponse<List<CallbackRecords.CallbackRecord>> listRecords(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String callbackType,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(callbackService.listRecords(status, callbackType, orderId, limit));
    }

    @PatchMapping("/api/admin/callback-records/{id}/mark-success")
    public ApiResponse<CallbackRecords.CallbackRecord> markSucceeded(
            @PathVariable UUID id,
            @RequestBody(required = false) CallbackManualCommand command
    ) {
        return ApiResponse.ok(callbackService.markSucceeded(id, command == null ? null : command.responseBody()));
    }

    @PatchMapping("/api/admin/callback-records/{id}/mark-failed")
    public ApiResponse<CallbackRecords.CallbackRecord> markFailed(
            @PathVariable UUID id,
            @RequestBody(required = false) CallbackManualCommand command
    ) {
        return ApiResponse.ok(callbackService.markFailed(id, command == null ? null : command.responseBody()));
    }

    @PatchMapping("/api/admin/callback-records/{id}/replay")
    public ApiResponse<CallbackRecords.CallbackRecord> replay(@PathVariable UUID id) {
        return ApiResponse.ok(callbackService.replay(id));
    }

    public record CallbackManualCommand(String responseBody) {
    }
}
