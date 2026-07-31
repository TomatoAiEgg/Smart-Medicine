package com.zhyf.order.api.legacy;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.order.application.LegacyPdaLogisticsOutboundCommand;
import com.zhyf.order.application.LegacyPdaLogisticsOutboundResult;
import com.zhyf.order.application.OrderStatusUpdateService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacyPdaLogisticsController {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Hong_Kong");

    private final OrderStatusUpdateService orderStatusUpdateService;

    public LegacyPdaLogisticsController(OrderStatusUpdateService orderStatusUpdateService) {
        this.orderStatusUpdateService = orderStatusUpdateService;
    }

    @RequestMapping(path = "/pdaLogisticsOutbound", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<LegacyPdaLogisticsOutboundResult> outbound(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        LegacyRequest request = LegacyRequest.from(query, body);
        return ApiResponse.ok(orderStatusUpdateService.legacyPdaLogisticsOutbound(
                new LegacyPdaLogisticsOutboundCommand(
                        request.text("recipeId", "recipeNo", "prescriptionNo", "prescription_no"),
                        request.text("operFlag", "oper_flag"),
                        request.text("account", "operator", "userName", "username", "opUser", "operUser"),
                        request.timestamp()
                )
        ));
    }

    private static final class LegacyRequest {

        private final Map<String, String> values;

        private LegacyRequest(Map<String, String> values) {
            this.values = values;
        }

        private static LegacyRequest from(Map<String, String> query, Map<String, Object> body) {
            Map<String, String> values = new LinkedHashMap<>();
            if (body != null) {
                body.forEach((key, value) -> put(values, key, value));
            }
            if (query != null) {
                query.forEach((key, value) -> put(values, key, value));
            }
            return new LegacyRequest(values);
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
                // 继续兼容老系统本地时间格式。
            }
            try {
                return OffsetDateTime.parse(raw).toInstant();
            } catch (DateTimeParseException ignored) {
                // 继续兼容老系统本地时间格式。
            }
            try {
                LocalDateTime value = LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                return value.atZone(DEFAULT_ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
                return Instant.now();
            }
        }

        private static void put(Map<String, String> values, String key, Object value) {
            if (key == null || value == null) {
                return;
            }
            if (value instanceof Map<?, ?> nested) {
                nested.forEach((nestedKey, nestedValue) -> {
                    if (nestedKey != null) {
                        put(values, String.valueOf(nestedKey), nestedValue);
                    }
                });
                return;
            }
            values.put(normalizeKey(key), String.valueOf(value));
        }

        private static String normalizeKey(String key) {
            return key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        }
    }
}
