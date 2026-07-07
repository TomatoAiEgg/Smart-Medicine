package com.zhyf.decoction.api.legacy;

import com.zhyf.decoction.adapter.DeviceOperationRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class LegacyDeviceRequest {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Hong_Kong");

    private final Map<String, String> values;

    private LegacyDeviceRequest(Map<String, String> values) {
        this.values = values;
    }

    static LegacyDeviceRequest from(Map<String, String> query, Map<String, Object> body) {
        Map<String, String> values = new LinkedHashMap<>();
        if (body != null) {
            body.forEach((key, value) -> put(values, key, value));
        }
        if (query != null) {
            query.forEach((key, value) -> put(values, key, value));
        }
        return new LegacyDeviceRequest(values);
    }

    DeviceOperationRequest toOperation(String prefix) {
        return toOperation(prefix, null);
    }

    DeviceOperationRequest toOperation(String prefix, String forcedStatus) {
        String taskNo = text("taskNo", "task_no", "decoctionTaskNo", "decoction_task_no");
        String deviceCode = text("deviceCode", "device_code", "equipCode", "equipNo", "equipmentCode", "machineCode");
        String prescriptionNo = text("prescriptionNo", "prescription_no", "recipeNo", "recipeId", "recipelId", "recipeCode");
        String pailNo = text("pailNo", "pail_no", "bucketNo", "barrelNo", "pailCode", "waterBucketNo");
        String status = StringUtils.hasText(forcedStatus)
                ? forcedStatus
                : text("operStatus", "opStatus", "status", "decoctingStatus", "boilStatus");
        String operator = text("operator", "account", "userName", "username", "user", "opUser", "operUser", "operater");
        if (!StringUtils.hasText(operator)) {
            operator = "legacy-device";
        }
        return new DeviceOperationRequest(
                operationId(prefix, taskNo, prescriptionNo, deviceCode, pailNo, status),
                taskNo,
                deviceCode,
                prescriptionNo,
                pailNo,
                operator,
                timestamp(),
                text("sign", "signature", "password", "token"),
                status,
                text("reason", "errorReason", "remark", "memo"),
                integer("waterVolumeMl", "waterVolume", "waterMl", "water", "addWater"),
                integer("temperatureCelsius", "temperature", "temp", "tempValue"),
                integer("durationSeconds", "durationSeconds", "duration", "boilSeconds", "timeLength"),
                text("remark", "memo", "note")
        );
    }

    String pailNo() {
        return text("pailNo", "pail_no", "bucketNo", "barrelNo", "pailCode", "waterBucketNo");
    }

    String prescriptionNo() {
        return text("prescriptionNo", "prescription_no", "recipeNo", "recipeId", "recipelId", "recipeCode");
    }

    int limit(int defaultLimit) {
        Integer value = integer("limit", "pageSize", "rows");
        return value == null ? defaultLimit : value;
    }

    private String operationId(
            String prefix,
            String taskNo,
            String prescriptionNo,
            String deviceCode,
            String pailNo,
            String status
    ) {
        String existing = text("operationId", "operation_id", "opId", "requestId", "serialNo", "logNo", "traceId");
        if (StringUtils.hasText(existing)) {
            return existing;
        }
        String timestamp = text("timestamp", "opTime", "operTime", "operateTime", "time", "createTime");
        if (!StringUtils.hasText(timestamp)) {
            timestamp = String.valueOf(System.currentTimeMillis());
        }
        String businessKey = firstText(taskNo, prescriptionNo, pailNo, deviceCode, "none");
        String raw = prefix + "-" + businessKey + "-" + firstText(status, "NA") + "-" + timestamp;
        return raw.replaceAll("[^A-Za-z0-9_.:-]", "-");
    }

    private Instant timestamp() {
        String raw = text("timestamp", "opTime", "operTime", "operateTime", "time", "createTime");
        if (!StringUtils.hasText(raw)) {
            return Instant.now();
        }
        try {
            if (raw.matches("\\d+")) {
                long number = Long.parseLong(raw);
                return number > 9_999_999_999L ? Instant.ofEpochMilli(number) : Instant.ofEpochSecond(number);
            }
            return Instant.parse(raw);
        } catch (DateTimeParseException | NumberFormatException ignored) {
            // Try the legacy local datetime format below.
        }
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException ignored) {
            // Try local datetime below.
        }
        try {
            LocalDateTime value = LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return value.atZone(DEFAULT_ZONE).toInstant();
        } catch (DateTimeParseException ignored) {
            return Instant.now();
        }
    }

    private Integer integer(String... keys) {
        String value = text(keys);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String text(String... keys) {
        for (String key : keys) {
            String value = values.get(normalizeKey(key));
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private static void put(Map<String, String> values, String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        values.put(normalizeKey(key), String.valueOf(value));
    }

    private static String normalizeKey(String key) {
        return key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }
}
