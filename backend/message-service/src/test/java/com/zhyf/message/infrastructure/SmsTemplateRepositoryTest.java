package com.zhyf.message.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.message.application.SmsTemplateRecords;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class SmsTemplateRepositoryTest {

    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final SmsTemplateRepository repository = new SmsTemplateRepository(jdbcTemplate);

    @Test
    void shouldBuildSmsTemplateQueryWithFiltersAndPagination() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), anyRowMapper(), any(Object[].class))).thenReturn(List.of());

        repository.searchSmsTemplates(new SmsTemplateRecords.SmsTemplateQuery("order", "ORDER", true, 2, 15));

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from sms_template t")
                .contains("t.template_code ilike ?")
                .contains("t.template_type = ?")
                .contains("t.enabled = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "%order%",
                "%order%",
                "%order%",
                "%order%",
                "ORDER",
                true
        );

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), anyRowMapper(), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from sms_template t")
                .contains("order by enabled desc, updated_at desc, template_code asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly(
                "%order%",
                "%order%",
                "%order%",
                "%order%",
                "ORDER",
                true,
                15,
                15
        );
    }

    private static <T> RowMapper<T> anyRowMapper() {
        return any();
    }
}
