package com.zhyf.workflow.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.workflow.application.OrderReviewCommand;
import com.zhyf.workflow.application.OrderReviewResult;
import com.zhyf.workflow.application.OrderReviewTaskService;
import com.zhyf.workflow.application.PrescriptionDispenseTaskService;
import com.zhyf.workflow.application.PrescriptionRecheckTaskService;
import com.zhyf.workflow.domain.WorkflowTaskSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/workflow")
public class WorkflowReviewTaskController {

    private final OrderReviewTaskService orderReviewTaskService;
    private final PrescriptionDispenseTaskService dispenseTaskService;
    private final PrescriptionRecheckTaskService recheckTaskService;

    public WorkflowReviewTaskController(
            OrderReviewTaskService orderReviewTaskService,
            PrescriptionDispenseTaskService dispenseTaskService,
            PrescriptionRecheckTaskService recheckTaskService
    ) {
        this.orderReviewTaskService = orderReviewTaskService;
        this.dispenseTaskService = dispenseTaskService;
        this.recheckTaskService = recheckTaskService;
    }

    @GetMapping("/review-tasks")
    public ApiResponse<List<WorkflowTaskSnapshot>> listReviewTasks() {
        return ApiResponse.ok(orderReviewTaskService.listPendingReviewTasks());
    }

    @PatchMapping("/review-tasks/{taskId}/approve")
    public ApiResponse<OrderReviewResult> approveReviewTask(
            @PathVariable UUID taskId,
            @RequestBody OrderReviewCommand command
    ) {
        return ApiResponse.ok(orderReviewTaskService.approve(taskId, command));
    }

    @PatchMapping("/review-tasks/{taskId}/reject")
    public ApiResponse<OrderReviewResult> rejectReviewTask(
            @PathVariable UUID taskId,
            @RequestBody OrderReviewCommand command
    ) {
        return ApiResponse.ok(orderReviewTaskService.reject(taskId, command));
    }

    @GetMapping("/dispense-tasks")
    public ApiResponse<List<WorkflowTaskSnapshot>> listDispenseTasks() {
        return ApiResponse.ok(dispenseTaskService.listPendingDispenseTasks());
    }

    @PatchMapping("/dispense-tasks/{taskId}/complete")
    public ApiResponse<OrderReviewResult> completeDispenseTask(
            @PathVariable UUID taskId,
            @RequestBody OrderReviewCommand command
    ) {
        return ApiResponse.ok(dispenseTaskService.complete(taskId, command));
    }

    @GetMapping("/recheck-tasks")
    public ApiResponse<List<WorkflowTaskSnapshot>> listRecheckTasks() {
        return ApiResponse.ok(recheckTaskService.listPendingRecheckTasks());
    }

    @PatchMapping("/recheck-tasks/{taskId}/complete")
    public ApiResponse<OrderReviewResult> completeRecheckTask(
            @PathVariable UUID taskId,
            @RequestBody OrderReviewCommand command
    ) {
        return ApiResponse.ok(recheckTaskService.complete(taskId, command));
    }
}
