package com.zhyf.logistics.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.logistics.application.LogisticsCommands;
import com.zhyf.logistics.application.LogisticsRecords;
import com.zhyf.logistics.application.LogisticsService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacyLogisticsController {

    private final LogisticsService logisticsService;

    public LegacyLogisticsController(LogisticsService logisticsService) {
        this.logisticsService = logisticsService;
    }

    @RequestMapping(path = "/logistics/notify", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<LogisticsRecords.ShipmentRecord> sfNotify(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiResponse.ok(logisticsService.receiveTrace(traceCommand(query, body, "SF")));
    }

    @RequestMapping(path = "/logistics/emsNotify", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<LogisticsRecords.ShipmentRecord> emsNotify(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiResponse.ok(logisticsService.receiveTrace(traceCommand(query, body, "EMS")));
    }

    @RequestMapping(path = "/logistics/queryBillPrintInfo", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<Map<String, Object>> queryBillPrintInfo(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiResponse.ok(logisticsService.queryBillPrintInfo(value(query, body, "orderId", "orderNo", "bspOrderNo")));
    }

    @RequestMapping(path = "/logistics/createLogisticsOrder", method = {RequestMethod.GET, RequestMethod.POST})
    public LegacyApiResponse<Map<String, Object>> createLogisticsOrder(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return legacyResult(() -> logisticsService.createLegacyLogisticsOrder(legacyOrderCommand(query, body)));
    }

    @RequestMapping(path = "/logistics/cancelLogisticsOrder", method = {RequestMethod.GET, RequestMethod.POST})
    public LegacyApiResponse<Map<String, Object>> cancelLogisticsOrder(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return legacyResult(() -> logisticsService.cancelLegacyLogisticsOrder(legacyOrderCommand(query, body)));
    }

    @RequestMapping(path = "/logistics/printWaybills", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<Map<String, Object>> printWaybills(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiResponse.ok(logisticsService.printWaybill(
                value(query, body, "orderId", "orderNo", "bspOrderNo"),
                value(query, body, "templateCode", "template_code")
        ));
    }

    @RequestMapping(path = "/logistics/queryEmsPdfFile", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<Map<String, Object>> queryEmsPdfFile(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiResponse.ok(logisticsService.queryEmsPdfFile(value(query, body, "waybillNo", "logisticsNo", "mailNo")));
    }

    @RequestMapping(path = "/logistics/queryLogisticsCost", method = {RequestMethod.GET, RequestMethod.POST})
    public LegacyApiResponse<Map<String, Object>> queryLogisticsCost(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return LegacyApiResponse.ok(logisticsService.queryLogisticsCost(
                value(query, body, "orderId", "orderNo", "bspOrderNo")
        ));
    }

    @RequestMapping(path = "/logistics/queryLogisticsInfo", method = {RequestMethod.GET, RequestMethod.POST})
    public LegacyApiResponse<List<Map<String, Object>>> queryLogisticsInfo(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return LegacyApiResponse.ok(logisticsService.queryLogisticsInfo(
                intValue(value(query, body, "queryWay")),
                value(query, body, "paramValue", "orderId", "orderNo", "receiverPhone", "tel")
        ));
    }

    private LogisticsCommands.TraceCommand traceCommand(Map<String, String> query, Map<String, Object> body, String provider) {
        String logisticsNo = value(query, body, "logisticsNo", "mailNo", "waybillNo", "logistics_no");
        String opCode = value(query, body, "opCode", "statusCode", "code");
        String content = value(query, body, "traceContent", "remark", "description", "content");
        String rawPayload = body == null || body.isEmpty() ? "{\"source\":\"legacy-logistics\"}" : json(body);
        return new LogisticsCommands.TraceCommand(logisticsNo, provider, opCode, content, rawPayload, Instant.now(), "legacy-logistics");
    }

    private LogisticsCommands.LegacyLogisticsOrderCommand legacyOrderCommand(
            Map<String, String> query,
            Map<String, Object> body
    ) {
        return new LogisticsCommands.LegacyLogisticsOrderCommand(
                value(query, body, "orderId", "orderNo", "bspOrderNo"),
                value(query, body, "logisticsCompany", "logisticsCompanyName", "provider"),
                value(query, body, "waybillNo", "logisticsNo", "mailNo"),
                value(query, body, "payMethod"),
                decimalValue(value(query, body, "parcelWeighs", "pkgWeight", "weight")),
                intValue(value(query, body, "packagesNo", "pkgNum", "depositumNo")),
                "legacy-logistics",
                value(query, body, "remark")
        );
    }

    private String value(Map<String, String> query, Map<String, Object> body, String... keys) {
        for (String key : keys) {
            if (query != null && query.get(key) != null && !query.get(key).isBlank()) {
                return query.get(key);
            }
            if (body != null && body.get(key) != null && !String.valueOf(body.get(key)).isBlank()) {
                return String.valueOf(body.get(key));
            }
            if (body != null && body.get("data") instanceof Map<?, ?> data
                    && data.get(key) != null && !String.valueOf(data.get(key)).isBlank()) {
                return String.valueOf(data.get(key));
            }
            if (body != null && body.get("body") instanceof Map<?, ?> nestedBody
                    && nestedBody.get(key) != null && !String.valueOf(nestedBody.get(key)).isBlank()) {
                return String.valueOf(nestedBody.get(key));
            }
            if (body != null && body.get("body") != null && !(body.get("body") instanceof Map<?, ?>)
                    && !String.valueOf(body.get("body")).isBlank()) {
                return String.valueOf(body.get("body"));
            }
        }
        return null;
    }

    private Integer intValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private java.math.BigDecimal decimalValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private LegacyApiResponse<Map<String, Object>> legacyResult(LegacyOperation operation) {
        try {
            return LegacyApiResponse.ok(operation.execute());
        } catch (com.zhyf.common.exception.BusinessException ex) {
            return LegacyApiResponse.fail(mapLegacyCode(ex.code()), ex.getMessage());
        } catch (Exception ex) {
            return LegacyApiResponse.fail("441000", "系统内部错误");
        }
    }

    private String mapLegacyCode(String code) {
        return switch (code == null ? "" : code) {
            case "ORDER_NO_REQUIRED" -> "440100";
            case "ORDER_NOT_FOUND", "SHIPMENT_NOT_FOUND" -> "440900";
            case "SHIPMENT_CANCEL_NOT_ALLOWED" -> "440800";
            default -> "449999";
        };
    }

    private String json(Map<String, Object> body) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append('"').append(escape(entry.getKey())).append('"').append(':')
                    .append('"').append(escape(String.valueOf(entry.getValue()))).append('"');
            first = false;
        }
        builder.append('}');
        return builder.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record LegacyApiResponse<T>(String code, String message, T data) {
        private static <T> LegacyApiResponse<T> ok(T data) {
            return new LegacyApiResponse<>("200", "success", data);
        }

        private static <T> LegacyApiResponse<T> fail(String code, String message) {
            return new LegacyApiResponse<>(code, message, null);
        }
    }

    @FunctionalInterface
    private interface LegacyOperation {
        Map<String, Object> execute();
    }
}
