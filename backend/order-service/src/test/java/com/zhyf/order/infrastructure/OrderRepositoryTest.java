package com.zhyf.order.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class OrderRepositoryTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final OrderRepository repository = new OrderRepository(jdbcTemplate);

    @Test
    void shouldMapWorkflowTaskTimestampsFromOffsetDateTime() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.ofInstant(Instant.parse("2026-06-25T11:00:00Z"), ZoneOffset.UTC);
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getObject("task_id", UUID.class)).thenReturn(taskId);
        when(rs.getObject("tenant_id", UUID.class)).thenReturn(tenantId);
        when(rs.getObject("order_id", UUID.class)).thenReturn(orderId);
        when(rs.getString("task_type")).thenReturn("ORDER_REVIEW");
        when(rs.getString("task_status")).thenReturn("PENDING");
        when(rs.getString("source_event_id")).thenReturn("event-1");
        when(rs.getString("assigned_to")).thenReturn(null);
        when(rs.getString("review_comment")).thenReturn(null);
        when(rs.getString("order_no")).thenReturn("ZHYF1");
        when(rs.getString("external_order_no")).thenReturn("EXT1");
        when(rs.getString("order_status")).thenReturn("CREATED");
        when(rs.getString("validation_status")).thenReturn("PASSED");
        when(rs.getString("validation_message")).thenReturn("基础校验通过");
        when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(createdAt);
        when(rs.getObject("updated_at", OffsetDateTime.class)).thenReturn(createdAt.plusSeconds(1));
        when(rs.getObject("completed_at", OffsetDateTime.class)).thenReturn(null);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });

        var tasks = repository.findPendingReviewTasks();

        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().taskId()).isEqualTo(taskId);
        assertThat(tasks.getFirst().createdAt()).isEqualTo(createdAt.toInstant());
        assertThat(tasks.getFirst().updatedAt()).isEqualTo(createdAt.plusSeconds(1).toInstant());
        assertThat(tasks.getFirst().completedAt()).isNull();
    }
}
