package com.zhyf.order.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhyf.order.application.AdminInstitutionApiQuery;
import com.zhyf.order.application.AdminInstitutionApiPermissionQuery;
import com.zhyf.order.application.AdminInstitutionAppQuery;
import com.zhyf.order.application.AdminInstitutionIpWhitelistQuery;
import com.zhyf.order.application.AdminInstitutionQuery;
import com.zhyf.order.application.AdminDictItemQuery;
import com.zhyf.order.application.AdminDictTypeQuery;
import com.zhyf.order.application.AdminDecoctCenterQuery;
import com.zhyf.order.application.AdminSystemConfigQuery;
import com.zhyf.order.application.AdminLabelTemplateQuery;
import com.zhyf.order.application.AdminLogisticsAddressCostQuery;
import com.zhyf.order.application.AdminLogisticsSpecialRuleQuery;
import com.zhyf.order.application.AdminOrderInterceptRuleQuery;
import com.zhyf.order.application.AdminOrderMergeQuery;
import com.zhyf.order.application.AdminOperatorQuery;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@SuppressWarnings("unchecked")
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

    @Test
    void shouldLoadOrderProgressWithCoreFulfillmentRecords() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.ofInstant(Instant.parse("2026-07-18T01:00:00Z"), ZoneOffset.UTC);
        ResultSet orderRs = org.mockito.Mockito.mock(ResultSet.class);
        when(orderRs.getObject("order_id", UUID.class)).thenReturn(orderId);
        when(orderRs.getObject("tenant_id", UUID.class)).thenReturn(tenantId);
        when(orderRs.getString("order_no")).thenReturn("ZHYF1");
        when(orderRs.getString("external_order_no")).thenReturn("EXT1");
        when(orderRs.getString("order_status")).thenReturn("RECHECKED");
        when(orderRs.getObject("created_at", OffsetDateTime.class)).thenReturn(createdAt);
        when(orderRs.getObject("updated_at", OffsetDateTime.class)).thenReturn(createdAt.plusSeconds(10));

        ResultSet prescriptionRs = org.mockito.Mockito.mock(ResultSet.class);
        when(prescriptionRs.getObject("prescription_id", UUID.class)).thenReturn(UUID.randomUUID());
        when(prescriptionRs.getString("prescription_no")).thenReturn("RX1");
        when(prescriptionRs.getString("external_prescription_no")).thenReturn("EXT-RX1");
        when(prescriptionRs.getString("prescription_status")).thenReturn("CREATED");
        when(prescriptionRs.getInt("detail_count")).thenReturn(2);
        when(prescriptionRs.getObject("created_at", OffsetDateTime.class)).thenReturn(createdAt);

        ResultSet dispenseRs = org.mockito.Mockito.mock(ResultSet.class);
        when(dispenseRs.getObject("record_id", UUID.class)).thenReturn(UUID.randomUUID());
        when(dispenseRs.getObject("task_id", UUID.class)).thenReturn(UUID.randomUUID());
        when(dispenseRs.getString("dispenser")).thenReturn("dispenser1");
        when(dispenseRs.getString("dispense_comment")).thenReturn("printed");
        when(dispenseRs.getString("print_status")).thenReturn("PRINTED");
        when(dispenseRs.getObject("dispensed_at", OffsetDateTime.class)).thenReturn(createdAt.plusSeconds(20));

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("ZHYF1"))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(orderRs, 0));
        });
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(orderId))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            RowMapper<Object> mapper = invocation.getArgument(1);
            if (sql.contains("from prescription p")) {
                return List.of(mapper.mapRow(prescriptionRs, 0));
            }
            if (sql.contains("from dispense_record d")) {
                return List.of(mapper.mapRow(dispenseRs, 0));
            }
            return List.of();
        });

        var progress = repository.findOrderProgressByOrderNo("ZHYF1").orElseThrow();

        assertThat(progress.orderNo()).isEqualTo("ZHYF1");
        assertThat(progress.orderStatus()).isEqualTo("RECHECKED");
        assertThat(progress.prescriptions()).hasSize(1);
        assertThat(progress.prescriptions().getFirst().detailCount()).isEqualTo(2);
        assertThat(progress.dispenseRecords()).hasSize(1);
        assertThat(progress.dispenseRecords().getFirst().dispenser()).isEqualTo("dispenser1");
    }

    @Test
    void shouldBuildOperatorQueryWithKeywordStatusAndPagination() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.searchAdminOperators(new AdminOperatorQuery("disp", true, 2, 10));

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from operator_user u")
                .contains("u.username ilike ?")
                .contains("u.enabled = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly("%disp%", "%disp%", "%disp%", true);

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from operator_user u")
                .contains("order by enabled desc, username asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly("%disp%", "%disp%", "%disp%", true, 10, 10);
    }

    @Test
    void shouldBuildDictTypeQueryWithKeywordStatusAndPagination() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.searchAdminDictTypes(new AdminDictTypeQuery("type", true, 2, 10));

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from dict_type t")
                .contains("t.type_code ilike ?")
                .contains("t.enabled = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly("%type%", "%type%", true);

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from dict_type t")
                .contains("order by enabled desc, type_code asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly("%type%", "%type%", true, 10, 10);
    }

    @Test
    void shouldBuildDictItemQueryWithKeywordTypeStatusAndPagination() {
        UUID typeId = UUID.fromString("11111111-2222-3333-4444-000000000701");
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.searchAdminDictItems(new AdminDictItemQuery("decoction", typeId, false, 3, 15));

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from dict_item i")
                .contains("join dict_type t on t.id = i.type_id")
                .contains("i.item_code ilike ?")
                .contains("i.type_id = ?")
                .contains("i.enabled = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "%decoction%",
                "%decoction%",
                "%decoction%",
                "%decoction%",
                "%decoction%",
                typeId,
                false
        );

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from dict_item i")
                .contains("order by t.type_code asc, i.sort_no asc, i.item_code asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly(
                "%decoction%",
                "%decoction%",
                "%decoction%",
                "%decoction%",
                "%decoction%",
                typeId,
                false,
                15,
                30
        );
    }

    @Test
    void shouldBuildSystemConfigQueryWithKeywordTypeStatusAndPagination() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.searchAdminSystemConfigs(new AdminSystemConfigQuery("sms", "BOOLEAN", true, 2, 25));

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from system_config c")
                .contains("c.config_key ilike ?")
                .contains("c.value_type = ?")
                .contains("c.enabled = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "%sms%",
                "%sms%",
                "%sms%",
                "%sms%",
                "BOOLEAN",
                true
        );

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from system_config c")
                .contains("order by enabled desc, config_key asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly(
                "%sms%",
                "%sms%",
                "%sms%",
                "%sms%",
                "BOOLEAN",
                true,
                25,
                25
        );
    }

    @Test
    void shouldBuildDecoctCenterQueryWithKeywordStatusAndPagination() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.searchAdminDecoctCenters(new AdminDecoctCenterQuery("center", false, 3, 10));

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from decoct_center c")
                .contains("c.center_code ilike ?")
                .contains("c.enabled = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "%center%",
                "%center%",
                "%center%",
                "%center%",
                "%center%",
                "%center%",
                false
        );

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from decoct_center c")
                .contains("order by enabled desc, center_code asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly(
                "%center%",
                "%center%",
                "%center%",
                "%center%",
                "%center%",
                "%center%",
                false,
                10,
                20
        );
    }

    @Test
    void shouldBuildInstitutionQueryWithKeywordStatusTypeAndPagination() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.searchAdminInstitutions(new AdminInstitutionQuery("demo", "ENABLED", "HOSPITAL", 3, 15));

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from institution i")
                .contains("i.institution_code ilike ?")
                .contains("i.status = ?")
                .contains("i.institution_type = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "%demo%",
                "%demo%",
                "%demo%",
                "ENABLED",
                "HOSPITAL"
        );

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from institution i")
                .contains("order by status asc, institution_name asc, institution_code asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly(
                "%demo%",
                "%demo%",
                "%demo%",
                "ENABLED",
                "HOSPITAL",
                15,
                30
        );
    }

    @Test
    void shouldBuildInstitutionAppQueryWithFiltersAndPagination() {
        UUID institutionId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.searchAdminInstitutionApps(new AdminInstitutionAppQuery("app", institutionId, true, 4, 12));

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from institution_app a")
                .contains("join institution i on i.id = a.institution_id")
                .contains("i.institution_code ilike ?")
                .contains("a.app_key ilike ?")
                .contains("a.institution_id = ?")
                .contains("a.enabled = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "%app%",
                "%app%",
                "%app%",
                "%app%",
                institutionId,
                true
        );

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from institution_app a")
                .contains("app_secret_configured")
                .contains("order by a.enabled desc, i.institution_name asc, a.app_key asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly(
                "%app%",
                "%app%",
                "%app%",
                "%app%",
                institutionId,
                true,
                12,
                36
        );
    }

    @Test
    void shouldBuildInstitutionApiQueryWithKeywordStatusAndPagination() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.searchAdminInstitutionApis(new AdminInstitutionApiQuery("order", true, 2, 30));

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from institution_api_definition a")
                .contains("a.api_code ilike ?")
                .contains("a.request_path ilike ?")
                .contains("a.enabled = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "%order%",
                "%order%",
                "%order%",
                "%order%",
                true
        );

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from institution_api_definition a")
                .contains("order by enabled desc, api_code asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly(
                "%order%",
                "%order%",
                "%order%",
                "%order%",
                true,
                30,
                30
        );
    }

    @Test
    void shouldBuildInstitutionApiPermissionQueryWithFiltersAndPagination() {
        UUID institutionId = UUID.randomUUID();
        UUID apiId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.searchAdminInstitutionApiPermissions(
                new AdminInstitutionApiPermissionQuery("order", institutionId, apiId, true, 3, 20)
        );

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from institution_api_permission p")
                .contains("join institution i on i.id = p.institution_id")
                .contains("join institution_api_definition a on a.id = p.api_id")
                .contains("i.institution_code ilike ?")
                .contains("a.api_code ilike ?")
                .contains("p.institution_id = ?")
                .contains("p.api_id = ?")
                .contains("p.enabled = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "%order%",
                "%order%",
                "%order%",
                "%order%",
                "%order%",
                "%order%",
                institutionId,
                apiId,
                true
        );

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from institution_api_permission p")
                .contains("order by p.enabled desc, i.institution_name asc, a.api_code asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly(
                "%order%",
                "%order%",
                "%order%",
                "%order%",
                "%order%",
                "%order%",
                institutionId,
                apiId,
                true,
                20,
                40
        );
    }

    @Test
    void shouldBuildInstitutionIpWhitelistQueryWithFiltersAndPagination() {
        UUID institutionId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.searchAdminInstitutionIpWhitelists(
                new AdminInstitutionIpWhitelistQuery("hospital", institutionId, "10.0", true, 2, 25)
        );

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from institution_ip_whitelist w")
                .contains("join institution i on i.id = w.institution_id")
                .contains("i.institution_code ilike ?")
                .contains("w.institution_id = ?")
                .contains("w.ip_range ilike ?")
                .contains("w.enabled = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "%hospital%",
                "%hospital%",
                "%hospital%",
                institutionId,
                "%10.0%",
                true
        );

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from institution_ip_whitelist w")
                .contains("order by w.enabled desc, i.institution_name asc, w.ip_range asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly(
                "%hospital%",
                "%hospital%",
                "%hospital%",
                institutionId,
                "%10.0%",
                true,
                25,
                25
        );
    }

    @Test
    void shouldBuildLogisticsSpecialRuleQueryWithFiltersAndPagination() {
        UUID institutionId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.searchAdminLogisticsSpecialRules(
                new AdminLogisticsSpecialRuleQuery("sf", institutionId, true, 3, 15)
        );

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from logistics_special_rule r")
                .contains("join institution i on i.id = r.institution_id")
                .contains("i.institution_code ilike ?")
                .contains("r.logistics_company ilike ?")
                .contains("r.institution_id = ?")
                .contains("r.enabled = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "%sf%",
                "%sf%",
                "%sf%",
                "%sf%",
                "%sf%",
                institutionId,
                true
        );

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from logistics_special_rule r")
                .contains("order by r.enabled desc, i.institution_name asc, r.rule_name asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly(
                "%sf%",
                "%sf%",
                "%sf%",
                "%sf%",
                "%sf%",
                institutionId,
                true,
                15,
                30
        );
    }

    @Test
    void shouldBuildLogisticsAddressCostQueryWithFiltersAndPagination() {
        UUID institutionId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.searchAdminLogisticsAddressCosts(
                new AdminLogisticsAddressCostQuery("sz", institutionId, "SF", true, 2, 10)
        );

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from logistics_address_cost c")
                .contains("join institution i on i.id = c.institution_id")
                .contains("i.institution_code ilike ?")
                .contains("c.province ilike ?")
                .contains("c.institution_id = ?")
                .contains("c.logistics_company ilike ?")
                .contains("c.enabled = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "%sz%",
                "%sz%",
                "%sz%",
                "%sz%",
                "%sz%",
                "%sz%",
                "%sz%",
                institutionId,
                "%SF%",
                true
        );

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from logistics_address_cost c")
                .contains("order by c.enabled desc, i.institution_name asc, c.province asc, c.city asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly(
                "%sz%",
                "%sz%",
                "%sz%",
                "%sz%",
                "%sz%",
                "%sz%",
                "%sz%",
                institutionId,
                "%SF%",
                true,
                10,
                10
        );
    }

    @Test
    void shouldBuildOrderMergeQueryWithKeywordStatusAndPagination() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.searchAdminOrderMerges(new AdminOrderMergeQuery("MG", "ACTIVE", 2, 20));

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from order_merge m")
                .contains("m.merge_no ilike ?")
                .contains("from order_merge_item mi")
                .contains("m.status = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "%MG%",
                "%MG%",
                "%MG%",
                "%MG%",
                "%MG%",
                "%MG%",
                "ACTIVE"
        );

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from order_merge m")
                .contains("left join lateral")
                .contains("order by m.created_at desc, m.merge_no desc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly(
                "%MG%",
                "%MG%",
                "%MG%",
                "%MG%",
                "%MG%",
                "%MG%",
                "ACTIVE",
                20,
                20
        );
    }

    @Test
    void shouldBuildOrderInterceptRuleQueryWithFiltersAndPagination() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.searchAdminOrderInterceptRules(
                new AdminOrderInterceptRuleQuery("phone", "CREATE_ORDER", true, 3, 10)
        );

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from order_intercept_rule r")
                .contains("r.rule_code ilike ?")
                .contains("r.intercept_stage = ?")
                .contains("r.enabled = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "%phone%",
                "%phone%",
                "%phone%",
                "%phone%",
                "%phone%",
                "CREATE_ORDER",
                true
        );

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from order_intercept_rule r")
                .contains("order by enabled desc, priority asc, rule_code asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly(
                "%phone%",
                "%phone%",
                "%phone%",
                "%phone%",
                "%phone%",
                "CREATE_ORDER",
                true,
                10,
                20
        );
    }

    @Test
    void shouldBuildLabelTemplateQueryWithFiltersAndPagination() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        UUID institutionId = UUID.randomUUID();

        repository.searchAdminLabelTemplates(
                new AdminLabelTemplateQuery("label", institutionId, "DECOCTION", true, 2, 15)
        );

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(
                countSqlCaptor.capture(),
                eq(Long.class),
                countArgsCaptor.capture()
        );
        assertThat(countSqlCaptor.getValue())
                .contains("from label_template t")
                .contains("left join institution i on i.id = t.institution_id")
                .contains("t.template_code ilike ?")
                .contains("t.institution_id = ?")
                .contains("t.prescription_type = ?")
                .contains("t.enabled = ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "%label%",
                "%label%",
                "%label%",
                "%label%",
                institutionId,
                "DECOCTION",
                true
        );

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).query(listSqlCaptor.capture(), any(RowMapper.class), listArgsCaptor.capture());
        assertThat(listSqlCaptor.getValue())
                .contains("from label_template t")
                .contains("order by t.enabled desc, t.updated_at desc, t.template_code asc limit ? offset ?");
        assertThat(listArgsCaptor.getValue()).containsExactly(
                "%label%",
                "%label%",
                "%label%",
                "%label%",
                institutionId,
                "DECOCTION",
                true,
                15,
                15
        );
    }
}
