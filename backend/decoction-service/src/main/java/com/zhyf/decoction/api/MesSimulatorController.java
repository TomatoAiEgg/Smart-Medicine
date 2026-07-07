package com.zhyf.decoction.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.decoction.application.DecoctionEventCommand;
import com.zhyf.decoction.application.DecoctionRecords;
import com.zhyf.decoction.application.DecoctionSimulatorService;
import com.zhyf.decoction.application.MesTaskOperationCommand;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulator/mes")
public class MesSimulatorController {

    private final DecoctionSimulatorService simulatorService;

    public MesSimulatorController(DecoctionSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @GetMapping("/tasks/pending")
    public ApiResponse<List<DecoctionRecords.DecoctionTaskRecord>> listPendingTasks() {
        return ApiResponse.ok(simulatorService.listPendingMesTasks());
    }

    @GetMapping("/tasks/active")
    public ApiResponse<List<DecoctionRecords.DecoctionTaskRecord>> listActiveTasks() {
        return ApiResponse.ok(simulatorService.listActiveMesTasks());
    }

    @PostMapping("/tasks/{taskNo}/start")
    public ApiResponse<DecoctionRecords.DecoctionTaskRecord> startTask(
            @PathVariable String taskNo,
            @RequestBody MesTaskOperationCommand command
    ) {
        return ApiResponse.ok(simulatorService.startMesTask(taskNo, command));
    }

    @PostMapping("/tasks/{taskNo}/finish")
    public ApiResponse<DecoctionRecords.DecoctionTaskRecord> finishTask(
            @PathVariable String taskNo,
            @RequestBody MesTaskOperationCommand command
    ) {
        return ApiResponse.ok(simulatorService.finishMesTask(taskNo, command));
    }

    @PostMapping("/tasks/{taskNo}/cancel")
    public ApiResponse<DecoctionRecords.DecoctionTaskRecord> cancelTask(
            @PathVariable String taskNo,
            @RequestBody DecoctionEventCommand command
    ) {
        return ApiResponse.ok(simulatorService.cancelMesTask(taskNo, command));
    }

    @PostMapping("/tasks/{taskNo}/terminate")
    public ApiResponse<DecoctionRecords.DecoctionTaskRecord> terminateTask(
            @PathVariable String taskNo,
            @RequestBody DecoctionEventCommand command
    ) {
        return ApiResponse.ok(simulatorService.terminateMesTask(taskNo, command));
    }

    @PostMapping("/tasks/{taskNo}/water-finish")
    public ApiResponse<DecoctionRecords.DecoctionTaskEventRecord> recordWaterFinished(
            @PathVariable String taskNo,
            @RequestBody DecoctionEventCommand command
    ) {
        return ApiResponse.ok(simulatorService.recordWaterFinished(taskNo, command));
    }

    @PostMapping("/tasks/{taskNo}/temperature")
    public ApiResponse<DecoctionRecords.DecoctionTaskEventRecord> recordTemperature(
            @PathVariable String taskNo,
            @RequestBody DecoctionEventCommand command
    ) {
        return ApiResponse.ok(simulatorService.recordTemperature(taskNo, command));
    }

    @PostMapping("/tasks/{taskNo}/error")
    public ApiResponse<DecoctionRecords.DecoctionTaskEventRecord> recordError(
            @PathVariable String taskNo,
            @RequestBody DecoctionEventCommand command
    ) {
        return ApiResponse.ok(simulatorService.recordError(taskNo, command));
    }

    @GetMapping("/tasks/{taskNo}/events")
    public ApiResponse<List<DecoctionRecords.DecoctionTaskEventRecord>> listTaskEvents(@PathVariable String taskNo) {
        return ApiResponse.ok(simulatorService.listTaskEvents(taskNo));
    }

    @GetMapping("/tasks/{taskNo}/work-records")
    public ApiResponse<List<DecoctionRecords.DeviceWorkRecord>> listDeviceWorkRecords(@PathVariable String taskNo) {
        return ApiResponse.ok(simulatorService.listDeviceWorkRecords(taskNo));
    }
}
