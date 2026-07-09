package com.zhyf.integration.infrastructure;

import com.zhyf.integration.application.IntegrationRecords;
import com.zhyf.integration.application.IntegrationSendResult;
import com.zhyf.integration.application.IntegrationTaskSender;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpIntegrationTaskSender implements IntegrationTaskSender {

    private final RestClient restClient;

    public HttpIntegrationTaskSender(RestClient.Builder builder) {
        this.restClient = builder.requestFactory(new JdkClientHttpRequestFactory()).build();
    }

    @Override
    public IntegrationSendResult send(IntegrationRecords.IntegrationRetryTaskRecord task) {
        if (!StringUtils.hasText(task.requestUrl())) {
            return IntegrationSendResult.failure("request url is empty");
        }
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(task.requestUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(task.requestBody() == null ? "" : task.requestBody())
                    .retrieve()
                    .toEntity(String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return IntegrationSendResult.success(response.getBody());
            }
            return IntegrationSendResult.failure("HTTP " + response.getStatusCode().value() + " " + response.getBody());
        } catch (RestClientException ex) {
            return IntegrationSendResult.failure(ex.getMessage());
        }
    }
}
