package com.zhyf.logistics.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.logistics.application.LogisticsCommands;
import com.zhyf.logistics.application.LogisticsRecords;
import com.zhyf.logistics.application.LogisticsService;
import java.time.Instant;
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

    private LogisticsCommands.TraceCommand traceCommand(Map<String, String> query, Map<String, Object> body, String provider) {
        String logisticsNo = value(query, body, "logisticsNo", "mailNo", "waybillNo", "logistics_no");
        String opCode = value(query, body, "opCode", "statusCode", "code");
        String content = value(query, body, "traceContent", "remark", "description", "content");
        String rawPayload = body == null || body.isEmpty() ? "{\"source\":\"legacy-logistics\"}" : json(body);
        return new LogisticsCommands.TraceCommand(logisticsNo, provider, opCode, content, rawPayload, Instant.now(), "legacy-logistics");
    }

    private String value(Map<String, String> query, Map<String, Object> body, String... keys) {
        for (String key : keys) {
            if (query != null && query.get(key) != null && !query.get(key).isBlank()) {
                return query.get(key);
            }
            if (body != null && body.get(key) != null && !String.valueOf(body.get(key)).isBlank()) {
                return String.valueOf(body.get(key));
            }
        }
        return null;
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
}
