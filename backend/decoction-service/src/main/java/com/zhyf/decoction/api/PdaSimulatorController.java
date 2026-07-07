package com.zhyf.decoction.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.decoction.application.DecoctionRecords;
import com.zhyf.decoction.application.DecoctionSimulatorService;
import com.zhyf.decoction.application.PdaLoginCommand;
import com.zhyf.decoction.application.SimulatorOperationCommand;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulator/pda")
public class PdaSimulatorController {

    private final DecoctionSimulatorService simulatorService;

    public PdaSimulatorController(DecoctionSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @PostMapping("/login")
    public ApiResponse<DecoctionRecords.PdaLoginResult> login(@RequestBody PdaLoginCommand command) {
        return ApiResponse.ok(simulatorService.login(command));
    }

    @GetMapping("/prescriptions/can-operate")
    public ApiResponse<List<DecoctionRecords.PrescriptionRecord>> listCanOperatePrescriptions(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(simulatorService.listCanOperatePrescriptions(limit));
    }

    @GetMapping("/decoction/devices")
    public ApiResponse<List<DecoctionRecords.DeviceRecord>> listDevices() {
        return ApiResponse.ok(simulatorService.listDevices());
    }

    @PostMapping("/bind-prescription")
    public ApiResponse<DecoctionRecords.DecoctionTaskRecord> bindPrescription(
            @RequestBody SimulatorOperationCommand command
    ) {
        return ApiResponse.ok(simulatorService.bindPrescription(command));
    }

    @PostMapping("/decoction/start")
    public ApiResponse<DecoctionRecords.DecoctionTaskRecord> startDecoction(
            @RequestBody SimulatorOperationCommand command
    ) {
        return ApiResponse.ok(simulatorService.startDecoction(command));
    }

    @PostMapping("/decoction/finish")
    public ApiResponse<DecoctionRecords.DecoctionTaskRecord> finishDecoction(
            @RequestBody SimulatorOperationCommand command
    ) {
        return ApiResponse.ok(simulatorService.finishDecoction(command));
    }

    @PostMapping("/decoction/cancel")
    public ApiResponse<DecoctionRecords.DecoctionTaskRecord> cancelDecoction(
            @RequestBody SimulatorOperationCommand command
    ) {
        return ApiResponse.ok(simulatorService.cancelDecoction(command));
    }

    @PostMapping("/decoction/terminate")
    public ApiResponse<DecoctionRecords.DecoctionTaskRecord> terminateDecoction(
            @RequestBody SimulatorOperationCommand command
    ) {
        return ApiResponse.ok(simulatorService.terminateDecoction(command));
    }
}
