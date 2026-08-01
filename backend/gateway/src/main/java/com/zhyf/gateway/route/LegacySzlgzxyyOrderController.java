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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@RestController
@RequestMapping("/szlgzx")
public class LegacySzlgzxyyOrderController {

    private static final ZoneId LEGACY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LEGACY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(LEGACY_ZONE);

    private final InstitutionAppClient appClient;
    private final IpWhitelistChecker ipWhitelistChecker;
    private final AccessLogService accessLogService;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LegacySzlgzxyyOrderController(
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

    @PostMapping(value = "/zhyfCreateOrder", produces = MediaType.TEXT_PLAIN_VALUE)
    public String zhyfCreateOrder(@RequestBody String encryptedBody, HttpServletRequest request) {
        return handleEncrypted("zhyfCreateOrder", encryptedBody, request, this::createOrderXml);
    }

    @PostMapping(value = "/zhyfCancelOrder", produces = MediaType.TEXT_PLAIN_VALUE)
    public String zhyfCancelOrder(@RequestBody String encryptedBody, HttpServletRequest request) {
        return handleEncrypted("zhyfCancelOrder", encryptedBody, request, this::cancelOrderXml);
    }

    @PostMapping(value = "/zhyfCreateOrderTest", produces = MediaType.APPLICATION_XML_VALUE)
    public String zhyfCreateOrderTest(@RequestBody String xmlBody, HttpServletRequest request) {
        return handlePlain("zhyfCreateOrder", xmlBody, request, this::createOrderXml);
    }

    @PostMapping(value = "/zhyfCancelOrderTest", produces = MediaType.APPLICATION_XML_VALUE)
    public String zhyfCancelOrderTest(@RequestBody String xmlBody, HttpServletRequest request) {
        return handlePlain("zhyfCancelOrder", xmlBody, request, this::cancelOrderXml);
    }

    private String handleEncrypted(
            String operation,
            String encryptedBody,
            HttpServletRequest request,
            SzlgHandler handler
    ) {
        String responseXml;
        try {
            String xml = escapeXml(decryptBase64(encryptedBody));
            responseXml = handlePlain(operation, xml, request, handler);
            return encryptBase64(responseXml);
        } catch (Exception ex) {
            responseXml = failed("0001", "报文格式错误(入参格式不正确)");
            try {
                return encryptBase64(responseXml);
            } catch (Exception ignored) {
                return responseXml;
            }
        }
    }

    private String handlePlain(
            String operation,
            String xmlBody,
            HttpServletRequest request,
            SzlgHandler handler
    ) {
        String appKey = null;
        String resultCode = "0000";
        try {
            SzlgMessage message = parseMessage(xmlBody);
            appKey = message.appKey();
            String response = handler.handle(message, request);
            resultCode = response.contains("<ret_value>0000</ret_value>") ? "0000" : readResponseCode(response);
            return response;
        } catch (SzlgException ex) {
            resultCode = ex.code();
            return failed(ex.code(), ex.getMessage());
        } catch (BusinessException ex) {
            resultCode = mapBusinessCode(ex.code());
            return failed(resultCode, responseDescription(resultCode, ex.getMessage()));
        } catch (RestClientResponseException ex) {
            resultCode = mapDownstreamError(ex);
            return failed(resultCode, responseDescription(resultCode, downstreamMessage(ex)));
        } catch (Exception ex) {
            resultCode = "0001";
            return failed("0001", "报文格式错误(入参格式不正确)");
        } finally {
            accessLogService.record(request.getRequestURI(), clientIp(request), appKey, null, resultCode);
        }
    }

    private String createOrderXml(SzlgMessage message, HttpServletRequest request) throws Exception {
        InstitutionAppView app = verifyLegacy(message, "zhyfCreateOrder", "createOrder", request);
        validateCreateMessage(message);
        ObjectNode payload = transformCreatePayload(message);
        String rawBody = objectMapper.writeValueAsString(payload);
        JsonNode created = currentCreateOrder(app, rawBody);
        return createSuccess(readText(created.path("data"), "orderNo"));
    }

    private String cancelOrderXml(SzlgMessage message, HttpServletRequest request) throws Exception {
        InstitutionAppView app = verifyLegacy(message, "zhyfCancelOrder", "cancelOrder", request);
        String orderNo = requiredText(message.body(), "order_no", "0003");
        JsonNode detail = currentOrderDetail(orderNo);
        String status = readText(detail, "orderStatus");
        if (!"CANCELLED".equals(status)) {
            ObjectNode command = objectMapper.createObjectNode();
            command.put("operator", defaultText(text(message.body(), "oper_name"), "legacy-szlgzx"));
            command.put("reason", defaultText(text(message.body(), "reason"), "legacy zhyfCancelOrder"));
            currentPost("/api/admin/orders/" + encode(orderNo) + "/cancel", command);
            detail = currentOrderDetail(orderNo);
        }
        JsonNode listItem = currentOrderListItem(orderNo);
        return cancelSuccess(
                formatTime(readText(detail, "createdAt")),
                formatTime(readText(detail, "createdAt")),
                legacyStatus(readText(detail, "orderStatus")),
                readText(listItem, "logisticsCompany"),
                readText(listItem, "logisticsNo")
        );
    }

    private InstitutionAppView verifyLegacy(
            SzlgMessage message,
            String legacyApiCode,
            String currentApiCode,
            HttpServletRequest request
    ) {
        if (!StringUtils.hasText(message.appKey())
                || message.timestamp() == null
                || !StringUtils.hasText(message.sign())) {
            throw new SzlgException("0003", "字段缺失");
        }
        long diff = Math.abs(System.currentTimeMillis() - message.timestamp());
        if (diff > properties.signatureTimeoutSeconds() * 1000L) {
            throw new SzlgException("0005", "接口调用凭证不正确");
        }
        InstitutionAppView app = appClient.getEnabledApp(message.appKey());
        if (!app.allowsApi(legacyApiCode) && !app.allowsApi(currentApiCode)) {
            throw new SzlgException("0011", "接口访问无权限");
        }
        ipWhitelistChecker.check(app.ipWhitelist(), clientIp(request));
        String source = message.serviceName() + message.appKey() + message.timestamp() + app.appSecret();
        String expected = md5Hex(source).toUpperCase();
        if (!SignatureUtils.constantTimeEquals(expected, message.sign().toUpperCase())) {
            throw new SzlgException("0005", "接口调用凭证不正确");
        }
        return app;
    }

    private void validateCreateMessage(SzlgMessage message) {
        Element body = message.body();
        Element delivery = child(body, "delivery_Info");
        Element patient = child(body, "patientInfo");
        Element visit = child(body, "visitInfo");
        Element pair = child(body, "pair_list");
        if (delivery == null || patient == null || visit == null || pair == null) {
            throw new SzlgException("0003", "字段缺失");
        }
        require(delivery, "recv_name", "recv_tel", "province_name", "city_name", "area_name", "address", "add_type");
        require(patient, "patient_name", "patient_sex", "is_pregnant", "hospital_card_no");
        require(visit, "dept_name", "doctor_name", "diagnose");
        require(pair, "pres_no", "medication_method", "is_decoct", "dose");
    }

    private void require(Element element, String... names) {
        for (String name : names) {
            requiredText(element, name, "0003");
        }
    }

    private ObjectNode transformCreatePayload(SzlgMessage message) {
        Element body = message.body();
        Element delivery = child(body, "delivery_Info");
        Element patient = child(body, "patientInfo");
        Element visit = child(body, "visitInfo");
        Element pair = child(body, "pair_list");
        String presNo = requiredText(pair, "pres_no", "0003");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("externalOrderNo", "SZLGZX-" + message.appKey() + "-" + presNo);
        payload.put("source", "legacy-szlgzx");
        payload.put("hospitalId", message.appKey());
        putIfText(payload, "receiverName", text(delivery, "recv_name"));
        putIfText(payload, "receiverPhone", text(delivery, "recv_tel"));
        putIfText(payload, "receiverProvince", text(delivery, "province_name"));
        putIfText(payload, "receiverCity", text(delivery, "city_name"));
        putIfText(payload, "receiverZone", text(delivery, "area_name"));
        putIfText(payload, "receiverAddress", text(delivery, "address"));
        putIfText(payload, "addressType", text(delivery, "add_type"));
        putIfText(payload, "deliveryTime", normalizeDateTime(text(delivery, "appoint_processing_date")));
        putIfText(payload, "orderRemark", text(body, "remark"));
        putIfText(payload, "patientName", text(patient, "patient_name"));
        putIfText(payload, "patientPhone", text(patient, "patient_mp"));

        ObjectNode prescription = objectMapper.createObjectNode();
        prescription.put("externalPrescriptionNo", presNo);
        prescription.put("prescriptionNo", presNo);
        putIfText(prescription, "prescriptionType", text(pair, "type"));
        putIfText(prescription, "hospitalType", text(patient, "recipe_source"));
        putIfText(prescription, "doseCount", text(pair, "amount"));
        putIfText(prescription, "boilTimes", text(pair, "decoctTimes"));
        putIfText(prescription, "isWithin", text(pair, "medication_method"));
        putIfText(prescription, "perPackNum", text(pair, "coct_pack_num"));
        putIfText(prescription, "perPackDose", text(pair, "decoct_pack_ml"));
        putIfText(prescription, "totalAmount", text(pair, "pres_price"));
        putIfText(prescription, "diagnosis", joinText(text(visit, "diagnose"), text(visit, "symptom")));
        putIfText(prescription, "departmentName", text(visit, "dept_name"));
        putIfText(prescription, "medicationMethod", text(pair, "med_method"));
        putIfText(prescription, "medicationInstruction", text(pair, "med_guide"));
        putIfText(prescription, "prescriptionRemark", text(visit, "medical_advice"));
        putIfText(prescription, "doctorName", text(visit, "doctor_name"));

        ArrayNode details = objectMapper.createArrayNode();
        Element drugList = child(body, "drug_list");
        if (drugList != null) {
            NodeList drugs = drugList.getElementsByTagName("drug");
            for (int i = 0; i < drugs.getLength(); i++) {
                if (drugs.item(i) instanceof Element drug) {
                    ObjectNode detail = objectMapper.createObjectNode();
                    putIfText(detail, "drugCode", text(drug, "drug_code"));
                    putIfText(detail, "medicineCode", text(drug, "drug_code"));
                    putIfText(detail, "drugName", text(drug, "name"));
                    putIfText(detail, "medicineName", text(drug, "name"));
                    putIfText(detail, "dose", text(drug, "num"));
                    putIfText(detail, "quantity", text(drug, "num"));
                    putIfText(detail, "unit", text(drug, "unit"));
                    putIfText(detail, "unitPrice", text(drug, "price"));
                    putIfText(detail, "drugSpecs", text(drug, "goods_norms"));
                    putIfText(detail, "specialUsage", joinedActions(drug));
                    putIfText(detail, "remark", text(drug, "remark"));
                    details.add(detail);
                }
            }
        }
        prescription.set("details", details);
        payload.set("prescriptions", objectMapper.createArrayNode().add(prescription));
        return payload;
    }

    private SzlgMessage parseMessage(String xml) throws Exception {
        Document rootDoc = parseXml(escapeXml(xml));
        Element root = rootDoc.getDocumentElement();
        Element header = unwrapMessageNode(root, "msgHeader");
        Element body = unwrapMessageNode(root, "msgBody");
        if (header == null || body == null) {
            throw new SzlgException("0001", "报文格式错误(入参格式不正确)");
        }
        String timestamp = requiredText(header, "timestamp", "0003");
        Long timestampValue;
        try {
            timestampValue = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException ex) {
            throw new SzlgException("0005", "接口调用凭证不正确");
        }
        return new SzlgMessage(
                requiredText(header, "serviceName", "0003"),
                requiredText(header, "appKey", "0003"),
                timestampValue,
                requiredText(header, "sign", "0003"),
                body
        );
    }

    private Element unwrapMessageNode(Element root, String name) throws Exception {
        Element wrapper = child(root, name);
        if (wrapper == null) {
            return null;
        }
        if (child(wrapper, "serviceName") != null || child(wrapper, "delivery_Info") != null || child(wrapper, "order_no") != null) {
            return wrapper;
        }
        Element direct = firstElementChild(wrapper);
        if (direct != null) {
            return direct;
        }
        String content = wrapper.getTextContent();
        if (!StringUtils.hasText(content)) {
            return null;
        }
        return parseXml(escapeXml(content.trim())).getDocumentElement();
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String decryptBase64(String encryptedBody) throws Exception {
        if (!StringUtils.hasText(properties.szlgzxyyPrivateKey())) {
            throw new SzlgException("0005", "接口调用凭证不正确");
        }
        byte[] encrypted = Base64.getDecoder().decode(encryptedBody.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey(properties.szlgzxyyPrivateKey()));
        return new String(blockCipher(encrypted, 128, cipher), StandardCharsets.UTF_8);
    }

    private String encryptBase64(String plainText) throws Exception {
        if (!StringUtils.hasText(properties.szlgzxyyPublicKey())) {
            throw new SzlgException("0005", "接口调用凭证不正确");
        }
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey(properties.szlgzxyyPublicKey()));
        byte[] encrypted = blockCipher(plainText.getBytes(StandardCharsets.UTF_8), 117, cipher);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private byte[] blockCipher(byte[] bytes, int blockSize, Cipher cipher) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int index = 0;
        while (bytes.length - index * blockSize > 0) {
            int length = Math.min(blockSize, bytes.length - index * blockSize);
            out.write(cipher.doFinal(bytes, index * blockSize, length));
            index++;
        }
        return out.toByteArray();
    }

    private PublicKey publicKey(String key) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(key.getBytes(StandardCharsets.UTF_8));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }

    private PrivateKey privateKey(String key) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(key.getBytes(StandardCharsets.UTF_8));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
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
        return currentGet("/api/admin/orders/" + encode(orderNo) + "/detail").path("data");
    }

    private JsonNode currentOrderListItem(String orderNo) {
        JsonNode records = currentGet("/api/admin/orders?orderNo=" + encode(orderNo) + "&page=1&pageSize=1")
                .path("data")
                .path("records");
        return records.isArray() && !records.isEmpty() ? records.get(0) : objectMapper.createObjectNode();
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

    private JsonNode parseDownstream(String body) {
        try {
            JsonNode response = objectMapper.readTree(defaultText(body, "{}"));
            String code = response.path("code").asText();
            if (StringUtils.hasText(code) && !"SUCCESS".equals(code) && !"0".equals(code)) {
                throw new SzlgException(mapBusinessCode(code), response.path("message").asText("下游系统返回错误"));
            }
            return response;
        } catch (SzlgException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SzlgException("0001", "报文格式错误(入参格式不正确)");
        }
    }

    private String createSuccess(String orderNo) {
        return "<root><ret_value>0000</ret_value><ret_desc>操作成功</ret_desc><message><orderId>"
                + xml(orderNo) + "</orderId></message></root>";
    }

    private String cancelSuccess(
            String orderTime,
            String ptOrderTime,
            String orderStatus,
            String logisticsCompanyName,
            String logisticsNo
    ) {
        return "<root><ret_value>0000</ret_value><ret_desc>取消成功</ret_desc><message>"
                + "<order_time>" + xml(orderTime) + "</order_time>"
                + "<pt_order_time>" + xml(ptOrderTime) + "</pt_order_time>"
                + "<order_status>" + xml(orderStatus) + "</order_status>"
                + "<logistics_company_name>" + xml(logisticsCompanyName) + "</logistics_company_name>"
                + "<logistics_no>" + xml(logisticsNo) + "</logistics_no>"
                + "</message></root>";
    }

    private String failed(String retValue, String retDesc) {
        return "<root><ret_value>" + xml(retValue) + "</ret_value><ret_desc>" + xml(retDesc)
                + "</ret_desc><message></message></root>";
    }

    private String mapBusinessCode(String code) {
        return switch (defaultText(code, "")) {
            case "APP_NOT_FOUND", "APP_DISABLED", "INVALID_SIGNATURE", "SIGNATURE_EXPIRED" -> "0005";
            case "API_NOT_ALLOWED", "IP_NOT_ALLOWED" -> "0011";
            case "ORDER_NO_REQUIRED", "ORDER_CANCEL_COMMAND_REQUIRED", "ORDER_CANCEL_REASON_REQUIRED" -> "0003";
            case "ORDER_NOT_FOUND" -> "0001";
            case "ORDER_CANCEL_NOT_ALLOWED", "ORDER_CANCEL_CONFLICT" -> "0014";
            default -> "0014";
        };
    }

    private String responseDescription(String code, String fallback) {
        return switch (code) {
            case "0001" -> "报文格式错误(入参格式不正确)";
            case "0002" -> "接口不存在";
            case "0003" -> "字段缺失";
            case "0004" -> "系统未知异常，请联系管理员";
            case "0005" -> "接口调用凭证不正确";
            case "0011" -> "接口访问无权限";
            case "0012" -> "传入字符非法";
            case "0013" -> "集成引擎路由异常，入错误队列";
            case "0014" -> "下游系统返回错误";
            default -> defaultText(fallback, "下游系统返回错误");
        };
    }

    private String readResponseCode(String response) {
        int start = response.indexOf("<ret_value>");
        int end = response.indexOf("</ret_value>");
        if (start >= 0 && end > start) {
            return response.substring(start + "<ret_value>".length(), end);
        }
        return "0014";
    }

    private String mapDownstreamError(RestClientResponseException ex) {
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            return mapBusinessCode(body.path("code").asText());
        } catch (Exception ignored) {
            return "0014";
        }
    }

    private String downstreamMessage(RestClientResponseException ex) {
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            return body.path("message").asText("下游系统返回错误");
        } catch (Exception ignored) {
            return "下游系统返回错误";
        }
    }

    private Element child(Element element, String name) {
        if (element == null) {
            return null;
        }
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element child && name.equals(child.getTagName())) {
                return child;
            }
        }
        return null;
    }

    private Element firstElementChild(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                return child;
            }
        }
        return null;
    }

    private String requiredText(Element element, String name, String code) {
        String value = text(element, name);
        if (!StringUtils.hasText(value)) {
            throw new SzlgException(code, responseDescription(code, "字段缺失"));
        }
        return value.trim();
    }

    private String text(Element element, String name) {
        Element child = child(element, name);
        return child == null ? null : child.getTextContent();
    }

    private String readText(JsonNode node, String... fields) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }

    private void putIfText(ObjectNode node, String field, String value) {
        if (StringUtils.hasText(value)) {
            node.put(field, value.trim());
        }
    }

    private String joinedActions(Element drug) {
        Element actions = child(drug, "actions");
        if (actions == null) {
            return null;
        }
        NodeList actionNodes = actions.getElementsByTagName("action");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < actionNodes.getLength(); i++) {
            String value = actionNodes.item(i).getTextContent();
            if (StringUtils.hasText(value)) {
                if (!builder.isEmpty()) {
                    builder.append(",");
                }
                builder.append(value.trim());
            }
        }
        return builder.toString();
    }

    private String normalizeDateTime(String value) {
        return StringUtils.hasText(value) ? value.replace("/", "-").trim() : null;
    }

    private String joinText(String first, String second) {
        if (!StringUtils.hasText(first)) {
            return second;
        }
        if (!StringUtils.hasText(second)) {
            return first;
        }
        return first.trim() + " " + second.trim();
    }

    private String legacyStatus(String status) {
        return switch (defaultText(status, "")) {
            case "CREATED" -> "5";
            case "AUDIT_PASSED" -> "15";
            case "RECHECKED" -> "20";
            case "DECOCTING" -> "25";
            case "DECOCTED" -> "27";
            case "PACKED" -> "30";
            case "SHIPPED" -> "36";
            case "IN_TRANSIT" -> "37";
            case "SIGNED" -> "40";
            case "AUDIT_FAILED" -> "98";
            case "CANCELLED" -> "99";
            default -> status;
        };
    }

    private String formatTime(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            return LEGACY_TIME_FORMATTER.format(Instant.parse(value));
        } catch (Exception ex) {
            return value;
        }
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
            throw new SzlgException("0005", "接口调用凭证不正确");
        }
    }

    private String escapeXml(String xml) {
        return defaultText(xml, "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    private String xml(String value) {
        return defaultText(value, "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
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

    @FunctionalInterface
    private interface SzlgHandler {
        String handle(SzlgMessage message, HttpServletRequest request) throws Exception;
    }

    @FunctionalInterface
    private interface HeaderCustomizer {
        void customize(HttpHeaders headers);
    }

    private record SzlgMessage(
            String serviceName,
            String appKey,
            Long timestamp,
            String sign,
            Element body
    ) {
    }

    private static final class SzlgException extends RuntimeException {
        private final String code;

        private SzlgException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }
}
