package com.zhyf.order.api.legacy;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.order.application.LegacyPdaLabelPrintResult;
import com.zhyf.order.application.LegacyPdaLabelPrintInitResult;
import com.zhyf.order.application.OrderService;
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
public class LegacyPdaLabelController {

    private final OrderService orderService;

    public LegacyPdaLabelController(OrderService orderService) {
        this.orderService = orderService;
    }

    @RequestMapping(path = "/pdaLabelPrintInit", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<LegacyPdaLabelPrintInitResult> labelPrintInit(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        LegacyRequest request = LegacyRequest.from(query, body);
        return ApiResponse.ok(orderService.getLegacyPdaLabelPrintInit(
                request.text("recipeId", "recipeNo", "prescriptionNo", "prescription_no")
        ));
    }

    @RequestMapping(path = "/pdaLabelPrint", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<LegacyPdaLabelPrintResult> labelPrint(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        LegacyRequest request = LegacyRequest.from(query, body);
        return ApiResponse.ok(orderService.createLegacyPdaLabelPrintRecord(
                request.text("recipeId", "recipeNo", "prescriptionNo", "prescription_no"),
                request.integer("printNum", "print_num", "times"),
                request.text("dmjCode", "printerCode", "printer_code"),
                request.text("dmjIp", "printerIp", "printer_ip"),
                request.text("account", "operator", "userName", "username", "opUser", "operUser")
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
