package com.zhyf.order.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.order.application.AdminExportTaskBatchRunResult;
import com.zhyf.order.application.AdminExportTaskFile;
import com.zhyf.order.application.AdminExportTaskPage;
import com.zhyf.order.application.AdminExportTaskQuery;
import com.zhyf.order.application.AdminExportTaskRecord;
import com.zhyf.order.application.AdminExportTaskService;
import com.zhyf.order.application.AdminOrderSearchQuery;
import com.zhyf.order.application.AdminOrderWarehouseQuery;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/export-tasks")
public class AdminExportTaskController {

    private static final ZoneId DEFAULT_QUERY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LEGACY_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminExportTaskService exportTaskService;

    public AdminExportTaskController(AdminExportTaskService exportTaskService) {
        this.exportTaskService = exportTaskService;
    }

    @GetMapping
    public ApiResponse<AdminExportTaskPage> listTasks(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(exportTaskService.listTasks(new AdminExportTaskQuery(
                taskType,
                taskStatus,
                keyword,
                page,
                pageSize
        )));
    }

    @PostMapping("/orders")
    public ApiResponse<AdminExportTaskRecord> createOrderExportTask(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) String prescriptionType,
            @RequestParam(required = false) String hospitalType,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String excludeOrderStatus,
            @RequestParam(required = false) String decoctionCenter,
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) String logisticsCompany,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String hospitalPrescriptionNo,
            @RequestParam(required = false) String patientName,
            @RequestParam(required = false) String receiverPhone,
            @RequestParam(defaultValue = "admin") String requestedBy,
            @RequestParam(defaultValue = "true") boolean runImmediately
    ) {
        Map<String, String> queryParam = queryParam();
        putQueryParam(queryParam, "startTime", startTime);
        putQueryParam(queryParam, "endTime", endTime);
        putQueryParam(queryParam, "institution", institution);
        putQueryParam(queryParam, "prescriptionType", prescriptionType);
        putQueryParam(queryParam, "hospitalType", hospitalType);
        putQueryParam(queryParam, "orderStatus", orderStatus);
        putQueryParam(queryParam, "excludeOrderStatus", excludeOrderStatus);
        putQueryParam(queryParam, "decoctionCenter", decoctionCenter);
        putQueryParam(queryParam, "deliveryType", deliveryType);
        putQueryParam(queryParam, "logisticsCompany", logisticsCompany);
        putQueryParam(queryParam, "province", province);
        putQueryParam(queryParam, "keyword", keyword);
        putQueryParam(queryParam, "hospitalPrescriptionNo", hospitalPrescriptionNo);
        putQueryParam(queryParam, "patientName", patientName);
        putQueryParam(queryParam, "receiverPhone", receiverPhone);
        AdminOrderSearchQuery query = new AdminOrderSearchQuery(
                parseQueryTime(startTime),
                parseQueryTime(endTime),
                institution,
                prescriptionType,
                hospitalType,
                orderStatus,
                excludeOrderStatus,
                decoctionCenter,
                deliveryType,
                logisticsCompany,
                province,
                keyword,
                hospitalPrescriptionNo,
                patientName,
                receiverPhone,
                1,
                5000
        );
        return ApiResponse.ok(exportTaskService.createOrderExportTask(
                query,
                queryParam,
                requestedBy,
                runImmediately
        ));
    }

    @PostMapping("/order-warehouses")
    public ApiResponse<AdminExportTaskRecord> createOrderWarehouseExportTask(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) String prescriptionType,
            @RequestParam(required = false) String hospitalType,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String decoctionCenter,
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) String logisticsCompany,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String prescriptionNo,
            @RequestParam(required = false) String hospitalPrescriptionNo,
            @RequestParam(required = false) String patientName,
            @RequestParam(required = false) String receiverPhone,
            @RequestParam(required = false) String nodeTime,
            @RequestParam(defaultValue = "admin") String requestedBy,
            @RequestParam(defaultValue = "true") boolean runImmediately
    ) {
        Map<String, String> queryParam = queryParam();
        putQueryParam(queryParam, "startTime", startTime);
        putQueryParam(queryParam, "endTime", endTime);
        putQueryParam(queryParam, "institution", institution);
        putQueryParam(queryParam, "prescriptionType", prescriptionType);
        putQueryParam(queryParam, "hospitalType", hospitalType);
        putQueryParam(queryParam, "orderStatus", orderStatus);
        putQueryParam(queryParam, "decoctionCenter", decoctionCenter);
        putQueryParam(queryParam, "deliveryType", deliveryType);
        putQueryParam(queryParam, "logisticsCompany", logisticsCompany);
        putQueryParam(queryParam, "province", province);
        putQueryParam(queryParam, "orderNo", orderNo);
        putQueryParam(queryParam, "prescriptionNo", prescriptionNo);
        putQueryParam(queryParam, "hospitalPrescriptionNo", hospitalPrescriptionNo);
        putQueryParam(queryParam, "patientName", patientName);
        putQueryParam(queryParam, "receiverPhone", receiverPhone);
        putQueryParam(queryParam, "nodeTime", nodeTime);
        AdminOrderWarehouseQuery query = new AdminOrderWarehouseQuery(
                parseQueryTime(startTime),
                parseQueryTime(endTime),
                institution,
                prescriptionType,
                hospitalType,
                orderStatus,
                decoctionCenter,
                deliveryType,
                logisticsCompany,
                province,
                orderNo,
                prescriptionNo,
                hospitalPrescriptionNo,
                patientName,
                receiverPhone,
                nodeTime,
                1,
                5000
        );
        return ApiResponse.ok(exportTaskService.createOrderWarehouseExportTask(
                query,
                queryParam,
                requestedBy,
                runImmediately
        ));
    }

    @PostMapping("/run-pending")
    public ApiResponse<AdminExportTaskBatchRunResult> runPendingTasks(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(exportTaskService.runPendingTasks(limit));
    }

    @PostMapping("/{taskId}/run")
    public ApiResponse<AdminExportTaskRecord> runTask(@PathVariable UUID taskId) {
        return ApiResponse.ok(exportTaskService.runTask(taskId));
    }

    @GetMapping("/{taskId}/file")
    public ResponseEntity<byte[]> downloadTaskFile(@PathVariable UUID taskId) {
        AdminExportTaskFile file = exportTaskService.getTaskFile(taskId);
        String filename = StringUtils.hasText(file.fileName()) ? file.fileName() : "export-task.csv";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType(
                        StringUtils.hasText(file.contentType()) ? file.contentType() : "text/csv;charset=UTF-8"
                ))
                .body(file.fileContent());
    }

    private Map<String, String> queryParam() {
        return new LinkedHashMap<>();
    }

    private void putQueryParam(Map<String, String> values, String key, String value) {
        if (StringUtils.hasText(value)) {
            values.put(key, value.trim());
        }
    }

    private Instant parseQueryTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // 兼容老后台时间格式。
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
            // 兼容老后台时间格式。
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
