package com.zhyf.gateway.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.security.SignatureUtils;
import com.zhyf.gateway.accesslog.AccessLogService;
import com.zhyf.gateway.app.InstitutionAppClient;
import com.zhyf.gateway.app.InstitutionAppView;
import com.zhyf.gateway.config.GatewayProperties;
import com.zhyf.gateway.whitelist.IpWhitelistChecker;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

@RestController
@RequestMapping("/zhy")
public class LegacySmartCloudOrderController {

    private static final ZoneId LEGACY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LEGACY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(LEGACY_ZONE);
    private static final DateTimeFormatter LEGACY_MILLIS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter LEGACY_TIME_PARSER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.MILLI_OF_SECOND, 1, 3, true)
            .optionalEnd()
            .toFormatter();

    private final InstitutionAppClient appClient;
    private final IpWhitelistChecker ipWhitelistChecker;
    private final AccessLogService accessLogService;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LegacySmartCloudOrderController(
            InstitutionAppClient appClient,
            IpWhitelistChecker ipWhitelistChecker,
            AccessLogService accessLogService,
            GatewayProperties properties,
            ObjectMapper objectMapper,
            RestClient.Builder builder
    ) {
        this.appClient = appClient;
        this.ipWhitelistChecker = ipWhitelistChecker;
        this.accessLogService = accessLogService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = builder.build();
    }

    @RequestMapping("/healthCheck")
    public String healthCheck() {
        return "OK";
    }

    @PostMapping(value = "/syncHospitalRecipel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> syncHospitalRecipel(@RequestBody String rawBody, HttpServletRequest request) {
        return handleSmartCloud("syncHospitalRecipel", rawBody, request, root -> {
            InstitutionAppView app = verifySmartCloudApp(root, "createOrder", request);
            verifyCreateSign(root, app.appSecret());
            ObjectNode payload = transformCreatePayload(root);
            String currentRawBody = objectMapper.writeValueAsString(payload);
            JsonNode created = currentCreateOrder(app, currentRawBody);
            return success("数据同步成功" + readText(created.path("data"), "orderNo"));
        });
    }

    @PostMapping(value = "/modHospitalRecipel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> modHospitalRecipel(@RequestBody String rawBody, HttpServletRequest request) {
        return handleSmartCloud("modHospitalRecipel", rawBody, request, root -> {
            verifySmartCloudApp(root, "updateOrderAddress", request);
            String hospitalPrescriptionNo = requireText(root, "outtradeno", "outtradeno不能为空");
            String orderNo = findOrderNoByHospitalPrescriptionNo(hospitalPrescriptionNo);
            ObjectNode command = buildAddressCommand(root.path("express"));
            currentPatch("/api/admin/orders/" + encode(orderNo) + "/address", command);
            return success("修改成功");
        });
    }

    @PostMapping(value = "/cancelRecipel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> cancelRecipel(@RequestBody String rawBody, HttpServletRequest request) {
        return handleSmartCloud("cancelRecipel", rawBody, request, root -> {
            InstitutionAppView app = verifySmartCloudApp(root, "cancelOrder", request);
            verifyCancelSign(root, app.appSecret());
            String hospitalPrescriptionNo = requireText(root, "orderNumber", "机构处方号不能为空");
            String orderNo = findOrderNoByHospitalPrescriptionNo(hospitalPrescriptionNo);
            ObjectNode command = objectMapper.createObjectNode();
            command.put("operator", "legacy-smart-cloud");
            command.put("reason", "legacy cancelRecipel");
            currentPost("/api/admin/orders/" + encode(orderNo) + "/cancel", command);
            return success("取消成功");
        });
    }

    @PostMapping(value = "/recipeStatusCallback", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> recipeStatusCallback(@RequestBody(required = false) String rawBody, HttpServletRequest request) {
        accessLogService.record(request.getRequestURI(), clientIp(request), null, null, "NOT_IMPLEMENTED");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(writeSmartResponse(failure("合利康源状态回调未接入")));
    }

    private ResponseEntity<String> handleSmartCloud(
            String methodName,
            String rawBody,
            HttpServletRequest request,
            SmartCloudHandler handler
    ) {
        String appKey = null;
        String resultCode = "SUCCESS";
        try {
            JsonNode root = objectMapper.readTree(defaultText(rawBody, "{}").replace("\\[", "[").replace("\\]", "]"));
            appKey = readText(root, "appid");
            ObjectNode result = handler.handle(root);
            resultCode = result.path("status").asInt() == 1 ? "SUCCESS" : "FAILED";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(writeSmartResponse(result));
        } catch (SmartCloudApiException ex) {
            resultCode = ex.code();
            return smartError(ex.getMessage());
        } catch (BusinessException ex) {
            resultCode = ex.code();
            return smartError(ex.getMessage());
        } catch (RestClientResponseException ex) {
            resultCode = "DOWNSTREAM_" + ex.getStatusCode().value();
            return smartError(downstreamMessage(ex));
        } catch (Exception ex) {
            resultCode = "SMART_CLOUD_ERROR";
            return smartError("系统内部错误");
        } finally {
            accessLogService.record(request.getRequestURI(), clientIp(request), appKey, null, resultCode);
        }
    }

    private InstitutionAppView verifySmartCloudApp(JsonNode root, String apiCode, HttpServletRequest request) {
        String appKey = requireText(root, "appid", "appid不能为空");
        String hospitalId = requireText(root, "hospitalid", "hospitalid不能为空");
        InstitutionAppView app = appClient.getEnabledApp(appKey);
        if (!allowsApi(app, apiCode) && !allowsApi(app, legacyApiCode(apiCode))) {
            throw new SmartCloudApiException("API_NOT_ALLOWED", "接口未授权");
        }
        ipWhitelistChecker.check(app.ipWhitelist(), clientIp(request));
        if (!StringUtils.hasText(hospitalId)) {
            throw new SmartCloudApiException("PARAM_ERROR", "hospitalid不能为空");
        }
        return app;
    }

    private boolean allowsApi(InstitutionAppView app, String apiCode) {
        return app != null && app.allowsApi(apiCode);
    }

    private String legacyApiCode(String apiCode) {
        return switch (apiCode) {
            case "createOrder" -> "syncHospitalRecipel";
            case "updateOrderAddress" -> "modHospitalRecipel";
            case "cancelOrder" -> "cancelRecipel";
            default -> apiCode;
        };
    }

    private void verifyCreateSign(JsonNode root, String appSecret) {
        verifyLegacyMd5Sign(root, appSecret, true);
    }

    private void verifyCancelSign(JsonNode root, String appSecret) {
        verifyLegacyMd5Sign(root, appSecret, true);
    }

    private void verifyLegacyMd5Sign(JsonNode root, String appSecret, boolean requireSign) {
        String sign = readText(root, "sign");
        String hospitalId = readText(root, "hospitalid");
        if (!StringUtils.hasText(sign) && "10008".equals(hospitalId)) {
            return;
        }
        if (requireSign && !StringUtils.hasText(sign)) {
            throw new SmartCloudApiException("SIGN_ERROR", "签名错误");
        }
        for (String timestamp : timestampCandidates(root.get("timestamp"))) {
            TreeMap<String, String> signParams = buildSignParams(root, timestamp);
            String plainText = linkedParams(signParams) + "&key=" + appSecret;
            if (SignatureUtils.constantTimeEquals(md5Hex(plainText).toUpperCase(), sign.toUpperCase())) {
                return;
            }
        }
        throw new SmartCloudApiException("SIGN_ERROR", "签名错误");
    }

    private TreeMap<String, String> buildSignParams(JsonNode root, String timestamp) {
        TreeMap<String, String> params = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            if ("sign".equals(key)) {
                continue;
            }
            if ("timestamp".equals(key)) {
                params.put(key, timestamp);
            } else if ("drug".equals(key) && field.getValue().isArray()) {
                params.put(key, buildArraySignText(field.getValue()));
            } else if (field.getValue().isObject()) {
                params.put(key, buildObjectSignText(field.getValue()));
            } else {
                params.put(key, field.getValue().asText());
            }
        }
        return params;
    }

    private String buildArraySignText(JsonNode array) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : array) {
            if (!builder.isEmpty()) {
                builder.append("&");
            }
            builder.append(item.isObject() ? buildObjectSignText(item) : item.asText());
        }
        return builder.toString();
    }

    private String buildObjectSignText(JsonNode object) {
        TreeMap<String, String> params = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            params.put(field.getKey(), field.getValue().asText());
        }
        return linkedParams(params);
    }

    private String[] timestampCandidates(JsonNode timestampNode) {
        String raw = timestampNode == null || timestampNode.isNull() ? "" : timestampNode.asText();
        if (!StringUtils.hasText(raw)) {
            return new String[]{""};
        }
        try {
            LocalDateTime parsed = LocalDateTime.parse(raw, LEGACY_TIME_PARSER);
            return new String[]{LEGACY_MILLIS_FORMATTER.format(parsed), LEGACY_TIME_FORMATTER.format(parsed.atZone(LEGACY_ZONE))};
        } catch (Exception ignored) {
            return new String[]{raw};
        }
    }

    private ObjectNode transformCreatePayload(JsonNode root) {
        String outTradeNo = requireText(root, "outtradeno", "outtradeno不能为空");
        JsonNode patient = requiredNode(root, "patient");
        JsonNode hospital = requiredNode(root, "hospital");
        JsonNode recipel = requiredNode(root, "recipel");
        JsonNode express = requiredNode(root, "express");
        JsonNode drugs = requiredNode(root, "drug");
        if (!drugs.isArray() || drugs.isEmpty()) {
            throw new SmartCloudApiException("PARAM_ERROR", "drug不能为空");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("externalOrderNo", outTradeNo);
        payload.put("source", "legacy-smart-cloud");
        putIfText(payload, "hospitalId", readText(root, "hospitalid"));
        putIfText(payload, "hospitalCode", readText(hospital, "code"));
        putIfText(payload, "hospitalName", readText(hospital, "name"));
        putIfText(payload, "patientName", readText(patient, "name"));
        putIfText(payload, "patientPhone", readText(patient, "phone"));
        putIfText(payload, "patientAge", readText(patient, "age"));
        putIfText(payload, "patientMonthAge", readText(patient, "monthAge"));
        putIfText(payload, "patientDayAge", readText(patient, "dayAge"));
        putIfText(payload, "receiverName", readText(express, "name"));
        putIfText(payload, "receiverPhone", readText(express, "phone"));
        putAddress(payload, readText(express, "address"));
        putIfText(payload, "addressType", readText(express, "type"));
        putIfText(payload, "deliveryTime", normalizeDateTime(readText(express, "time")));
        putIfText(payload, "logisticsCompany", readText(express, "company"));
        putIfText(payload, "logisticsMode", readText(express, "mode"));
        putIfText(payload, "orderRemark", readText(recipel, "advice"));

        ObjectNode prescription = objectMapper.createObjectNode();
        prescription.put("externalPrescriptionNo", outTradeNo);
        prescription.put("prescriptionNo", outTradeNo);
        putIfText(prescription, "prescriptionType", readText(recipel, "preparation"));
        putIfText(prescription, "hospitalType", hospitalType(readText(patient, "hospitalization")));
        putIfText(prescription, "doseCount", readText(recipel, "dosage"));
        putIfText(prescription, "boilTimes", smartBoilTimes(recipel));
        putIfText(prescription, "perPackNum", readText(recipel, "times"));
        putIfText(prescription, "perPackDose", readText(recipel, "weight"));
        putIfText(prescription, "totalAmount", readText(recipel, "amount"));
        putIfText(prescription, "diagnosis", readText(recipel, "clinicalDiagnosis"));
        putIfText(prescription, "departmentName", readText(patient, "department"));
        putIfText(prescription, "bedNo", readText(patient, "bednumber"));
        putIfText(prescription, "medicationMethod", readText(recipel, "medicationmode"));
        putIfText(prescription, "medicationInstruction", readText(recipel, "usage"));
        putIfText(prescription, "prescriptionRemark", readText(recipel, "advice"));
        putIfText(prescription, "doctorName", readText(hospital, "doctorname"));
        ArrayNode details = objectMapper.createArrayNode();
        for (JsonNode drug : drugs) {
            ObjectNode detail = objectMapper.createObjectNode();
            putIfText(detail, "drugCode", readText(drug, "code"));
            putIfText(detail, "medicineCode", readText(drug, "code"));
            putIfText(detail, "drugName", readText(drug, "name"));
            putIfText(detail, "medicineName", readText(drug, "name"));
            putIfText(detail, "dose", readText(drug, "number"));
            putIfText(detail, "quantity", readText(drug, "number"));
            putIfText(detail, "unit", readText(drug, "unit"));
            putIfText(detail, "specialUsage", readText(drug, "preparation"));
            details.add(detail);
        }
        prescription.set("details", details);
        payload.set("prescriptions", objectMapper.createArrayNode().add(prescription));
        return payload;
    }

    private ObjectNode buildAddressCommand(JsonNode express) {
        if (express == null || express.isNull()) {
            throw new SmartCloudApiException("PARAM_ERROR", "express参数未填写");
        }
        ObjectNode command = objectMapper.createObjectNode();
        command.put("receiverName", requireText(express, "name", "express.name不能为空"));
        command.put("receiverPhone", requireText(express, "phone", "express.phone不能为空"));
        putAddress(command, requireText(express, "address", "express.address不能为空"));
        putIfText(command, "addressType", readText(express, "type"));
        putIfText(command, "deliveryTime", normalizeDateTime(readText(express, "time")));
        command.put("operator", "legacy-smart-cloud");
        command.put("reason", "legacy modHospitalRecipel");
        return command;
    }

    private void putAddress(ObjectNode target, String address) {
        if (!StringUtils.hasText(address)) {
            return;
        }
        String[] parts = address.split(",", 4);
        if (parts.length > 0) {
            putIfText(target, "receiverProvince", parts[0]);
        }
        if (parts.length > 1) {
            putIfText(target, "receiverCity", parts[1]);
        }
        if (parts.length > 2) {
            putIfText(target, "receiverZone", parts[2]);
        }
        target.put("receiverAddress", parts.length > 3 ? parts[3].trim() : address.trim());
    }

    private String smartBoilTimes(JsonNode recipel) {
        String preparation = readText(recipel, "preparation");
        String mode = readText(recipel, "mode");
        if ("汤剂".equals(preparation) && "代煎".equals(mode)) {
            return "1";
        }
        return readText(recipel, "fryingtimes");
    }

    private String hospitalType(String hospitalization) {
        if ("0".equals(hospitalization)) {
            return "1";
        }
        if ("1".equals(hospitalization)) {
            return "2";
        }
        return hospitalization;
    }

    private String normalizeDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replace("/", "-").trim();
    }

    private String findOrderNoByHospitalPrescriptionNo(String hospitalPrescriptionNo) {
        JsonNode data = currentGet("/api/admin/orders?hospitalPrescriptionNo="
                + encode(hospitalPrescriptionNo) + "&page=1&pageSize=1").path("data");
        JsonNode records = data.path("records");
        if (records.isArray() && !records.isEmpty()) {
            String orderNo = readText(records.get(0), "orderNo");
            if (StringUtils.hasText(orderNo)) {
                return orderNo;
            }
        }
        throw new SmartCloudApiException("ORDER_NOT_FOUND", "该处方号不存在");
    }

    private JsonNode currentCreateOrder(InstitutionAppView app, String rawBody) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String bodyHash = SignatureUtils.sha256Hex(rawBody);
        String source = app.appKey() + "\n" + timestamp + "\n" + bodyHash;
        String signature = SignatureUtils.hmacSha256Hex(app.appSecret(), source);
        return currentPost("/api/institution/createOrder", rawBody, headers -> {
            headers.set("X-App-Key", app.appKey());
            headers.set("X-Timestamp", timestamp);
            headers.set("X-Signature", signature);
        });
    }

    private JsonNode currentGet(String path) {
        String body = restClient.get()
                .uri(URI.create(properties.orderServiceBaseUrl() + path))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
        return parseDownstream(body);
    }

    private JsonNode currentPost(String path, JsonNode body) {
        return currentPost(path, body.toString(), headers -> {
        });
    }

    private JsonNode currentPost(String path, String body, HeaderCustomizer customizer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        customizer.customize(headers);
        String response = restClient.post()
                .uri(URI.create(properties.orderServiceBaseUrl() + path))
                .headers(target -> target.putAll(headers))
                .body(body)
                .retrieve()
                .body(String.class);
        return parseDownstream(response);
    }

    private JsonNode currentPatch(String path, JsonNode body) {
        String response = restClient.patch()
                .uri(URI.create(properties.orderServiceBaseUrl() + path))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);
        return parseDownstream(response);
    }

    private JsonNode parseDownstream(String body) {
        try {
            JsonNode response = objectMapper.readTree(defaultText(body, "{}"));
            String code = response.path("code").asText();
            if (StringUtils.hasText(code) && !"SUCCESS".equals(code) && !"0".equals(code)) {
                throw new SmartCloudApiException(code, response.path("message").asText("操作失败"));
            }
            return response;
        } catch (SmartCloudApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SmartCloudApiException("DATA_PARSE_ERROR", "参数解析异常");
        }
    }

    private ObjectNode success(String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", 1);
        response.put("msg", message);
        return response;
    }

    private ObjectNode failure(String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", 0);
        response.put("msg", message);
        return response;
    }

    private ResponseEntity<String> smartError(String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", 0);
        response.put("msg", "参数错误:" + defaultText(message, "操作失败"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(writeSmartResponse(response));
    }

    private String writeSmartResponse(ObjectNode response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            return "{\"status\":0,\"msg\":\"系统内部错误\"}";
        }
    }

    private JsonNode requiredNode(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || node.isNull()) {
            throw new SmartCloudApiException("PARAM_ERROR", field + "不能为空");
        }
        return node;
    }

    private String requireText(JsonNode node, String field, String message) {
        String value = readText(node, field);
        if (!StringUtils.hasText(value)) {
            throw new SmartCloudApiException("PARAM_ERROR", message);
        }
        return value;
    }

    private String readText(JsonNode node, String... fields) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private void putIfText(ObjectNode node, String field, String value) {
        if (StringUtils.hasText(value)) {
            node.put(field, value.trim());
        }
    }

    private String linkedParams(TreeMap<String, String> params) {
        StringBuilder builder = new StringBuilder();
        Iterator<Map.Entry<String, String>> iterator = params.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (StringUtils.hasText(entry.getValue())) {
                if (!builder.isEmpty()) {
                    builder.append("&");
                }
                builder.append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
        return builder.toString();
    }

    private String md5Hex(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new SmartCloudApiException("SIGN_ERROR", "签名错误");
        }
    }

    private String encode(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private String downstreamMessage(RestClientResponseException ex) {
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            return body.path("message").asText("操作失败");
        } catch (Exception ignored) {
            return "操作失败";
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    @FunctionalInterface
    private interface SmartCloudHandler {
        ObjectNode handle(JsonNode root) throws Exception;
    }

    @FunctionalInterface
    private interface HeaderCustomizer {
        void customize(HttpHeaders headers);
    }

    private static final class SmartCloudApiException extends RuntimeException {
        private final String code;

        private SmartCloudApiException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }
}
