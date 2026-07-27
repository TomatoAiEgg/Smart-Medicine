package com.zhyf.logistics.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.logistics.application.LogisticsCommands;
import com.zhyf.logistics.application.LogisticsRecords;
import com.zhyf.logistics.application.LogisticsService;
import com.zhyf.logistics.application.LogisticsShipmentQuery;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

    private static final ZoneId DEFAULT_QUERY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LEGACY_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogisticsService logisticsService;

    public LogisticsAdminController(LogisticsService logisticsService) {
        this.logisticsService = logisticsService;
    }

    @GetMapping("/orders/ready")
    public ApiResponse<List<LogisticsRecords.DeliveryOrderRecord>> listReadyOrders(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String patientName,
            @RequestParam(required = false) String receiverName,
            @RequestParam(required = false) String receiverPhone,
            @RequestParam(required = false) String hospitalType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) String logisticsCompany,
            @RequestParam(required = false) String logisticsNo,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(logisticsService.listReadyOrders(query(
                startTime,
                endTime,
                institution,
                orderNo,
                patientName,
                receiverName,
                receiverPhone,
                hospitalType,
                status,
                deliveryType,
                logisticsCompany,
                logisticsNo,
                limit
        )));
    }

    @GetMapping("/shipments")
    public ApiResponse<List<LogisticsRecords.ShipmentRecord>> listShipments(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String patientName,
            @RequestParam(required = false) String receiverName,
            @RequestParam(required = false) String receiverPhone,
            @RequestParam(required = false) String hospitalType,
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) String logisticsCompany,
            @RequestParam(required = false) String logisticsNo,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(logisticsService.listShipments(query(
                startTime,
                endTime,
                institution,
                orderNo,
                patientName,
                receiverName,
                receiverPhone,
                hospitalType,
                status,
                deliveryType,
                logisticsCompany,
                logisticsNo,
                limit
        )));
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

    private LogisticsShipmentQuery query(
            String startTime,
            String endTime,
            String institution,
            String orderNo,
            String patientName,
            String receiverName,
            String receiverPhone,
            String hospitalType,
            String status,
            String deliveryType,
            String logisticsCompany,
            String logisticsNo,
            int limit
    ) {
        return new LogisticsShipmentQuery(
                parseQueryTime(startTime),
                parseQueryTime(endTime),
                institution,
                orderNo,
                patientName,
                receiverName,
                receiverPhone,
                hospitalType,
                status,
                deliveryType,
                logisticsCompany,
                logisticsNo,
                limit
        );
    }

    private Instant parseQueryTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // 兼容老后台查询框的本地时间格式。
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
            // 兼容老后台查询框的本地时间格式。
        }
        try {
            return LocalDateTime.parse(trimmed, LEGACY_DATE_TIME_FORMATTER)
                    .atZone(DEFAULT_QUERY_ZONE)
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(trimmed)
                        .atZone(DEFAULT_QUERY_ZONE)
                        .toInstant();
            } catch (DateTimeParseException ex) {
                throw new BusinessException("INVALID_QUERY_TIME", "时间格式不正确，应为 yyyy-MM-dd HH:mm:ss");
            }
        }
    }
}
