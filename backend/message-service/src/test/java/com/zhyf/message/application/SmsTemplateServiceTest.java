package com.zhyf.message.application;

import static org.mockito.Mockito.verify;

import com.zhyf.message.infrastructure.SmsTemplateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SmsTemplateServiceTest {

    private final SmsTemplateRepository repository = Mockito.mock(SmsTemplateRepository.class);
    private final SmsTemplateService service = new SmsTemplateService(repository);

    @Test
    void shouldNormalizePagingWhenListingTemplates() {
        service.listSmsTemplates(new SmsTemplateRecords.SmsTemplateQuery(" order ", " ORDER ", true, 0, 500));

        verify(repository).searchSmsTemplates(new SmsTemplateRecords.SmsTemplateQuery(
                "order",
                "ORDER",
                true,
                1,
                100
        ));
    }
}
