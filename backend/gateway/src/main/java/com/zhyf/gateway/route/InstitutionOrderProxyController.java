package com.zhyf.gateway.route;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.gateway.accesslog.AccessLogService;
import com.zhyf.gateway.app.InstitutionAppClient;
import com.zhyf.gateway.app.InstitutionAppView;
import com.zhyf.gateway.config.GatewayProperties;
import com.zhyf.gateway.signature.SignatureVerifier;
import com.zhyf.gateway.whitelist.IpWhitelistChecker;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/institution")
public class InstitutionOrderProxyController {

    private final InstitutionAppClient appClient;
    private final SignatureVerifier signatureVerifier;
    private final IpWhitelistChecker ipWhitelistChecker;
    private final AccessLogService accessLogService;
    private final GatewayProperties properties;
    private final RestClient restClient;

    public InstitutionOrderProxyController(
            InstitutionAppClient appClient,
            SignatureVerifier signatureVerifier,
            IpWhitelistChecker ipWhitelistChecker,
            AccessLogService accessLogService,
            GatewayProperties properties,
            RestClient.Builder builder
    ) {
        this.appClient = appClient;
        this.signatureVerifier = signatureVerifier;
        this.ipWhitelistChecker = ipWhitelistChecker;
        this.accessLogService = accessLogService;
        this.properties = properties;
        this.restClient = builder.build();
    }

    @PostMapping("/createOrder")
    public ResponseEntity<String> createOrder(
            @RequestHeader("X-App-Key") String appKey,
            @RequestHeader("X-Timestamp") String timestamp,
            @RequestHeader("X-Signature") String signature,
            HttpServletRequest request
    ) throws Exception {
        InstitutionAppView app = null;
        String resultCode = "SUCCESS";
        try {
            String rawBody = StreamUtils.copyToString(request.getInputStream(), request.getCharacterEncoding() == null
                    ? java.nio.charset.StandardCharsets.UTF_8
                    : java.nio.charset.Charset.forName(request.getCharacterEncoding()));
            String requestIp = clientIp(request);
            app = appClient.getEnabledApp(appKey);
            ipWhitelistChecker.check(app.ipWhitelist(), requestIp);
            signatureVerifier.verify(app, appKey, timestamp, signature, rawBody);
            ResponseEntity<String> response = forwardCreateOrder(appKey, timestamp, signature, rawBody);
            resultCode = "HTTP_" + response.getStatusCode().value();
            return response;
        } catch (BusinessException ex) {
            resultCode = ex.code();
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toErrorJson(ex.code(), ex.getMessage()));
        } finally {
            accessLogService.record(request.getRequestURI(), clientIp(request), appKey, app, resultCode);
        }
    }

    private ResponseEntity<String> forwardCreateOrder(
            String appKey,
            String timestamp,
            String signature,
            String rawBody
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-App-Key", appKey);
        headers.set("X-Timestamp", timestamp);
        headers.set("X-Signature", signature);
        ResponseEntity<String> response = restClient.post()
                .uri(properties.orderServiceBaseUrl() + "/api/institution/createOrder")
                .headers(target -> target.putAll(headers))
                .body(rawBody)
                .exchange((clientRequest, clientResponse) -> ResponseEntity
                        .status(clientResponse.getStatusCode())
                        .headers(copyResponseHeaders(clientResponse.getHeaders()))
                        .body(StreamUtils.copyToString(
                                clientResponse.getBody(),
                                java.nio.charset.StandardCharsets.UTF_8
                        )));
        return ResponseEntity.status(response.getStatusCode())
                .headers(copyResponseHeaders(response.getHeaders()))
                .body(response.getBody());
    }

    private HttpHeaders copyResponseHeaders(HttpHeaders source) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(source.getContentType() == null ? MediaType.APPLICATION_JSON : source.getContentType());
        return headers;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String toErrorJson(String code, String message) {
        ApiResponse<Void> response = ApiResponse.fail(code, message);
        return """
                {"code":"%s","message":"%s","data":null}
                """.formatted(response.code(), escape(response.message()));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
