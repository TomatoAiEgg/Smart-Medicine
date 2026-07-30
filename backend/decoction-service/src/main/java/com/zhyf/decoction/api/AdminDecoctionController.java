package com.zhyf.decoction.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.decoction.application.DecoctionDeviceCommand;
import com.zhyf.decoction.application.DecoctionRecords;
import com.zhyf.decoction.application.DecoctionSimulatorService;
import com.zhyf.decoction.application.WaterPailCommand;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/decoction")
public class AdminDecoctionController {

    private final DecoctionSimulatorService simulatorService;

    public AdminDecoctionController(DecoctionSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @GetMapping("/devices")
    public ApiResponse<List<DecoctionRecords.DeviceRecord>> listDevices() {
        return ApiResponse.ok(simulatorService.listDevices());
    }

    @PostMapping("/devices")
    public ApiResponse<DecoctionRecords.DeviceRecord> createDevice(@RequestBody DecoctionDeviceCommand command) {
        return ApiResponse.ok(simulatorService.createDevice(command));
    }

    @PatchMapping("/devices/{deviceCode}")
    public ApiResponse<DecoctionRecords.DeviceRecord> updateDevice(
            @PathVariable String deviceCode,
            @RequestBody DecoctionDeviceCommand command
    ) {
        return ApiResponse.ok(simulatorService.updateDevice(deviceCode, command));
    }

    @GetMapping("/water-pails")
    public ApiResponse<List<DecoctionRecords.WaterPailRecord>> listWaterPails() {
        return ApiResponse.ok(simulatorService.listWaterPails());
    }

    @PostMapping("/water-pails")
    public ApiResponse<DecoctionRecords.WaterPailRecord> createWaterPail(@RequestBody WaterPailCommand command) {
        return ApiResponse.ok(simulatorService.createWaterPail(command));
    }

    @PatchMapping("/water-pails/{pailNo}")
    public ApiResponse<DecoctionRecords.WaterPailRecord> updateWaterPail(
            @PathVariable String pailNo,
            @RequestBody WaterPailCommand command
    ) {
        return ApiResponse.ok(simulatorService.updateWaterPail(pailNo, command));
    }
}
