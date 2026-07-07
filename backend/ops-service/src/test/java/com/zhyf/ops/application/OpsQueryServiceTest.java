package com.zhyf.ops.application;

import static org.mockito.Mockito.verify;

import com.zhyf.ops.infrastructure.OpsQueryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OpsQueryServiceTest {

    private final OpsQueryRepository repository = Mockito.mock(OpsQueryRepository.class);
    private final OpsQueryService service = new OpsQueryService(repository);

    @Test
    void shouldUseDefaultLimitWhenLimitIsInvalid() {
        service.listOutbox("NEW", "ORDER_CREATED", 0);

        verify(repository).findEventOutbox("NEW", "ORDER_CREATED", 50);
    }

    @Test
    void shouldCapLimitAtMaxLimit() {
        service.listMessageConsumeLogs("SUCCESS", "group-1", "event-1", 500);

        verify(repository).findMessageConsumeLogs("SUCCESS", "group-1", "event-1", 200);
    }
}
