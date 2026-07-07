package com.zhyf.logistics.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.logistics.application.LogisticsCommands;
import com.zhyf.logistics.application.LogisticsRecords;
import com.zhyf.logistics.application.LogisticsService;
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
@RequestMapping("/api/admin/logistics")
public class LogisticsAdminController {

    private final LogisticsService logisticsService;

    public LogisticsAdminController(LogisticsService logisticsService) {
        this.logisticsService = logisticsService;
    }

    @GetMapping("/orders/ready")
    public ApiResponse<List<LogisticsRecords.DeliveryOrderRecord>> listReadyOrders(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(logisticsService.listReadyOrders(limit));
    }

    @GetMapping("/shipments")
    public ApiResponse<List<LogisticsRecords.ShipmentRecord>> listShipments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(logisticsService.listShipments(status, orderNo, limit));
    }

    @PostMapping("/shipments/pack")
    public ApiResponse<LogisticsRecords.ShipmentRecord> pack(@RequestBody LogisticsCommands.PackCommand command) {
        return ApiResponse.ok(logisticsService.pack(command));
    }

    @PatchMapping("/shipments/{shipmentId}/ship")
    public ApiResponse<LogisticsRecords.ShipmentRecord> ship(
            @PathVariable UUID shipmentId,
            @RequestBody(required = false) LogisticsCommands.ShipmentActionCommand command
    ) {
        return ApiResponse.ok(logisticsService.ship(shipmentId, command));
    }

    @PatchMapping("/shipments/{shipmentId}/sign")
    public ApiResponse<LogisticsRecords.ShipmentRecord> sign(
            @PathVariable UUID shipmentId,
            @RequestBody(required = false) LogisticsCommands.ShipmentActionCommand command
    ) {
        return ApiResponse.ok(logisticsService.sign(shipmentId, command));
    }

    @GetMapping("/shipments/{shipmentId}/traces")
    public ApiResponse<List<LogisticsRecords.ShipmentTraceRecord>> listTraces(@PathVariable UUID shipmentId) {
        return ApiResponse.ok(logisticsService.listTraces(shipmentId));
    }

    @PostMapping("/shipments/trace")
    public ApiResponse<LogisticsRecords.ShipmentRecord> receiveTrace(
            @RequestBody LogisticsCommands.TraceCommand command
    ) {
        return ApiResponse.ok(logisticsService.receiveTrace(command));
    }
}
