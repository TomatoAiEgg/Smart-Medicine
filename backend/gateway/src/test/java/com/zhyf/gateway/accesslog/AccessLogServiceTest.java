package com.zhyf.gateway.accesslog;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.gateway.app.InstitutionAppView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AccessLogServiceTest {

    @Test
    void shouldNotBreakMainRequestWhenPersistFails() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db down"));
        AccessLogService service = new AccessLogService(jdbcTemplate);
        InstitutionAppView app = new InstitutionAppView(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "demo-app",
                "demo-secret",
                "HMAC_SHA256",
                null,
                true,
                List.of()
        );

        assertThatCode(() -> service.record(
                "/api/institution/createOrder",
                "127.0.0.1",
                "demo-app",
                app,
                "SUCCESS"
        )).doesNotThrowAnyException();
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }
}
