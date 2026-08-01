package com.zhyf.order.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.order.infrastructure.AdminExportTaskRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminExportTaskService {

    public static final String TASK_TYPE_ADMIN_ORDERS = "ADMIN_ORDERS";
    public static final String TASK_TYPE_ADMIN_ORDER_WAREHOUSES = "ADMIN_ORDER_WAREHOUSES";

    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";
    private static final ZoneId DEFAULT_QUERY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LEGACY_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminExportTaskRepository exportTaskRepository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public AdminExportTaskService(
            AdminExportTaskRepository exportTaskRepository,
            OrderService orderService,
            ObjectMapper objectMapper
    ) {
        this.exportTaskRepository = exportTaskRepository;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    public AdminExportTaskPage listTasks(AdminExportTaskQuery query) {
        AdminExportTaskQuery normalized = new AdminExportTaskQuery(
                cleanText(query.taskType()),
                cleanText(query.taskStatus()),
                cleanText(query.keyword()),
                Math.max(query.page(), 1),
                Math.min(Math.max(query.pageSize(), 1), 100)
        );
        return exportTaskRepository.searchTasks(DEFAULT_TENANT_ID, normalized);
    }

    @Transactional
    public AdminExportTaskRecord createOrderExportTask(
            AdminOrderSearchQuery query,
            Map<String, String> queryParam,
            String requestedBy,
            boolean runImmediately
    ) {
        AdminExportTaskRecord task = createTask(
                TASK_TYPE_ADMIN_ORDERS,
                "订单信息汇总导出",
                queryParam,
                requestedBy
        );
        if (runImmediately) {
            return runTask(task.taskId());
        }
        return task;
    }

    @Transactional
    public AdminExportTaskRecord createOrderWarehouseExportTask(
            AdminOrderWarehouseQuery query,
            Map<String, String> queryParam,
            String requestedBy,
            boolean runImmediately
    ) {
        AdminExportTaskRecord task = createTask(
                TASK_TYPE_ADMIN_ORDER_WAREHOUSES,
                "订单仓库汇总导出",
                queryParam,
                requestedBy
        );
        if (runImmediately) {
            return runTask(task.taskId());
        }
        return task;
    }

    @Transactional
    public AdminExportTaskRecord runTask(UUID taskId) {
        AdminExportTaskRecord task = findTask(taskId);
        boolean retry = "FAILED".equals(task.taskStatus());
        exportTaskRepository.markRunning(DEFAULT_TENANT_ID, taskId, retry);
        try {
            ExportContent content = exportContent(task);
            exportTaskRepository.markSuccess(
                    DEFAULT_TENANT_ID,
                    taskId,
                    content.fileName(),
                    CSV_CONTENT_TYPE,
                    content.content(),
                    content.rowCount()
            );
        } catch (RuntimeException ex) {
            exportTaskRepository.markFailed(DEFAULT_TENANT_ID, taskId, failureMessage(ex));
        }
        return findTask(taskId);
    }

    @Transactional
    public AdminExportTaskBatchRunResult runPendingTasks(int limit) {
        List<UUID> taskIds = exportTaskRepository.findPendingTaskIds(DEFAULT_TENANT_ID, Math.min(Math.max(limit, 1), 50));
        List<AdminExportTaskRecord> records = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        for (UUID taskId : taskIds) {
            AdminExportTaskRecord record = runTask(taskId);
            records.add(record);
            if ("SUCCESS".equals(record.taskStatus())) {
                successCount++;
            } else if ("FAILED".equals(record.taskStatus())) {
                failCount++;
            }
        }
        return new AdminExportTaskBatchRunResult(records.size(), successCount, failCount, records);
    }

    public AdminExportTaskFile getTaskFile(UUID taskId) {
        findTask(taskId);
        return exportTaskRepository.findTaskFile(DEFAULT_TENANT_ID, taskId)
                .orElseThrow(() -> new BusinessException("EXPORT_TASK_FILE_NOT_READY", "导出文件尚未生成"));
    }

    private AdminExportTaskRecord createTask(
            String taskType,
            String taskName,
            Map<String, String> queryParam,
            String requestedBy
    ) {
        AdminExportTaskRecord task = new AdminExportTaskRecord(
                UUID.randomUUID(),
                DEFAULT_TENANT_ID,
                taskType,
                taskName,
                "PENDING",
                json(queryParam),
                null,
                null,
                null,
                null,
                null,
                defaultText(requestedBy, "admin"),
                0,
                null,
                null,
                null,
                null
        );
        exportTaskRepository.insertTask(task);
        return findTask(task.taskId());
    }

    private ExportContent exportContent(AdminExportTaskRecord task) {
        Map<String, String> queryParam = parseQueryParam(task.queryParam());
        if (TASK_TYPE_ADMIN_ORDERS.equals(task.taskType())) {
            String csv = orderService.exportAdminOrdersCsv(toOrderQuery(queryParam));
            return new ExportContent(
                    "订单信息汇总-" + LocalDate.now(DEFAULT_QUERY_ZONE) + ".csv",
                    csv.getBytes(StandardCharsets.UTF_8),
                    dataRows(csv)
            );
        }
        if (TASK_TYPE_ADMIN_ORDER_WAREHOUSES.equals(task.taskType())) {
            String csv = orderService.exportAdminOrderWarehousesCsv(toOrderWarehouseQuery(queryParam));
            return new ExportContent(
                    "订单仓库汇总-" + LocalDate.now(DEFAULT_QUERY_ZONE) + ".csv",
                    csv.getBytes(StandardCharsets.UTF_8),
                    dataRows(csv)
            );
        }
        throw new BusinessException("EXPORT_TASK_TYPE_UNSUPPORTED", "不支持的导出任务类型");
    }

    private AdminOrderSearchQuery toOrderQuery(Map<String, String> queryParam) {
        return new AdminOrderSearchQuery(
                parseQueryTime(queryParam.get("startTime")),
                parseQueryTime(queryParam.get("endTime")),
                queryParam.get("institution"),
                queryParam.get("prescriptionType"),
                queryParam.get("hospitalType"),
                queryParam.get("orderStatus"),
                queryParam.get("excludeOrderStatus"),
                queryParam.get("decoctionCenter"),
                queryParam.get("deliveryType"),
                queryParam.get("logisticsCompany"),
                queryParam.get("province"),
                queryParam.get("keyword"),
                queryParam.get("hospitalPrescriptionNo"),
                queryParam.get("patientName"),
                queryParam.get("receiverPhone"),
                1,
                5000
        );
    }

    private AdminOrderWarehouseQuery toOrderWarehouseQuery(Map<String, String> queryParam) {
        return new AdminOrderWarehouseQuery(
                parseQueryTime(queryParam.get("startTime")),
                parseQueryTime(queryParam.get("endTime")),
                queryParam.get("institution"),
                queryParam.get("prescriptionType"),
                queryParam.get("hospitalType"),
                queryParam.get("orderStatus"),
                queryParam.get("decoctionCenter"),
                queryParam.get("deliveryType"),
                queryParam.get("logisticsCompany"),
                queryParam.get("province"),
                queryParam.get("orderNo"),
                queryParam.get("prescriptionNo"),
                queryParam.get("hospitalPrescriptionNo"),
                queryParam.get("patientName"),
                queryParam.get("receiverPhone"),
                queryParam.get("nodeTime"),
                1,
                5000
        );
    }

    private AdminExportTaskRecord findTask(UUID taskId) {
        return exportTaskRepository.findTask(DEFAULT_TENANT_ID, taskId)
                .orElseThrow(() -> new BusinessException("EXPORT_TASK_NOT_FOUND", "导出任务不存在"));
    }

    private String json(Map<String, String> queryParam) {
        try {
            return objectMapper.writeValueAsString(queryParam);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("EXPORT_TASK_QUERY_INVALID", "导出查询条件无法保存");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseQueryParam(String value) {
        try {
            return objectMapper.readValue(value, Map.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("EXPORT_TASK_QUERY_INVALID", "导出查询条件无法读取");
        }
    }

    private int dataRows(String csv) {
        if (!StringUtils.hasText(csv)) {
            return 0;
        }
        String normalized = csv.startsWith("\ufeff") ? csv.substring(1) : csv;
        int lineCount = normalized.split("\\R", -1).length;
        if (normalized.endsWith("\n") || normalized.endsWith("\r")) {
            lineCount--;
        }
        return Math.max(lineCount - 1, 0);
    }

    private String failureMessage(RuntimeException ex) {
        if (StringUtils.hasText(ex.getMessage())) {
            return ex.getMessage();
        }
        return ex.getClass().getSimpleName();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String cleanText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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

    private record ExportContent(String fileName, byte[] content, int rowCount) {
    }
}
