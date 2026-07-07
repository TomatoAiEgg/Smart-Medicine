package com.zhyf.callback.infrastructure;

import com.zhyf.callback.application.CallbackRecords;
import com.zhyf.callback.application.CallbackSendResult;
import com.zhyf.callback.application.CallbackSender;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpCallbackSender implements CallbackSender {

    private final RestClient restClient;

    public HttpCallbackSender(RestClient.Builder builder) {
        this.restClient = builder.requestFactory(new JdkClientHttpRequestFactory()).build();
    }

    @Override
    public CallbackSendResult send(CallbackRecords.CallbackRecord record) {
        if (!StringUtils.hasText(record.requestUrl())) {
            return CallbackSendResult.failure("callback url is empty");
        }
        try {
            return restClient.post()
                    .uri(record.requestUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(record.requestBody())
                    .exchange((request, response) -> {
                        String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                        if (response.getStatusCode().is2xxSuccessful()) {
                            return CallbackSendResult.success(StringUtils.hasText(body) ? body : "HTTP "
                                    + response.getStatusCode().value());
                        }
                        return CallbackSendResult.failure("HTTP " + response.getStatusCode().value() + " " + body);
                    });
        } catch (RestClientException ex) {
            return CallbackSendResult.failure(ex.getMessage());
        }
    }
}
