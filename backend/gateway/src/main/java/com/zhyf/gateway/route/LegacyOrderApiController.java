package com.zhyf.gateway.route;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

@RestController
public class LegacyOrderApiController {

    private static final String OK = "200";
    private static final String PARAM_ERROR = "440100";
    private static final String APP_KEY_ERROR = "440200";
    private static final String IP_WHITE_ERROR = "440300";
    private static final String SIGN_TIMEOUT = "440400";
    private static final String SIGN_ERROR = "440500";
    private static final String DATA_PARSE_ERROR = "440700";
    private static final String STATUS_NOT_SUPPORT_CANCEL = "440800";
    private static final String ORDER_NOT_FOUND = "440900";
    private static final String SERVER_INNER_ERROR = "441000";
    private static final String OPT_ERROR = "449999";
    private static final ZoneId LEGACY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LEGACY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(LEGACY_ZONE);

    private final InstitutionAppClient appClient;
    private final IpWhitelistChecker ipWhitelistChecker;
    private final AccessLogService accessLogService;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LegacyOrderApiController(
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

    @RequestMapping(value = "/createOrder", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createOrder(@RequestBody String rawBody, HttpServletRequest request) {
        return handleLegacy("createOrder", request, rawBody, root -> {
            JsonNode body = requiredBody(root);
            TreeMap<String, String> signParams = new TreeMap<>();
            signParams.put("orderTime", readText(body, "orderTime"));
            signParams.put("tel", readText(body, "tel"));
            signParams.put("hosRecipeNo", joinedHosRecipeNos(body));
            InstitutionAppView app = verifyLegacy(root.get("header"), signParams, "createOrder", request);

            ObjectNode currentPayload = transformCreateOrderBody(app, body, rawBody);
            String currentRawBody = objectMapper.writeValueAsString(currentPayload);
            JsonNode created = currentCreateOrder(app, currentRawBody);
            JsonNode detail = currentOrderDetail(readDataText(created, "orderNo"));
            return legacyOk("成功", legacyCreateData(created, detail));
        });
    }

    @RequestMapping(value = "/cancelOrder", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> cancelOrder(@RequestBody String rawBody, HttpServletRequest request) {
        return handleLegacy("cancelOrder", request, rawBody, root -> {
            JsonNode body = requiredBody(root);
            TreeMap<String, String> signParams = new TreeMap<>();
            signParams.put("orderId", readText(body, "orderId"));
            signParams.put("operName", readText(body, "operName"));
            signParams.put("reason", readText(body, "reason"));
            verifyLegacy(root.get("header"), signParams, "cancelOrder", request);
            String orderNo = requireText(body, "orderId", "orderId不能为空");
            ObjectNode command = objectMapper.createObjectNode();
            command.put("operator", defaultText(readText(body, "operName"), "legacy-api"));
            command.put("reason", defaultText(readText(body, "reason"), "legacy cancel"));
            currentPost("/api/admin/orders/" + encode(orderNo) + "/cancel", command);
            return legacyOk("取消成功", null);
        });
    }

    @RequestMapping(value = "/queryOrder", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> queryOrder(@RequestBody String rawBody, HttpServletRequest request) {
        return handleLegacy("queryOrder", request, rawBody, root -> {
            JsonNode body = requiredBody(root);
            TreeMap<String, String> signParams = new TreeMap<>();
            signParams.put("orderId", readText(body, "orderId"));
            verifyLegacy(root.get("header"), signParams, "queryOrder", request);
            String orderNo = requireText(body, "orderId", "orderId不能为空");
            JsonNode detail = currentOrderDetail(orderNo);
            JsonNode listItem = currentOrderListItem(orderNo);
            return legacyOk("成功", legacyQueryData(detail, listItem));
        });
    }

    @RequestMapping(value = "/updateOrderAddress", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateOrderAddress(@RequestBody String rawBody, HttpServletRequest request) {
        return handleLegacy("updateOrderAddress", request, rawBody, root -> {
            JsonNode body = requiredBody(root);
            TreeMap<String, String> signParams = new TreeMap<>();
            signParams.put("orderId", readText(body, "orderId"));
            signParams.put("consignee", readText(body, "consignee"));
            signParams.put("tel", readText(body, "tel"));
            signParams.put("province", readText(body, "province"));
            signParams.put("city", readText(body, "city"));
            signParams.put("zone", readText(body, "zone"));
            signParams.put("addrDetail", readText(body, "addrDetail"));
            signParams.put("addrType", readText(body, "addrType"));
            signParams.put("deliveryTime", readText(body, "deliveryTime"));
            verifyLegacy(root.get("header"), signParams, "updateOrderAddress", request);
            String orderNo = requireText(body, "orderId", "orderId不能为空");
            ObjectNode command = objectMapper.createObjectNode();
            command.put("receiverName", requireText(body, "consignee", "consignee不能为空"));
            command.put("receiverPhone", requireText(body, "tel", "tel不能为空"));
            command.put("receiverProvince", requireText(body, "province", "province不能为空"));
            command.put("receiverCity", requireText(body, "city", "city不能为空"));
            command.put("receiverZone", requireText(body, "zone", "zone不能为空"));
            command.put("receiverAddress", requireText(body, "addrDetail", "addrDetail不能为空"));
            command.put("addressType", requireText(body, "addrType", "addrType不能为空"));
            putIfText(command, "deliveryTime", readText(body, "deliveryTime"));
            command.put("operator", "legacy-api");
            command.put("reason", "legacy updateOrderAddress");
            currentPatch("/api/admin/orders/" + encode(orderNo) + "/address", command);
            return legacyOk("修改成功", null);
        });
    }

    @RequestMapping(value = "/getOrderId", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getOrderId(@RequestBody String rawBody, HttpServletRequest request) {
        return handleLegacy("getOrderId", request, rawBody, root -> {
            verifyLegacy(root, new TreeMap<>(), "getOrderId", request);
            return legacyOk("获取成功", "ZHYF" + Instant.now().toEpochMilli());
        });
    }

    private ResponseEntity<String> handleLegacy(
            String methodName,
            HttpServletRequest request,
            String rawBody,
            LegacyHandler handler
    ) {
        String appKey = null;
        String resultCode = OK;
        try {
            JsonNode root = objectMapper.readTree(defaultText(rawBody, "{}"));
            appKey = readText(methodName.equals("getOrderId") ? root : root.get("header"), "appKey");
            ObjectNode result = handler.handle(root);
            resultCode = result.path("code").asText(OK);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException ex) {
            resultCode = DATA_PARSE_ERROR;
            return legacyErrorResponse(DATA_PARSE_ERROR, "参数解析异常");
        } catch (LegacyApiException ex) {
            resultCode = ex.code();
            return legacyErrorResponse(ex.code(), ex.getMessage());
        } catch (BusinessException ex) {
            resultCode = mapBusinessCode(ex.code());
            return legacyErrorResponse(resultCode, ex.getMessage());
        } catch (RestClientResponseException ex) {
            resultCode = mapDownstreamError(ex);
            return legacyErrorResponse(resultCode, downstreamMessage(ex));
        } catch (Exception ex) {
            resultCode = SERVER_INNER_ERROR;
            return legacyErrorResponse(SERVER_INNER_ERROR, "系统内部错误");
        } finally {
            accessLogService.record(request.getRequestURI(), clientIp(request), appKey, null, resultCode);
        }
    }

    private InstitutionAppView verifyLegacy(
            JsonNode header,
            TreeMap<String, String> params,
            String methodName,
            HttpServletRequest request
    ) {
        String appKey = requireHeaderText(header, "appKey");
        String timestamp = requireHeaderText(header, "timestamp");
        String sign = requireHeaderText(header, "sign");
        verifyLegacyTimestamp(timestamp);
        InstitutionAppView app;
        try {
            app = appClient.getEnabledApp(appKey);
        } catch (BusinessException ex) {
            throw new LegacyApiException(APP_KEY_ERROR, ex.getMessage());
        }
        if (!app.allowsApi(methodName)) {
            throw new LegacyApiException(APP_KEY_ERROR, "接口未授权");
        }
        try {
            ipWhitelistChecker.check(app.ipWhitelist(), clientIp(request));
        } catch (BusinessException ex) {
            throw new LegacyApiException(IP_WHITE_ERROR, ex.getMessage());
        }
        String source = methodName + linkedParams(params) + appKey + timestamp + app.appSecret();
        String expected = md5Hex(source);
        if (!SignatureUtils.constantTimeEquals(expected.toLowerCase(), sign.toLowerCase())) {
            throw new LegacyApiException(SIGN_ERROR, "签名错误");
        }
        return app;
    }

    private void verifyLegacyTimestamp(String timestamp) {
        long requestSeconds;
        try {
            requestSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw new LegacyApiException(PARAM_ERROR, "timestamp格式错误");
        }
        long diffSeconds = Math.abs(Instant.now().getEpochSecond() - requestSeconds);
        if (diffSeconds > properties.signatureTimeoutSeconds()) {
            throw new LegacyApiException(SIGN_TIMEOUT, "签名过期");
        }
    }

    private ObjectNode transformCreateOrderBody(InstitutionAppView app, JsonNode body, String rawBody) {
        ObjectNode payload = body.deepCopy();
        String externalOrderNo = readText(payload, "externalOrderNo", "orderNo", "prescriptionOrderNo", "orderId");
        if (!StringUtils.hasText(externalOrderNo)) {
            String recipeNos = joinedHosRecipeNos(body);
            externalOrderNo = StringUtils.hasText(recipeNos)
                    ? "LEGACY-" + app.appKey() + "-" + recipeNos
                    : "LEGACY-" + app.appKey() + "-" + SignatureUtils.sha256Hex(rawBody).substring(0, 16);
        }
        payload.put("externalOrderNo", externalOrderNo);
        putAlias(payload, "receiverName", readText(body, "consignee"));
        putAlias(payload, "receiverPhone", readText(body, "tel"));
        putAlias(payload, "receiverAddress", readText(body, "addrDetail"));
        putAlias(payload, "receiverProvince", readText(body, "province"));
        putAlias(payload, "receiverCity", readText(body, "city"));
        putAlias(payload, "receiverZone", readText(body, "zone"));
        putAlias(payload, "addressType", readText(body, "addrType"));
        JsonNode recipes = body.get("recipeList");
        if (recipes != null && recipes.isArray()) {
            ArrayNode prescriptions = objectMapper.createArrayNode();
            for (JsonNode recipe : recipes) {
                prescriptions.add(transformPrescription(recipe));
            }
            payload.set("prescriptions", prescriptions);
            JsonNode firstRecipe = recipes.isEmpty() ? null : recipes.get(0);
            if (firstRecipe != null) {
                putAlias(payload, "patientName", readText(firstRecipe, "patientName"));
                putAlias(payload, "patientPhone", readText(firstRecipe, "patientTel"));
            }
        }
        return payload;
    }

    private ObjectNode transformPrescription(JsonNode recipe) {
        ObjectNode prescription = recipe.deepCopy();
        putAlias(prescription, "externalPrescriptionNo", readText(recipe, "hosRecipeNo"));
        putAlias(prescription, "prescriptionNo", readText(recipe, "hosRecipeNo"));
        putAlias(prescription, "prescriptionType", readText(recipe, "type"));
        putAlias(prescription, "hospitalType", readText(recipe, "recipeSource"));
        putAlias(prescription, "doseCount", readText(recipe, "amount"));
        putAlias(prescription, "boilTimes", readText(recipe, "decoctTimes"));
        putAlias(prescription, "isWithin", readText(recipe, "recipeUsage"));
        putAlias(prescription, "totalAmount", readText(recipe, "money"));
        putAlias(prescription, "diagnosis", readText(recipe, "recipeDiagnose"));
        putAlias(prescription, "departmentName", readText(recipe, "hosDepart"));
        putAlias(prescription, "wardName", readText(recipe, "hosAreaNo"));
        putAlias(prescription, "bedNo", readText(recipe, "hosBedNo"));
        putAlias(prescription, "medicationMethod", readText(recipe, "medMethod"));
        putAlias(prescription, "medicationInstruction", readText(recipe, "medGuide"));
        putAlias(prescription, "prescriptionRemark", readText(recipe, "recipeRemark"));
        Integer decoctionCount = decoctionCount(recipe);
        if (decoctionCount != null) {
            prescription.put("decoctionCount", decoctionCount);
        }
        JsonNode recipeDetails = recipe.get("recipeDetail");
        if (recipeDetails != null && recipeDetails.isArray()) {
            ArrayNode details = objectMapper.createArrayNode();
            for (JsonNode detail : recipeDetails) {
                details.add(transformPrescriptionDetail(detail));
            }
            prescription.set("details", details);
        }
        return prescription;
    }

    private ObjectNode transformPrescriptionDetail(JsonNode detail) {
        ObjectNode current = detail.deepCopy();
        putAlias(current, "drugCode", readText(detail, "herbNum"));
        putAlias(current, "medicineCode", readText(detail, "herbNum"));
        putAlias(current, "drugName", readText(detail, "herbName"));
        putAlias(current, "medicineName", readText(detail, "herbName"));
        putAlias(current, "drugSpecs", readText(detail, "goodsNorms"));
        putAlias(current, "unitPrice", readText(detail, "salePrice"));
        putAlias(current, "settlementUnitPrice", readText(detail, "settlePrice"));
        putAlias(current, "validationTips", readText(detail, "note"));
        return current;
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

    private JsonNode currentOrderDetail(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new LegacyApiException(ORDER_NOT_FOUND, "订单不存在");
        }
        return currentGet("/api/admin/orders/" + encode(orderNo) + "/detail").path("data");
    }

    private JsonNode currentOrderListItem(String orderNo) {
        JsonNode data = currentGet("/api/admin/orders?orderNo=" + encode(orderNo) + "&page=1&pageSize=1").path("data");
        JsonNode records = data.path("records");
        if (records.isArray() && !records.isEmpty()) {
            return records.get(0);
        }
        return objectMapper.createObjectNode();
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
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
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
                throw new LegacyApiException(mapBusinessCode(code), response.path("message").asText("操作失败"));
            }
            return response;
        } catch (LegacyApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LegacyApiException(DATA_PARSE_ERROR, "参数解析异常");
        }
    }

    private ObjectNode legacyCreateData(JsonNode created, JsonNode detail) {
        ObjectNode data = objectMapper.createObjectNode();
        String orderNo = readDataText(created, "orderNo");
        data.put("orderId", orderNo);
        ArrayNode recipeInfo = objectMapper.createArrayNode();
        JsonNode prescriptions = detail.path("prescriptions");
        if (prescriptions.isArray()) {
            for (JsonNode prescription : prescriptions) {
                ObjectNode item = objectMapper.createObjectNode();
                item.put("recipeId", readText(prescription, "prescriptionNo"));
                item.put("hosRecipeNo", readText(prescription, "externalPrescriptionNo"));
                recipeInfo.add(item);
            }
        }
        data.set("recipeInfo", recipeInfo);
        return data;
    }

    private ObjectNode legacyQueryData(JsonNode detail, JsonNode listItem) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("orderId", readText(detail, "orderNo"));
        String createdAt = readText(detail, "createdAt");
        data.put("orderTime", formatInstant(createdAt));
        data.put("ptOrderTime", formatInstant(createdAt));
        String status = readText(detail, "orderStatus");
        data.put("orderStatus", legacyStatus(status));
        data.put("orderStatusName", legacyStatusName(status));
        data.put("logisticsCompanyName", readText(listItem, "logisticsCompany"));
        data.put("logisticsNo", readText(listItem, "logisticsNo"));
        return data;
    }

    private ObjectNode legacyOk(String message, Object data) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("code", OK);
        response.put("message", message);
        if (data == null) {
            response.putNull("data");
        } else if (data instanceof JsonNode node) {
            response.set("data", node);
        } else {
            response.putPOJO("data", data);
        }
        return response;
    }

    private ResponseEntity<String> legacyErrorResponse(String code, String message) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("code", code);
            response.put("message", defaultText(message, "操作失败"));
            response.putNull("data");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(response));
        } catch (Exception ex) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"code\":\"441000\",\"message\":\"系统内部错误\",\"data\":null}");
        }
    }

    private JsonNode requiredBody(JsonNode root) {
        JsonNode body = root == null ? null : root.get("body");
        if (body == null || body.isNull()) {
            throw new LegacyApiException(PARAM_ERROR, "body节点不能为空");
        }
        return body;
    }

    private String requireHeaderText(JsonNode header, String field) {
        if (header == null || header.isNull()) {
            throw new LegacyApiException(PARAM_ERROR, "appKey, sign, timestamp参数不能为空");
        }
        String value = readText(header, field);
        if (!StringUtils.hasText(value)) {
            throw new LegacyApiException(PARAM_ERROR, "appKey, sign, timestamp参数不能为空");
        }
        return value;
    }

    private String requireText(JsonNode node, String field, String message) {
        String value = readText(node, field);
        if (!StringUtils.hasText(value)) {
            throw new LegacyApiException(PARAM_ERROR, message);
        }
        return value;
    }

    private String readDataText(JsonNode root, String field) {
        return readText(root.path("data"), field);
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

    private void putAlias(ObjectNode node, String field, String value) {
        if (StringUtils.hasText(value) && !node.hasNonNull(field)) {
            node.put(field, value);
        }
    }

    private void putIfText(ObjectNode node, String field, String value) {
        if (StringUtils.hasText(value)) {
            node.put(field, value);
        }
    }

    private String joinedHosRecipeNos(JsonNode body) {
        JsonNode recipes = body == null ? null : body.get("recipeList");
        if (recipes == null || !recipes.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode recipe : recipes) {
            String value = readText(recipe, "hosRecipeNo");
            if (StringUtils.hasText(value)) {
                if (!builder.isEmpty()) {
                    builder.append(",");
                }
                builder.append(value);
            }
        }
        return builder.toString();
    }

    private String linkedParams(TreeMap<String, String> params) {
        StringBuilder builder = new StringBuilder();
        Iterator<Map.Entry<String, String>> iterator = params.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            builder.append(entry.getKey()).append("=").append(defaultText(entry.getValue(), "").trim());
            if (iterator.hasNext()) {
                builder.append("&");
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
            throw new LegacyApiException(SERVER_INNER_ERROR, "系统内部错误");
        }
    }

    private String encode(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
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

    private Integer decoctionCount(JsonNode recipe) {
        Integer amount = readInteger(recipe, "amount");
        Integer decoctTimes = readInteger(recipe, "decoctTimes");
        if (amount == null || decoctTimes == null) {
            return null;
        }
        return amount * decoctTimes;
    }

    private Integer readInteger(JsonNode node, String field) {
        String value = readText(node, field);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value).intValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String formatInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            return LEGACY_TIME_FORMATTER.format(Instant.parse(value));
        } catch (Exception ex) {
            return value;
        }
    }

    private int legacyStatus(String status) {
        return switch (defaultText(status, "")) {
            case "CREATED" -> 5;
            case "REVIEW_PENDING" -> 10;
            case "REVIEWED", "PROCESSING" -> 15;
            case "DECOCTED", "PACKED" -> 25;
            case "SHIPPED" -> 37;
            case "SIGNED" -> 40;
            case "CANCELLED" -> 99;
            default -> 0;
        };
    }

    private String legacyStatusName(String status) {
        return switch (defaultText(status, "")) {
            case "CREATED" -> "已接单";
            case "REVIEW_PENDING" -> "待审核";
            case "REVIEWED" -> "已审核";
            case "PROCESSING" -> "生产中";
            case "DECOCTED" -> "已煎煮";
            case "PACKED" -> "已打包";
            case "SHIPPED" -> "已发货";
            case "SIGNED" -> "已签收";
            case "CANCELLED" -> "已取消";
            default -> defaultText(status, "");
        };
    }

    private String mapBusinessCode(String code) {
        return switch (defaultText(code, "")) {
            case "APP_NOT_FOUND", "APP_DISABLED" -> APP_KEY_ERROR;
            case "IP_NOT_ALLOWED" -> IP_WHITE_ERROR;
            case "INVALID_SIGNATURE" -> SIGN_ERROR;
            case "SIGNATURE_EXPIRED" -> SIGN_TIMEOUT;
            case "ORDER_NOT_FOUND" -> ORDER_NOT_FOUND;
            case "ORDER_CANCEL_NOT_ALLOWED" -> STATUS_NOT_SUPPORT_CANCEL;
            case "ORDER_NO_REQUIRED", "ORDER_CANCEL_REASON_REQUIRED", "RECEIVER_NAME_REQUIRED",
                    "RECEIVER_PHONE_REQUIRED", "RECEIVER_ADDRESS_REQUIRED", "ORDER_ADDRESS_COMMAND_REQUIRED",
                    "ORDER_CANCEL_COMMAND_REQUIRED" -> PARAM_ERROR;
            default -> OPT_ERROR;
        };
    }

    private String mapDownstreamError(RestClientResponseException ex) {
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            return mapBusinessCode(body.path("code").asText());
        } catch (Exception ignored) {
            return SERVER_INNER_ERROR;
        }
    }

    private String downstreamMessage(RestClientResponseException ex) {
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            return body.path("message").asText("操作失败");
        } catch (Exception ignored) {
            return "操作失败";
        }
    }

    @FunctionalInterface
    private interface LegacyHandler {
        ObjectNode handle(JsonNode root) throws Exception;
    }

    @FunctionalInterface
    private interface HeaderCustomizer {
        void customize(HttpHeaders headers);
    }

    private static final class LegacyApiException extends RuntimeException {
        private final String code;

        private LegacyApiException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }
}
