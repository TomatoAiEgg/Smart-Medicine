package com.zhyf.order.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.security.SignatureUtils;
import com.zhyf.common.status.OrderStatus;
import com.zhyf.common.status.PrescriptionStatus;
import com.zhyf.order.domain.InstitutionApp;
import com.zhyf.order.domain.OrderProgressSnapshot;
import com.zhyf.order.infrastructure.OrderRepository;
import java.math.BigDecimal;
import java.time.format.DateTimeParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderService {

    private static final ZoneId DEFAULT_PAYLOAD_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LEGACY_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter EXPORT_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(DEFAULT_PAYLOAD_ZONE);
    private static final int ADMIN_ORDER_EXPORT_LIMIT = 5000;
    private static final int ADMIN_ORDER_WAREHOUSE_EXPORT_LIMIT = 5000;
    private static final Set<OrderStatus> ADMIN_CANCELLABLE_STATUSES = EnumSet.of(
            OrderStatus.CREATED,
            OrderStatus.AUDIT_PASSED,
            OrderStatus.RECHECKED
    );
    private static final Set<OrderStatus> ADMIN_PRESCRIPTION_EDITABLE_STATUSES = EnumSet.of(
            OrderStatus.CREATED,
            OrderStatus.AUDIT_PASSED
    );
    private static final Set<OrderStatus> ADMIN_RECEIPTABLE_STATUSES = EnumSet.of(
            OrderStatus.RECHECKED,
            OrderStatus.DECOCTING,
            OrderStatus.DECOCTED,
            OrderStatus.PACKED,
            OrderStatus.SHIPPED,
            OrderStatus.IN_TRANSIT
    );
    private static final Set<OrderStatus> ADMIN_REPRINTABLE_STATUSES = EnumSet.of(
            OrderStatus.AUDIT_PASSED,
            OrderStatus.RECHECKED,
            OrderStatus.DECOCTING,
            OrderStatus.DECOCTED,
            OrderStatus.PACKED,
            OrderStatus.SHIPPED,
            OrderStatus.IN_TRANSIT,
            OrderStatus.SIGNED
    );
    private static final String ORDER_INITIALIZE_EVENT_TYPE = "ORDER_PRESCRIPTION_UPDATED";
    private static final String MANUAL_PROCESS_SOURCE = "admin-manual-process";
    private static final String MANUAL_PROCESS_LOGISTICS_COMPANY = "默认物流";
    private static final String MANUAL_PROCESS_DEVICE = "MANUAL-PROCESS";
    private static final UUID DEFAULT_ADMIN_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderCreateResult createOrder(OrderCreateCommand command) {
        String rawPayload = writeJson(command.payload());
        InstitutionApp app = orderRepository.findEnabledApp(command.appKey())
                .orElseThrow(() -> new BusinessException("APP_NOT_FOUND", "机构应用不存在或已停用"));

        verifySignature(command, app, rawPayload);

        String externalOrderNo = readText(command.payload(), "externalOrderNo", "orderNo", "prescriptionOrderNo");
        if (!StringUtils.hasText(externalOrderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "外部订单号不能为空");
        }

        return orderRepository.findOrderByExternalNo(app.tenantId(), app.institutionId(), externalOrderNo)
                .map(existing -> new OrderCreateResult(
                        existing.orderId(),
                        existing.orderNo(),
                        externalOrderNo,
                        existing.status(),
                        true
                ))
                .orElseGet(() -> createNewOrder(app, externalOrderNo, command.payload(), rawPayload));
    }

    public OrderCreateResult getOrder(String orderNo) {
        return orderRepository.findOrderByOrderNo(orderNo)
                .map(existing -> new OrderCreateResult(
                        existing.orderId(),
                        existing.orderNo(),
                        existing.externalOrderNo(),
                        existing.status(),
                        false
                ))
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
    }

    public OrderProgressSnapshot getOrderProgress(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        return orderRepository.findOrderProgressByOrderNo(orderNo.trim())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
    }

    public AdminOrderDetail getAdminOrderDetail(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        return orderRepository.findAdminOrderDetailByOrderNo(orderNo.trim())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
    }

    public AdminOperatorPage listAdminOperators(AdminOperatorQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminOperators(new AdminOperatorQuery(
                cleanText(query.keyword()),
                cleanText(query.roleCode()),
                query.enabled(),
                page,
                pageSize
        ));
    }

    public AdminOperatorRolePage listAdminOperatorRoles(AdminOperatorRoleQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminOperatorRoles(new AdminOperatorRoleQuery(
                cleanText(query.keyword()),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminOperatorRoleRecord renameAdminOperatorRole(String roleCode, AdminOperatorRoleRenameCommand command) {
        String oldRoleCode = requireText(roleCode, "OPERATOR_ROLE_CODE_REQUIRED", "Operator role code is required");
        String newRoleCode = requireText(command.roleCode(), "OPERATOR_ROLE_CODE_REQUIRED", "Operator role code is required");
        if (oldRoleCode.equals(newRoleCode)) {
            return orderRepository.findAdminOperatorRole(newRoleCode)
                    .orElseThrow(() -> new BusinessException("OPERATOR_ROLE_NOT_FOUND", "Operator role not found"));
        }
        int updated = orderRepository.renameAdminOperatorRole(oldRoleCode, newRoleCode);
        if (updated == 0) {
            throw new BusinessException("OPERATOR_ROLE_NOT_FOUND", "Operator role not found");
        }
        return orderRepository.findAdminOperatorRole(newRoleCode)
                .orElseThrow(() -> new BusinessException("OPERATOR_ROLE_NOT_FOUND", "Operator role not found"));
    }

    @Transactional
    public AdminOperatorRecord createAdminOperator(AdminOperatorCommand command) {
        String username = requireText(command.username(), "OPERATOR_USERNAME_REQUIRED", "Operator username is required");
        String displayName = requireText(command.displayName(), "OPERATOR_DISPLAY_NAME_REQUIRED", "Operator display name is required");
        if (orderRepository.findAdminOperatorByUsername(DEFAULT_ADMIN_TENANT_ID, username).isPresent()) {
            throw new BusinessException("OPERATOR_USERNAME_DUPLICATED", "Operator username already exists");
        }
        return orderRepository.insertAdminOperator(
                UUID.randomUUID(),
                DEFAULT_ADMIN_TENANT_ID,
                username,
                displayName,
                cleanText(command.roleCode()),
                command.enabled() == null || command.enabled()
        );
    }

    @Transactional
    public AdminOperatorRecord updateAdminOperator(UUID operatorId, AdminOperatorCommand command) {
        AdminOperatorRecord existing = orderRepository.findAdminOperatorById(operatorId)
                .orElseThrow(() -> new BusinessException("OPERATOR_NOT_FOUND", "Operator not found"));
        String displayName = requireText(command.displayName(), "OPERATOR_DISPLAY_NAME_REQUIRED", "Operator display name is required");
        return orderRepository.updateAdminOperator(
                existing.id(),
                displayName,
                cleanText(command.roleCode()),
                command.enabled() == null || command.enabled()
        );
    }

    public AdminDictTypePage listAdminDictTypes(AdminDictTypeQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminDictTypes(new AdminDictTypeQuery(
                cleanText(query.keyword()),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminDictTypeRecord createAdminDictType(AdminDictTypeCommand command) {
        String typeCode = requireText(command.typeCode(), "DICT_TYPE_CODE_REQUIRED", "Dict type code is required");
        String typeName = requireText(command.typeName(), "DICT_TYPE_NAME_REQUIRED", "Dict type name is required");
        if (orderRepository.findAdminDictTypeByCode(DEFAULT_ADMIN_TENANT_ID, typeCode).isPresent()) {
            throw new BusinessException("DICT_TYPE_CODE_DUPLICATED", "Dict type code already exists");
        }
        return orderRepository.insertAdminDictType(
                UUID.randomUUID(),
                DEFAULT_ADMIN_TENANT_ID,
                typeCode,
                typeName,
                command.enabled() == null || command.enabled()
        );
    }

    @Transactional
    public AdminDictTypeRecord updateAdminDictType(UUID typeId, AdminDictTypeCommand command) {
        AdminDictTypeRecord existing = orderRepository.findAdminDictTypeById(typeId)
                .orElseThrow(() -> new BusinessException("DICT_TYPE_NOT_FOUND", "Dict type not found"));
        String typeName = requireText(command.typeName(), "DICT_TYPE_NAME_REQUIRED", "Dict type name is required");
        return orderRepository.updateAdminDictType(
                existing.id(),
                typeName,
                command.enabled() == null || command.enabled()
        );
    }

    public AdminDictItemPage listAdminDictItems(AdminDictItemQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminDictItems(new AdminDictItemQuery(
                cleanText(query.keyword()),
                query.typeId(),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminDictItemRecord createAdminDictItem(AdminDictItemCommand command) {
        UUID typeId = requireUuid(command.typeId(), "DICT_TYPE_REQUIRED", "Dict type is required");
        AdminDictTypeRecord type = orderRepository.findAdminDictTypeById(typeId)
                .orElseThrow(() -> new BusinessException("DICT_TYPE_NOT_FOUND", "Dict type not found"));
        String itemCode = requireText(command.itemCode(), "DICT_ITEM_CODE_REQUIRED", "Dict item code is required");
        String itemName = requireText(command.itemName(), "DICT_ITEM_NAME_REQUIRED", "Dict item name is required");
        if (orderRepository.findAdminDictItemByCode(type.id(), itemCode).isPresent()) {
            throw new BusinessException("DICT_ITEM_CODE_DUPLICATED", "Dict item code already exists");
        }
        return orderRepository.insertAdminDictItem(
                UUID.randomUUID(),
                DEFAULT_ADMIN_TENANT_ID,
                type.id(),
                itemCode,
                itemName,
                cleanText(command.itemValue()),
                command.sortNo() == null ? 0 : Math.max(command.sortNo(), 0),
                command.enabled() == null || command.enabled(),
                cleanText(command.remark())
        );
    }

    @Transactional
    public AdminDictItemRecord updateAdminDictItem(UUID itemId, AdminDictItemCommand command) {
        AdminDictItemRecord existing = orderRepository.findAdminDictItemById(itemId)
                .orElseThrow(() -> new BusinessException("DICT_ITEM_NOT_FOUND", "Dict item not found"));
        String itemName = requireText(command.itemName(), "DICT_ITEM_NAME_REQUIRED", "Dict item name is required");
        return orderRepository.updateAdminDictItem(
                existing.id(),
                itemName,
                cleanText(command.itemValue()),
                command.sortNo() == null ? existing.sortNo() : Math.max(command.sortNo(), 0),
                command.enabled() == null ? existing.enabled() : command.enabled(),
                cleanText(command.remark())
        );
    }

    public AdminSystemConfigPage listAdminSystemConfigs(AdminSystemConfigQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminSystemConfigs(new AdminSystemConfigQuery(
                cleanText(query.keyword()),
                cleanText(query.valueType()),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminSystemConfigRecord createAdminSystemConfig(AdminSystemConfigCommand command) {
        String configKey = requireText(command.configKey(), "SYSTEM_CONFIG_KEY_REQUIRED", "Config key is required");
        String configName = requireText(command.configName(), "SYSTEM_CONFIG_NAME_REQUIRED", "Config name is required");
        String configValue = requireText(command.configValue(), "SYSTEM_CONFIG_VALUE_REQUIRED", "Config value is required");
        if (orderRepository.findAdminSystemConfigByKey(DEFAULT_ADMIN_TENANT_ID, configKey).isPresent()) {
            throw new BusinessException("SYSTEM_CONFIG_KEY_DUPLICATED", "Config key already exists");
        }
        return orderRepository.insertAdminSystemConfig(
                UUID.randomUUID(),
                DEFAULT_ADMIN_TENANT_ID,
                configKey,
                configName,
                configValue,
                defaultText(command.valueType(), "STRING"),
                command.enabled() == null || command.enabled(),
                cleanText(command.remark())
        );
    }

    @Transactional
    public AdminSystemConfigRecord updateAdminSystemConfig(UUID configId, AdminSystemConfigCommand command) {
        AdminSystemConfigRecord existing = orderRepository.findAdminSystemConfigById(configId)
                .orElseThrow(() -> new BusinessException("SYSTEM_CONFIG_NOT_FOUND", "Config not found"));
        String configName = requireText(command.configName(), "SYSTEM_CONFIG_NAME_REQUIRED", "Config name is required");
        String configValue = requireText(command.configValue(), "SYSTEM_CONFIG_VALUE_REQUIRED", "Config value is required");
        return orderRepository.updateAdminSystemConfig(
                existing.id(),
                configName,
                configValue,
                defaultText(command.valueType(), existing.valueType()),
                command.enabled() == null ? existing.enabled() : command.enabled(),
                cleanText(command.remark())
        );
    }

    public AdminDecoctCenterPage listAdminDecoctCenters(AdminDecoctCenterQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminDecoctCenters(new AdminDecoctCenterQuery(
                cleanText(query.keyword()),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminDecoctCenterRecord createAdminDecoctCenter(AdminDecoctCenterCommand command) {
        String centerCode = requireText(command.centerCode(), "DECOCT_CENTER_CODE_REQUIRED", "Center code is required");
        String centerName = requireText(command.centerName(), "DECOCT_CENTER_NAME_REQUIRED", "Center name is required");
        if (orderRepository.findAdminDecoctCenterByCode(DEFAULT_ADMIN_TENANT_ID, centerCode).isPresent()) {
            throw new BusinessException("DECOCT_CENTER_CODE_DUPLICATED", "Center code already exists");
        }
        return orderRepository.insertAdminDecoctCenter(
                UUID.randomUUID(),
                DEFAULT_ADMIN_TENANT_ID,
                centerCode,
                centerName,
                cleanText(command.contactName()),
                cleanText(command.contactPhone()),
                cleanText(command.address()),
                command.enabled() == null || command.enabled(),
                cleanText(command.remark())
        );
    }

    @Transactional
    public AdminDecoctCenterRecord updateAdminDecoctCenter(UUID centerId, AdminDecoctCenterCommand command) {
        AdminDecoctCenterRecord existing = orderRepository.findAdminDecoctCenterById(centerId)
                .orElseThrow(() -> new BusinessException("DECOCT_CENTER_NOT_FOUND", "Center not found"));
        String centerName = requireText(command.centerName(), "DECOCT_CENTER_NAME_REQUIRED", "Center name is required");
        return orderRepository.updateAdminDecoctCenter(
                existing.id(),
                centerName,
                cleanText(command.contactName()),
                cleanText(command.contactPhone()),
                cleanText(command.address()),
                command.enabled() == null ? existing.enabled() : command.enabled(),
                cleanText(command.remark())
        );
    }

    public AdminHerbPage listAdminHerbs(AdminHerbQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminHerbs(new AdminHerbQuery(
                cleanText(query.keyword()),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminHerbRecord createAdminHerb(AdminHerbCommand command) {
        String herbCode = requireText(command.herbCode(), "HERB_CODE_REQUIRED", "Herb code is required");
        String herbName = requireText(command.herbName(), "HERB_NAME_REQUIRED", "Herb name is required");
        if (orderRepository.findAdminHerbByCode(DEFAULT_ADMIN_TENANT_ID, herbCode).isPresent()) {
            throw new BusinessException("HERB_CODE_DUPLICATED", "Herb code already exists");
        }
        return orderRepository.insertAdminHerb(
                UUID.randomUUID(),
                DEFAULT_ADMIN_TENANT_ID,
                herbCode,
                herbName,
                cleanText(command.drugSpecs()),
                cleanText(command.drugOrigin()),
                cleanText(command.unit()),
                moneyOrZero(command.retailPrice(), "HERB_PRICE_INVALID"),
                command.enabled() == null || command.enabled(),
                cleanText(command.remark())
        );
    }

    @Transactional
    public AdminHerbRecord updateAdminHerb(UUID herbId, AdminHerbCommand command) {
        AdminHerbRecord existing = orderRepository.findAdminHerbById(herbId)
                .orElseThrow(() -> new BusinessException("HERB_NOT_FOUND", "Herb not found"));
        String herbName = requireText(command.herbName(), "HERB_NAME_REQUIRED", "Herb name is required");
        return orderRepository.updateAdminHerb(
                existing.id(),
                herbName,
                cleanText(command.drugSpecs()),
                cleanText(command.drugOrigin()),
                cleanText(command.unit()),
                command.retailPrice() == null ? existing.retailPrice() : moneyOrZero(command.retailPrice(), "HERB_PRICE_INVALID"),
                command.enabled() == null ? existing.enabled() : command.enabled(),
                cleanText(command.remark())
        );
    }

    public AdminHerbIndexPage listAdminHerbIndexes(AdminHerbIndexQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminHerbIndexes(new AdminHerbIndexQuery(
                cleanText(query.keyword()),
                query.institutionId(),
                query.enabled(),
                page,
                pageSize
        ));
    }

    public AdminHerbIndexOperationLogPage listAdminHerbIndexOperationLogs(AdminHerbIndexOperationLogQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminHerbIndexOperationLogs(new AdminHerbIndexOperationLogQuery(
                cleanText(query.keyword()),
                query.institutionId(),
                cleanText(query.actionType()),
                page,
                pageSize
        ));
    }

    public AdminHerbAreaPage listAdminHerbAreas(AdminHerbAreaQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminHerbAreas(new AdminHerbAreaQuery(
                cleanText(query.keyword()),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminHerbAreaRecord createAdminHerbArea(AdminHerbAreaCommand command) {
        String areaCode = requireText(command.areaCode(), "HERB_AREA_CODE_REQUIRED", "Area code is required");
        String areaName = requireText(command.areaName(), "HERB_AREA_NAME_REQUIRED", "Area name is required");
        if (orderRepository.findAdminHerbAreaByCode(DEFAULT_ADMIN_TENANT_ID, areaCode).isPresent()) {
            throw new BusinessException("HERB_AREA_CODE_DUPLICATED", "Area code already exists");
        }
        return orderRepository.insertAdminHerbArea(
                UUID.randomUUID(),
                DEFAULT_ADMIN_TENANT_ID,
                areaCode,
                areaName,
                command.enabled() == null || command.enabled(),
                cleanText(command.remark())
        );
    }

    @Transactional
    public AdminHerbAreaRecord updateAdminHerbArea(UUID areaId, AdminHerbAreaCommand command) {
        AdminHerbAreaRecord existing = orderRepository.findAdminHerbAreaById(areaId)
                .orElseThrow(() -> new BusinessException("HERB_AREA_NOT_FOUND", "Area not found"));
        String areaName = requireText(command.areaName(), "HERB_AREA_NAME_REQUIRED", "Area name is required");
        return orderRepository.updateAdminHerbArea(
                existing.id(),
                areaName,
                command.enabled() == null ? existing.enabled() : command.enabled(),
                cleanText(command.remark())
        );
    }

    @Transactional
    public AdminHerbIndexRecord createAdminHerbIndex(AdminHerbIndexCommand command) {
        UUID institutionId = requireUuid(command.institutionId(), "HERB_INDEX_INSTITUTION_REQUIRED", "Institution is required");
        UUID herbId = requireUuid(command.herbId(), "HERB_INDEX_HERB_REQUIRED", "Herb is required");
        String externalHerbCode = requireText(
                command.externalHerbCode(),
                "HERB_INDEX_EXTERNAL_CODE_REQUIRED",
                "External herb code is required"
        );
        String externalHerbName = requireText(
                command.externalHerbName(),
                "HERB_INDEX_EXTERNAL_NAME_REQUIRED",
                "External herb name is required"
        );
        orderRepository.findAdminInstitutionById(institutionId)
                .orElseThrow(() -> new BusinessException("INSTITUTION_NOT_FOUND", "Institution not found"));
        orderRepository.findAdminHerbById(herbId)
                .orElseThrow(() -> new BusinessException("HERB_NOT_FOUND", "Herb not found"));
        if (orderRepository.findAdminHerbIndexByExternalCode(
                DEFAULT_ADMIN_TENANT_ID,
                institutionId,
                externalHerbCode
        ).isPresent()) {
            throw new BusinessException("HERB_INDEX_DUPLICATED", "Herb index already exists for institution");
        }
        AdminHerbIndexRecord created = orderRepository.insertAdminHerbIndex(
                UUID.randomUUID(),
                DEFAULT_ADMIN_TENANT_ID,
                institutionId,
                externalHerbCode,
                externalHerbName,
                herbId,
                defaultText(command.matchType(), "MANUAL"),
                command.enabled() == null || command.enabled(),
                cleanText(command.remark())
        );
        writeHerbIndexLog(created, "CREATED", "admin", cleanText(command.remark()));
        return created;
    }

    @Transactional
    public AdminHerbIndexRecord updateAdminHerbIndex(UUID indexId, AdminHerbIndexCommand command) {
        AdminHerbIndexRecord existing = orderRepository.findAdminHerbIndexById(indexId)
                .orElseThrow(() -> new BusinessException("HERB_INDEX_NOT_FOUND", "Herb index not found"));
        UUID herbId = requireUuid(command.herbId(), "HERB_INDEX_HERB_REQUIRED", "Herb is required");
        String externalHerbName = requireText(
                command.externalHerbName(),
                "HERB_INDEX_EXTERNAL_NAME_REQUIRED",
                "External herb name is required"
        );
        orderRepository.findAdminHerbById(herbId)
                .orElseThrow(() -> new BusinessException("HERB_NOT_FOUND", "Herb not found"));
        AdminHerbIndexRecord updated = orderRepository.updateAdminHerbIndex(
                existing.id(),
                externalHerbName,
                herbId,
                defaultText(command.matchType(), existing.matchType()),
                command.enabled() == null ? existing.enabled() : command.enabled(),
                cleanText(command.remark())
        );
        writeHerbIndexLog(updated, herbIndexAction(existing, updated), "admin", cleanText(command.remark()));
        return updated;
    }

    public AdminInstitutionPage listAdminInstitutions(AdminInstitutionQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminInstitutions(new AdminInstitutionQuery(
                cleanText(query.keyword()),
                cleanText(query.status()),
                cleanText(query.institutionType()),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminInstitutionRecord createAdminInstitution(AdminInstitutionCommand command) {
        String institutionCode = requireText(
                command.institutionCode(),
                "INSTITUTION_CODE_REQUIRED",
                "Institution code is required"
        );
        String institutionName = requireText(
                command.institutionName(),
                "INSTITUTION_NAME_REQUIRED",
                "Institution name is required"
        );
        if (orderRepository.findAdminInstitutionByCode(DEFAULT_ADMIN_TENANT_ID, institutionCode).isPresent()) {
            throw new BusinessException("INSTITUTION_CODE_DUPLICATED", "Institution code already exists");
        }
        return orderRepository.insertAdminInstitution(
                UUID.randomUUID(),
                DEFAULT_ADMIN_TENANT_ID,
                institutionCode,
                institutionName,
                defaultText(command.institutionType(), "HOSPITAL"),
                defaultText(command.status(), "ENABLED"),
                cleanText(command.storageType())
        );
    }

    @Transactional
    public AdminInstitutionRecord updateAdminInstitution(UUID institutionId, AdminInstitutionCommand command) {
        AdminInstitutionRecord existing = orderRepository.findAdminInstitutionById(institutionId)
                .orElseThrow(() -> new BusinessException("INSTITUTION_NOT_FOUND", "Institution not found"));
        String institutionName = requireText(
                command.institutionName(),
                "INSTITUTION_NAME_REQUIRED",
                "Institution name is required"
        );
        return orderRepository.updateAdminInstitution(
                existing.id(),
                institutionName,
                defaultText(command.institutionType(), existing.institutionType()),
                defaultText(command.status(), existing.status()),
                cleanText(command.storageType())
        );
    }

    public AdminInstitutionAppPage listAdminInstitutionApps(AdminInstitutionAppQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminInstitutionApps(new AdminInstitutionAppQuery(
                cleanText(query.keyword()),
                query.institutionId(),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminInstitutionAppRecord createAdminInstitutionApp(AdminInstitutionAppCommand command) {
        UUID institutionId = command.institutionId();
        if (institutionId == null) {
            throw new BusinessException("INSTITUTION_ID_REQUIRED", "Institution is required");
        }
        AdminInstitutionRecord institution = orderRepository.findAdminInstitutionById(institutionId)
                .orElseThrow(() -> new BusinessException("INSTITUTION_NOT_FOUND", "Institution not found"));
        String appKey = requireText(command.appKey(), "APP_KEY_REQUIRED", "App key is required");
        String appSecret = requireText(command.appSecret(), "APP_SECRET_REQUIRED", "App secret is required");
        if (orderRepository.findAdminInstitutionAppByAppKey(appKey).isPresent()) {
            throw new BusinessException("APP_KEY_DUPLICATED", "App key already exists");
        }
        return orderRepository.insertAdminInstitutionApp(
                UUID.randomUUID(),
                institution.tenantId(),
                institution.id(),
                appKey,
                appSecret,
                defaultText(command.signType(), "HMAC_SHA256"),
                cleanText(command.callbackUrl()),
                command.enabled() == null || command.enabled()
        );
    }

    @Transactional
    public AdminInstitutionAppRecord updateAdminInstitutionApp(UUID appId, AdminInstitutionAppCommand command) {
        AdminInstitutionAppRecord existing = orderRepository.findAdminInstitutionAppById(appId)
                .orElseThrow(() -> new BusinessException("INSTITUTION_APP_NOT_FOUND", "Institution app not found"));
        return orderRepository.updateAdminInstitutionApp(
                existing.id(),
                cleanText(command.appSecret()),
                defaultText(command.signType(), existing.signType()),
                cleanText(command.callbackUrl()),
                command.enabled() == null ? existing.enabled() : command.enabled()
        );
    }

    public AdminInstitutionApiPage listAdminInstitutionApis(AdminInstitutionApiQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminInstitutionApis(new AdminInstitutionApiQuery(
                cleanText(query.keyword()),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminInstitutionApiRecord createAdminInstitutionApi(AdminInstitutionApiCommand command) {
        String apiCode = requireText(command.apiCode(), "API_CODE_REQUIRED", "API code is required");
        String apiName = requireText(command.apiName(), "API_NAME_REQUIRED", "API name is required");
        String requestMethod = requireText(
                command.requestMethod(),
                "API_REQUEST_METHOD_REQUIRED",
                "API request method is required"
        ).toUpperCase();
        String requestPath = requireText(command.requestPath(), "API_REQUEST_PATH_REQUIRED", "API request path is required");
        if (orderRepository.findAdminInstitutionApiByCode(apiCode).isPresent()) {
            throw new BusinessException("API_CODE_DUPLICATED", "API code already exists");
        }
        return orderRepository.insertAdminInstitutionApi(
                UUID.randomUUID(),
                apiCode,
                apiName,
                requestMethod,
                requestPath,
                cleanText(command.description()),
                command.enabled() == null || command.enabled()
        );
    }

    @Transactional
    public AdminInstitutionApiRecord updateAdminInstitutionApi(UUID apiId, AdminInstitutionApiCommand command) {
        AdminInstitutionApiRecord existing = orderRepository.findAdminInstitutionApiById(apiId)
                .orElseThrow(() -> new BusinessException("INSTITUTION_API_NOT_FOUND", "Institution API not found"));
        String apiName = requireText(command.apiName(), "API_NAME_REQUIRED", "API name is required");
        String requestMethod = requireText(
                command.requestMethod(),
                "API_REQUEST_METHOD_REQUIRED",
                "API request method is required"
        ).toUpperCase();
        String requestPath = requireText(command.requestPath(), "API_REQUEST_PATH_REQUIRED", "API request path is required");
        return orderRepository.updateAdminInstitutionApi(
                existing.id(),
                apiName,
                requestMethod,
                requestPath,
                cleanText(command.description()),
                command.enabled() == null ? existing.enabled() : command.enabled()
        );
    }

    public AdminInstitutionApiPermissionPage listAdminInstitutionApiPermissions(
            AdminInstitutionApiPermissionQuery query
    ) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminInstitutionApiPermissions(new AdminInstitutionApiPermissionQuery(
                cleanText(query.keyword()),
                query.institutionId(),
                query.apiId(),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminInstitutionApiPermissionRecord createAdminInstitutionApiPermission(
            AdminInstitutionApiPermissionCommand command
    ) {
        UUID institutionId = command.institutionId();
        UUID apiId = command.apiId();
        if (institutionId == null) {
            throw new BusinessException("INSTITUTION_ID_REQUIRED", "Institution is required");
        }
        if (apiId == null) {
            throw new BusinessException("API_ID_REQUIRED", "Institution API is required");
        }
        AdminInstitutionRecord institution = orderRepository.findAdminInstitutionById(institutionId)
                .orElseThrow(() -> new BusinessException("INSTITUTION_NOT_FOUND", "Institution not found"));
        AdminInstitutionApiRecord api = orderRepository.findAdminInstitutionApiById(apiId)
                .orElseThrow(() -> new BusinessException("INSTITUTION_API_NOT_FOUND", "Institution API not found"));
        if (orderRepository.findAdminInstitutionApiPermissionByInstitutionAndApi(institution.id(), api.id()).isPresent()) {
            throw new BusinessException("API_PERMISSION_DUPLICATED", "API permission already exists for institution");
        }
        return orderRepository.insertAdminInstitutionApiPermission(
                UUID.randomUUID(),
                institution.tenantId(),
                institution.id(),
                api.id(),
                cleanText(command.remark()),
                command.enabled() == null || command.enabled()
        );
    }

    @Transactional
    public AdminInstitutionApiPermissionRecord updateAdminInstitutionApiPermission(
            UUID permissionId,
            AdminInstitutionApiPermissionCommand command
    ) {
        AdminInstitutionApiPermissionRecord existing = orderRepository.findAdminInstitutionApiPermissionById(permissionId)
                .orElseThrow(() -> new BusinessException(
                        "API_PERMISSION_NOT_FOUND",
                        "Institution API permission not found"
                ));
        return orderRepository.updateAdminInstitutionApiPermission(
                existing.id(),
                cleanText(command.remark()),
                command.enabled() == null ? existing.enabled() : command.enabled()
        );
    }

    public AdminInstitutionIpWhitelistPage listAdminInstitutionIpWhitelists(
            AdminInstitutionIpWhitelistQuery query
    ) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminInstitutionIpWhitelists(new AdminInstitutionIpWhitelistQuery(
                cleanText(query.keyword()),
                query.institutionId(),
                cleanText(query.ipRange()),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminInstitutionIpWhitelistRecord createAdminInstitutionIpWhitelist(
            AdminInstitutionIpWhitelistCommand command
    ) {
        UUID institutionId = command.institutionId();
        if (institutionId == null) {
            throw new BusinessException("INSTITUTION_ID_REQUIRED", "Institution is required");
        }
        AdminInstitutionRecord institution = orderRepository.findAdminInstitutionById(institutionId)
                .orElseThrow(() -> new BusinessException("INSTITUTION_NOT_FOUND", "Institution not found"));
        String ipRange = requireText(command.ipRange(), "IP_RANGE_REQUIRED", "IP range is required");
        if (orderRepository.findAdminInstitutionIpWhitelistByInstitutionAndRange(institution.id(), ipRange).isPresent()) {
            throw new BusinessException("IP_RANGE_DUPLICATED", "IP range already exists for institution");
        }
        return orderRepository.insertAdminInstitutionIpWhitelist(
                UUID.randomUUID(),
                institution.tenantId(),
                institution.id(),
                ipRange,
                command.enabled() == null || command.enabled()
        );
    }

    @Transactional
    public AdminInstitutionIpWhitelistRecord updateAdminInstitutionIpWhitelist(
            UUID whitelistId,
            AdminInstitutionIpWhitelistCommand command
    ) {
        AdminInstitutionIpWhitelistRecord existing = orderRepository.findAdminInstitutionIpWhitelistById(whitelistId)
                .orElseThrow(() -> new BusinessException("IP_WHITELIST_NOT_FOUND", "IP whitelist not found"));
        String ipRange = requireText(command.ipRange(), "IP_RANGE_REQUIRED", "IP range is required");
        orderRepository.findAdminInstitutionIpWhitelistByInstitutionAndRange(existing.institutionId(), ipRange)
                .filter(duplicated -> !duplicated.id().equals(existing.id()))
                .ifPresent(duplicated -> {
                    throw new BusinessException("IP_RANGE_DUPLICATED", "IP range already exists for institution");
                });
        return orderRepository.updateAdminInstitutionIpWhitelist(
                existing.id(),
                ipRange,
                command.enabled() == null ? existing.enabled() : command.enabled()
        );
    }

    public AdminLogisticsSpecialRulePage listAdminLogisticsSpecialRules(AdminLogisticsSpecialRuleQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminLogisticsSpecialRules(new AdminLogisticsSpecialRuleQuery(
                cleanText(query.keyword()),
                query.institutionId(),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminLogisticsSpecialRuleRecord createAdminLogisticsSpecialRule(
            AdminLogisticsSpecialRuleCommand command
    ) {
        UUID institutionId = command.institutionId();
        if (institutionId == null) {
            throw new BusinessException("INSTITUTION_ID_REQUIRED", "Institution is required");
        }
        AdminInstitutionRecord institution = orderRepository.findAdminInstitutionById(institutionId)
                .orElseThrow(() -> new BusinessException("INSTITUTION_NOT_FOUND", "Institution not found"));
        String ruleName = requireText(command.ruleName(), "LOGISTICS_RULE_NAME_REQUIRED", "Rule name is required");
        String logisticsCompany = requireText(
                command.logisticsCompany(),
                "LOGISTICS_COMPANY_REQUIRED",
                "Logistics company is required"
        );
        if (orderRepository.findAdminLogisticsSpecialRuleByBusinessKey(
                institution.tenantId(),
                institution.id(),
                ruleName,
                logisticsCompany
        ).isPresent()) {
            throw new BusinessException("LOGISTICS_RULE_DUPLICATED", "Logistics rule already exists for institution");
        }
        return orderRepository.insertAdminLogisticsSpecialRule(
                UUID.randomUUID(),
                institution.tenantId(),
                institution.id(),
                ruleName,
                logisticsCompany,
                moneyOrZero(command.baseFee(), "BASE_FEE_INVALID"),
                moneyOrZero(command.extraFee(), "EXTRA_FEE_INVALID"),
                moneyOrZero(command.freeThreshold(), "FREE_THRESHOLD_INVALID"),
                cleanText(command.remark()),
                command.enabled() == null || command.enabled()
        );
    }

    @Transactional
    public AdminLogisticsSpecialRuleRecord updateAdminLogisticsSpecialRule(
            UUID ruleId,
            AdminLogisticsSpecialRuleCommand command
    ) {
        AdminLogisticsSpecialRuleRecord existing = orderRepository.findAdminLogisticsSpecialRuleById(ruleId)
                .orElseThrow(() -> new BusinessException("LOGISTICS_RULE_NOT_FOUND", "Logistics rule not found"));
        String ruleName = requireText(command.ruleName(), "LOGISTICS_RULE_NAME_REQUIRED", "Rule name is required");
        String logisticsCompany = requireText(
                command.logisticsCompany(),
                "LOGISTICS_COMPANY_REQUIRED",
                "Logistics company is required"
        );
        orderRepository.findAdminLogisticsSpecialRuleByBusinessKey(
                        existing.tenantId(),
                        existing.institutionId(),
                        ruleName,
                        logisticsCompany
                )
                .filter(duplicated -> !duplicated.id().equals(existing.id()))
                .ifPresent(duplicated -> {
                    throw new BusinessException(
                            "LOGISTICS_RULE_DUPLICATED",
                            "Logistics rule already exists for institution"
                    );
                });
        return orderRepository.updateAdminLogisticsSpecialRule(
                existing.id(),
                ruleName,
                logisticsCompany,
                moneyOrZero(command.baseFee(), "BASE_FEE_INVALID"),
                moneyOrZero(command.extraFee(), "EXTRA_FEE_INVALID"),
                moneyOrZero(command.freeThreshold(), "FREE_THRESHOLD_INVALID"),
                cleanText(command.remark()),
                command.enabled() == null ? existing.enabled() : command.enabled()
        );
    }

    public AdminLogisticsAddressCostPage listAdminLogisticsAddressCosts(AdminLogisticsAddressCostQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminLogisticsAddressCosts(new AdminLogisticsAddressCostQuery(
                cleanText(query.keyword()),
                query.institutionId(),
                cleanText(query.logisticsCompany()),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminLogisticsAddressCostRecord createAdminLogisticsAddressCost(
            AdminLogisticsAddressCostCommand command
    ) {
        UUID institutionId = command.institutionId();
        if (institutionId == null) {
            throw new BusinessException("INSTITUTION_ID_REQUIRED", "Institution is required");
        }
        AdminInstitutionRecord institution = orderRepository.findAdminInstitutionById(institutionId)
                .orElseThrow(() -> new BusinessException("INSTITUTION_NOT_FOUND", "Institution not found"));
        String logisticsCompany = requireText(
                command.logisticsCompany(),
                "LOGISTICS_COMPANY_REQUIRED",
                "Logistics company is required"
        );
        String province = requireText(command.province(), "PROVINCE_REQUIRED", "Province is required");
        String city = areaText(command.city());
        String district = areaText(command.district());
        if (orderRepository.findAdminLogisticsAddressCostByBusinessKey(
                institution.tenantId(),
                institution.id(),
                logisticsCompany,
                province,
                city,
                district
        ).isPresent()) {
            throw new BusinessException("LOGISTICS_ADDRESS_COST_DUPLICATED", "Address cost already exists");
        }
        return orderRepository.insertAdminLogisticsAddressCost(
                UUID.randomUUID(),
                institution.tenantId(),
                institution.id(),
                logisticsCompany,
                province,
                city,
                district,
                moneyOrZero(command.costAmount(), "COST_AMOUNT_INVALID"),
                cleanText(command.remark()),
                command.enabled() == null || command.enabled()
        );
    }

    @Transactional
    public AdminLogisticsAddressCostRecord updateAdminLogisticsAddressCost(
            UUID costId,
            AdminLogisticsAddressCostCommand command
    ) {
        AdminLogisticsAddressCostRecord existing = orderRepository.findAdminLogisticsAddressCostById(costId)
                .orElseThrow(() -> new BusinessException(
                        "LOGISTICS_ADDRESS_COST_NOT_FOUND",
                        "Address cost not found"
                ));
        String logisticsCompany = requireText(
                command.logisticsCompany(),
                "LOGISTICS_COMPANY_REQUIRED",
                "Logistics company is required"
        );
        String province = requireText(command.province(), "PROVINCE_REQUIRED", "Province is required");
        String city = areaText(command.city());
        String district = areaText(command.district());
        orderRepository.findAdminLogisticsAddressCostByBusinessKey(
                        existing.tenantId(),
                        existing.institutionId(),
                        logisticsCompany,
                        province,
                        city,
                        district
                )
                .filter(duplicated -> !duplicated.id().equals(existing.id()))
                .ifPresent(duplicated -> {
                    throw new BusinessException("LOGISTICS_ADDRESS_COST_DUPLICATED", "Address cost already exists");
                });
        return orderRepository.updateAdminLogisticsAddressCost(
                existing.id(),
                logisticsCompany,
                province,
                city,
                district,
                moneyOrZero(command.costAmount(), "COST_AMOUNT_INVALID"),
                cleanText(command.remark()),
                command.enabled() == null ? existing.enabled() : command.enabled()
        );
    }

    public AdminOrderMergePage listAdminOrderMerges(AdminOrderMergeQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminOrderMerges(new AdminOrderMergeQuery(
                cleanText(query.keyword()),
                cleanText(query.status()),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminOrderMergeRecord createAdminOrderMerge(AdminOrderMergeCommand command) {
        List<String> orderNos = normalizeOrderMergeOrderNos(command.orderNos());
        List<AdminOrderMergeCandidate> candidates = orderRepository.findAdminOrderMergeCandidates(orderNos);
        Map<String, AdminOrderMergeCandidate> candidateByOrderNo = new LinkedHashMap<>();
        for (AdminOrderMergeCandidate candidate : candidates) {
            candidateByOrderNo.put(candidate.orderNo(), candidate);
        }
        List<String> missingOrderNos = orderNos.stream()
                .filter(orderNo -> !candidateByOrderNo.containsKey(orderNo))
                .toList();
        if (!missingOrderNos.isEmpty()) {
            throw new BusinessException(
                    "ORDER_MERGE_ORDER_NOT_FOUND",
                    "Order not found: " + String.join(", ", missingOrderNos)
            );
        }
        Set<UUID> tenantIds = new LinkedHashSet<>();
        for (AdminOrderMergeCandidate candidate : candidates) {
            tenantIds.add(candidate.tenantId());
            if (orderRepository.existsActiveOrderMergeItem(candidate.orderId())) {
                throw new BusinessException(
                        "ORDER_MERGE_ORDER_ALREADY_ACTIVE",
                        "Order already has active merge: " + candidate.orderNo()
                );
            }
        }
        if (tenantIds.size() != 1) {
            throw new BusinessException("ORDER_MERGE_TENANT_MISMATCH", "Orders must belong to same tenant");
        }
        UUID mergeId = UUID.randomUUID();
        UUID tenantId = tenantIds.iterator().next();
        AdminOrderMergeRecord merge = orderRepository.insertAdminOrderMerge(
                mergeId,
                tenantId,
                "MG" + Instant.now().toEpochMilli(),
                cleanText(command.logisticsCompany()),
                cleanText(command.logisticsNo()),
                cleanText(command.remark())
        );
        for (String orderNo : orderNos) {
            AdminOrderMergeCandidate candidate = candidateByOrderNo.get(orderNo);
            orderRepository.insertAdminOrderMergeItem(
                    UUID.randomUUID(),
                    tenantId,
                    mergeId,
                    candidate.orderId(),
                    candidate.orderNo()
            );
        }
        return orderRepository.findAdminOrderMergeById(merge.id()).orElseThrow();
    }

    @Transactional
    public AdminOrderMergeRecord cancelAdminOrderMerge(UUID mergeId, AdminOrderMergeCommand command) {
        AdminOrderMergeRecord existing = orderRepository.findAdminOrderMergeById(mergeId)
                .orElseThrow(() -> new BusinessException("ORDER_MERGE_NOT_FOUND", "Order merge not found"));
        if ("CANCELLED".equals(existing.status())) {
            return existing;
        }
        return orderRepository.cancelAdminOrderMerge(existing.id(), cleanText(command.remark()));
    }

    public AdminOrderInterceptRulePage listAdminOrderInterceptRules(AdminOrderInterceptRuleQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminOrderInterceptRules(new AdminOrderInterceptRuleQuery(
                cleanText(query.keyword()),
                cleanText(query.interceptStage()),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminOrderInterceptRuleRecord createAdminOrderInterceptRule(AdminOrderInterceptRuleCommand command) {
        String ruleCode = requireText(command.ruleCode(), "INTERCEPT_RULE_CODE_REQUIRED", "Rule code is required");
        if (orderRepository.findAdminOrderInterceptRuleByCode(DEFAULT_ADMIN_TENANT_ID, ruleCode).isPresent()) {
            throw new BusinessException("INTERCEPT_RULE_CODE_DUPLICATED", "Rule code already exists");
        }
        return orderRepository.insertAdminOrderInterceptRule(
                UUID.randomUUID(),
                DEFAULT_ADMIN_TENANT_ID,
                ruleCode,
                requireText(command.ruleName(), "INTERCEPT_RULE_NAME_REQUIRED", "Rule name is required"),
                defaultText(command.interceptStage(), "CREATE_ORDER"),
                requireText(command.matchField(), "INTERCEPT_MATCH_FIELD_REQUIRED", "Match field is required"),
                defaultText(command.matchType(), "CONTAINS"),
                requireText(command.matchValue(), "INTERCEPT_MATCH_VALUE_REQUIRED", "Match value is required"),
                requireText(command.reason(), "INTERCEPT_REASON_REQUIRED", "Intercept reason is required"),
                priorityOrDefault(command.priority()),
                command.enabled() == null || command.enabled()
        );
    }

    @Transactional
    public AdminOrderInterceptRuleRecord updateAdminOrderInterceptRule(
            UUID ruleId,
            AdminOrderInterceptRuleCommand command
    ) {
        AdminOrderInterceptRuleRecord existing = orderRepository.findAdminOrderInterceptRuleById(ruleId)
                .orElseThrow(() -> new BusinessException(
                        "INTERCEPT_RULE_NOT_FOUND",
                        "Order intercept rule not found"
                ));
        return orderRepository.updateAdminOrderInterceptRule(
                existing.id(),
                requireText(command.ruleName(), "INTERCEPT_RULE_NAME_REQUIRED", "Rule name is required"),
                defaultText(command.interceptStage(), existing.interceptStage()),
                requireText(command.matchField(), "INTERCEPT_MATCH_FIELD_REQUIRED", "Match field is required"),
                defaultText(command.matchType(), existing.matchType()),
                requireText(command.matchValue(), "INTERCEPT_MATCH_VALUE_REQUIRED", "Match value is required"),
                requireText(command.reason(), "INTERCEPT_REASON_REQUIRED", "Intercept reason is required"),
                priorityOrDefault(command.priority()),
                command.enabled() == null ? existing.enabled() : command.enabled()
        );
    }

    public AdminLabelTemplatePage listAdminLabelTemplates(AdminLabelTemplateQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        return orderRepository.searchAdminLabelTemplates(new AdminLabelTemplateQuery(
                cleanText(query.keyword()),
                query.institutionId(),
                cleanText(query.prescriptionType()),
                query.enabled(),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminLabelTemplateRecord createAdminLabelTemplate(AdminLabelTemplateCommand command) {
        String templateCode = requireText(command.templateCode(), "LABEL_TEMPLATE_CODE_REQUIRED", "Template code is required");
        if (orderRepository.findAdminLabelTemplateByCode(DEFAULT_ADMIN_TENANT_ID, templateCode).isPresent()) {
            throw new BusinessException("LABEL_TEMPLATE_CODE_DUPLICATED", "Template code already exists");
        }
        UUID institutionId = verifiedTemplateInstitutionId(command.scopeType(), command.institutionId());
        return orderRepository.insertAdminLabelTemplate(
                UUID.randomUUID(),
                DEFAULT_ADMIN_TENANT_ID,
                templateCode,
                requireText(command.templateName(), "LABEL_TEMPLATE_NAME_REQUIRED", "Template name is required"),
                defaultText(command.scopeType(), "GLOBAL"),
                institutionId,
                cleanText(command.prescriptionType()),
                labelDimension(command.labelWidthMm(), 90, "LABEL_WIDTH_INVALID"),
                labelDimension(command.labelHeightMm(), 60, "LABEL_HEIGHT_INVALID"),
                requireText(command.contentTemplate(), "LABEL_TEMPLATE_CONTENT_REQUIRED", "Template content is required"),
                command.enabled() == null || command.enabled()
        );
    }

    @Transactional
    public AdminLabelTemplateRecord updateAdminLabelTemplate(UUID templateId, AdminLabelTemplateCommand command) {
        AdminLabelTemplateRecord existing = orderRepository.findAdminLabelTemplateById(templateId)
                .orElseThrow(() -> new BusinessException(
                        "LABEL_TEMPLATE_NOT_FOUND",
                        "Label template not found"
                ));
        String scopeType = defaultText(command.scopeType(), existing.scopeType());
        UUID requestedInstitutionId = command.institutionId() == null ? existing.institutionId() : command.institutionId();
        UUID institutionId = verifiedTemplateInstitutionId(scopeType, requestedInstitutionId);
        return orderRepository.updateAdminLabelTemplate(
                existing.id(),
                requireText(command.templateName(), "LABEL_TEMPLATE_NAME_REQUIRED", "Template name is required"),
                scopeType,
                institutionId,
                cleanText(command.prescriptionType()),
                labelDimension(command.labelWidthMm(), existing.labelWidthMm(), "LABEL_WIDTH_INVALID"),
                labelDimension(command.labelHeightMm(), existing.labelHeightMm(), "LABEL_HEIGHT_INVALID"),
                requireText(command.contentTemplate(), "LABEL_TEMPLATE_CONTENT_REQUIRED", "Template content is required"),
                command.enabled() == null ? existing.enabled() : command.enabled()
        );
    }

    public AdminOrderPage listAdminOrders(AdminOrderSearchQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        AdminOrderSearchQuery normalized = new AdminOrderSearchQuery(
                query.startTime(),
                query.endTime(),
                query.institution(),
                query.prescriptionType(),
                query.hospitalType(),
                query.orderStatus(),
                query.excludeOrderStatus(),
                query.decoctionCenter(),
                query.deliveryType(),
                query.logisticsCompany(),
                query.province(),
                query.keyword(),
                query.hospitalPrescriptionNo(),
                query.patientName(),
                query.receiverPhone(),
                page,
                pageSize
        );
        return orderRepository.searchAdminOrders(normalized);
    }

    public AdminManualProcessPage listAdminManualProcessOrders(AdminManualProcessQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        AdminManualProcessQuery normalized = new AdminManualProcessQuery(
                query.startTime(),
                query.endTime(),
                query.institution(),
                query.prescriptionType(),
                query.hospitalType(),
                query.isWithin(),
                defaultText(query.processType(), "PENDING"),
                query.deliveryType(),
                query.orderNo(),
                query.prescriptionNo(),
                query.hospitalPrescriptionNo(),
                query.patientName(),
                cleanText(query.doseRange()),
                page,
                pageSize
        );
        return orderRepository.searchAdminManualProcessOrders(normalized);
    }

    public AdminOrderRecheckPage listAdminOrderRechecks(AdminOrderRecheckQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        AdminOrderRecheckQuery normalized = new AdminOrderRecheckQuery(
                query.startTime(),
                query.endTime(),
                query.institution(),
                query.prescriptionType(),
                query.hospitalType(),
                query.isWithin(),
                query.deliveryType(),
                normalizeAdminRecheckStatus(query.recheckStatus()),
                query.batchNo(),
                query.prescriptionNo(),
                query.dispenser(),
                query.rechecker(),
                page,
                pageSize
        );
        return orderRepository.searchAdminOrderRechecks(normalized);
    }

    public AdminOrderReviewPage listAdminOrderReviews(AdminOrderReviewQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        String reviewStatus = normalizeAdminReviewStatus(query.reviewStatus());
        AdminOrderReviewQuery normalized = new AdminOrderReviewQuery(
                "REVIEWED".equals(reviewStatus) ? query.startTime() : null,
                "REVIEWED".equals(reviewStatus) ? query.endTime() : null,
                cleanText(query.institution()),
                cleanText(query.prescriptionType()),
                cleanText(query.hospitalType()),
                query.isWithin(),
                cleanText(query.deliveryType()),
                reviewStatus,
                cleanText(query.orderNo()),
                cleanText(query.prescriptionNo()),
                cleanText(query.hospitalPrescriptionNo()),
                cleanText(query.patientName()),
                cleanText(query.doseRange()),
                page,
                pageSize
        );
        return orderRepository.searchAdminOrderReviews(normalized);
    }

    public AdminOrderWarehousePage listAdminOrderWarehouses(AdminOrderWarehouseQuery query) {
        AdminOrderWarehouseQuery normalized = normalizeOrderWarehouseQuery(query, false);
        return orderRepository.searchAdminOrderWarehouses(normalized);
    }

    public String exportAdminOrderWarehousesCsv(AdminOrderWarehouseQuery query) {
        AdminOrderWarehouseQuery normalized = normalizeOrderWarehouseQuery(query, true);
        List<AdminOrderWarehouseItem> rows = orderRepository.exportAdminOrderWarehouses(
                normalized,
                ADMIN_ORDER_WAREHOUSE_EXPORT_LIMIT
        );
        StringBuilder builder = new StringBuilder();
        builder.append('\ufeff');
        appendCsvRow(builder, List.of(
                "订单号",
                "订单状态",
                "接单时间",
                "批次",
                "医疗机构",
                "送货方式",
                "收货人",
                "收货电话",
                "收货时间",
                "收货地址",
                "门诊住院",
                "病人姓名",
                "病人年龄",
                "科室",
                "处方类型",
                "剂数",
                "包数",
                "每包剂量"
        ));
        for (AdminOrderWarehouseItem row : rows) {
            appendCsvRow(builder, List.of(
                    value(row.orderNo()),
                    orderStatusText(row.orderStatus()),
                    dateTime(row.createdAt()),
                    batchText(row.batchNo()),
                    value(row.institutionName()),
                    deliveryTypeText(row.addressType()),
                    value(row.receiverName()),
                    value(row.receiverPhone()),
                    dateTime(row.deliveryTime()),
                    warehouseAddress(row),
                    hospitalTypeText(row.hospitalTypes()),
                    value(row.patientName()),
                    value(row.patientAge()),
                    value(row.departmentNames()),
                    prescriptionTypeText(row.prescriptionTypes()),
                    value(row.doseCounts()),
                    value(row.perPackNums()),
                    value(row.perPackDoses())
            ));
        }
        return builder.toString();
    }

    public String exportAdminOrdersCsv(AdminOrderSearchQuery query) {
        AdminOrderSearchQuery normalized = new AdminOrderSearchQuery(
                query.startTime(),
                query.endTime(),
                query.institution(),
                query.prescriptionType(),
                query.hospitalType(),
                query.orderStatus(),
                query.excludeOrderStatus(),
                query.decoctionCenter(),
                query.deliveryType(),
                query.logisticsCompany(),
                query.province(),
                query.keyword(),
                query.hospitalPrescriptionNo(),
                query.patientName(),
                query.receiverPhone(),
                1,
                ADMIN_ORDER_EXPORT_LIMIT
        );
        List<AdminOrderListItem> rows = orderRepository.exportAdminOrders(normalized, ADMIN_ORDER_EXPORT_LIMIT);
        StringBuilder builder = new StringBuilder();
        builder.append('\ufeff');
        appendCsvRow(builder, List.of(
                "平台处方号",
                "平台订单号",
                "订单时间",
                "煎煮中心",
                "机构名称",
                "机构处方号",
                "病人姓名",
                "门诊住院",
                "处方类型",
                "剂数",
                "处方金额",
                "收货人",
                "收货电话",
                "收货信息",
                "收货时间",
                "送货方式",
                "订单状态",
                "批次",
                "物流公司",
                "物流单号",
                "物流状态",
                "订单备注"
        ));
        for (AdminOrderListItem row : rows) {
            appendCsvRow(builder, List.of(
                    value(row.prescriptionNos()),
                    value(row.orderNo()),
                    dateTime(row.createdAt()),
                    value(row.storageType()),
                    value(row.institutionName()),
                    value(row.externalPrescriptionNos()),
                    value(row.patientName()),
                    hospitalTypeText(row.hospitalTypes()),
                    prescriptionTypeText(row.prescriptionTypes()),
                    value(row.doseCount()),
                    value(row.totalAmount()),
                    value(row.receiverName()),
                    value(row.receiverPhone()),
                    receiverAddress(row),
                    dateTime(row.deliveryTime()),
                    deliveryTypeText(row.addressType()),
                    orderStatusText(row.orderStatus()),
                    batchText(row.batchNo()),
                    value(row.logisticsCompany()),
                    value(row.logisticsNo()),
                    orderStatusText(row.logisticsStatus()),
                    value(row.orderRemark())
            ));
        }
        return builder.toString();
    }

    public AdminOrderReceiptPage listAdminOrderReceipts(AdminOrderReceiptQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        AdminOrderReceiptQuery normalized = new AdminOrderReceiptQuery(
                query.prescriptionNo(),
                query.receiverName(),
                query.receiverPhone(),
                query.patientName(),
                page,
                pageSize
        );
        return orderRepository.searchAdminOrderReceipts(normalized, receiptableStatusNames());
    }

    public AdminPrescriptionReprintPage listAdminPrescriptionReprints(AdminPrescriptionReprintQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        AdminPrescriptionReprintQuery normalized = new AdminPrescriptionReprintQuery(
                query.startTime(),
                query.endTime(),
                query.prescriptionNo(),
                page,
                pageSize
        );
        return orderRepository.searchAdminPrescriptionReprints(normalized, reprintableStatusNames());
    }

    public AdminLabelPrintRecordPage listAdminLabelPrintRecords(AdminLabelPrintRecordQuery query) {
        int page = Math.max(query.page(), 1);
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        String printStatus = cleanText(query.printStatus());
        if (StringUtils.hasText(printStatus)) {
            printStatus = normalizeLabelPrintStatus(printStatus);
        }
        return orderRepository.searchAdminLabelPrintRecords(new AdminLabelPrintRecordQuery(
                printStatus,
                cleanText(query.prescriptionNo()),
                page,
                pageSize
        ));
    }

    @Transactional
    public AdminLabelPrintRecord createAdminLabelPrintRecord(
            String prescriptionNo,
            AdminLabelPrintRecordCommand command
    ) {
        if (command == null) {
            throw new BusinessException("LABEL_PRINT_RECORD_COMMAND_REQUIRED", "标签打印记录参数不能为空");
        }
        String normalizedPrescriptionNo = requireText(
                prescriptionNo,
                "PRESCRIPTION_NO_REQUIRED",
                "处方号不能为空"
        );
        String printStatus = normalizeLabelPrintStatus(requireText(
                command.printStatus(),
                "LABEL_PRINT_STATUS_REQUIRED",
                "标签打印状态不能为空"
        ));
        String orderNo = orderRepository.findOrderNoByPrescriptionNo(normalizedPrescriptionNo)
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
        AdminOrderDetail detail = getAdminOrderDetail(orderNo);
        AdminOrderDetail.Prescription prescription = detail.prescriptions().stream()
                .filter(item -> normalizedPrescriptionNo.equals(item.prescriptionNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
        String printChannel = normalizeLabelPrintChannel(command.printChannel());
        String failureReason = "FAILED".equals(printStatus)
                ? defaultText(command.failureReason(), "CLOUD".equals(printChannel) ? "标签云打印失败" : "浏览器标签打印失败")
                : cleanText(command.failureReason());
        String requestParam = defaultText(command.requestParam(), labelPrintRequestParam(
                detail,
                prescription,
                printChannel,
                command
        ));
        return orderRepository.insertAdminLabelPrintRecord(
                UUID.randomUUID(),
                detail.tenantId(),
                detail.orderId(),
                prescription.prescriptionId(),
                detail.orderNo(),
                detail.externalOrderNo(),
                prescription.prescriptionNo(),
                prescription.externalPrescriptionNo(),
                detail.institutionName(),
                detail.patientName(),
                printStatus,
                printChannel,
                cleanText(command.printerCode()),
                cleanText(command.printerName()),
                defaultText(command.provider(), "CLOUD".equals(printChannel) ? "POSCOM" : null),
                cleanText(command.providerTaskNo()),
                command.templateId(),
                cleanText(command.templateName()),
                requestParam,
                cleanText(command.responseBody()),
                failureReason,
                defaultText(command.operator(), "admin"),
                command.retryOf()
        );
    }

    public AdminPrescriptionPrintPayload getAdminPrescriptionPrintPayload(String prescriptionNo) {
        String normalizedPrescriptionNo = requireText(
                prescriptionNo,
                "PRESCRIPTION_NO_REQUIRED",
                "处方号不能为空"
        );
        String orderNo = orderRepository.findOrderNoByPrescriptionNo(normalizedPrescriptionNo)
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
        AdminOrderDetail detail = getAdminOrderDetail(orderNo);
        AdminOrderDetail.Prescription prescription = detail.prescriptions().stream()
                .filter(item -> normalizedPrescriptionNo.equals(item.prescriptionNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
        return new AdminPrescriptionPrintPayload(
                detail.orderId(),
                prescription.prescriptionId(),
                detail.orderNo(),
                detail.externalOrderNo(),
                detail.orderStatus(),
                detail.institutionName(),
                detail.patientName(),
                detail.patientPhone(),
                detail.receiverName(),
                detail.receiverPhone(),
                detail.receiverProvince(),
                detail.receiverCity(),
                detail.receiverZone(),
                detail.receiverAddress(),
                detail.addressType(),
                detail.deliveryTime(),
                detail.batchNo(),
                prescription.prescriptionNo(),
                prescription.externalPrescriptionNo(),
                prescription.prescriptionType(),
                prescription.prescriptionStatus(),
                prescription.hospitalType(),
                prescription.doseCount(),
                prescription.decoctionCount(),
                prescription.boilTimes(),
                prescription.isWithin(),
                prescription.perPackNum(),
                prescription.perPackDose(),
                prescription.totalAmount(),
                prescription.doctorName(),
                prescription.diagnosis(),
                prescription.departmentName(),
                prescription.wardName(),
                prescription.bedNo(),
                prescription.medicationMethod(),
                prescription.medicationInstruction(),
                prescription.prescriptionRemark(),
                prescription.details(),
                Instant.now()
        );
    }

    public LegacyPdaLabelPrintInitResult getLegacyPdaLabelPrintInit(String recipeId) {
        String normalizedRecipeId = requireText(recipeId, "RECIPE_ID_REQUIRED", "处方号不能为空");
        String prescriptionNo = orderRepository.findPrescriptionNoByLegacyPdaRecipeId(normalizedRecipeId)
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
        AdminPrescriptionPrintPayload payload = getAdminPrescriptionPrintPayload(prescriptionNo);
        Integer totalPackNum = payload.perPackNum() == null || payload.doseCount() == null
                ? null
                : payload.perPackNum() * payload.doseCount();
        String withinName = payload.isWithin() == null ? "" : payload.isWithin() == 0 ? "内服" : "外用";
        String productionDate = DateTimeFormatter.ISO_LOCAL_DATE.withZone(DEFAULT_PAYLOAD_ZONE)
                .format(Instant.now());
        List<LegacyPdaLabelPrintInfo> info = legacyPdaLabelInfo(payload, withinName);
        return new LegacyPdaLabelPrintInitResult(
                payload.prescriptionNo(),
                null,
                value(payload.patientName()),
                null,
                null,
                withinName,
                payload.doseCount(),
                payload.doseCount() == null ? "" : "剂数:" + payload.doseCount() + "剂",
                payload.decoctionCount(),
                payload.boilTimes(),
                totalPackNum,
                value(payload.institutionName()),
                value(payload.externalPrescriptionNo()),
                value(payload.departmentName()),
                productionDate,
                value(payload.medicationMethod()),
                value(payload.wardName()),
                value(payload.bedNo()),
                List.of(),
                null,
                null,
                "2",
                "1",
                "1",
                info,
                info.stream().map(LegacyPdaLabelPrintInfo::value).toList()
        );
    }

    public LegacyPdaLabelPrintResult createLegacyPdaLabelPrintRecord(
            String recipeId,
            Integer printNum,
            String dmjCode,
            String dmjIp,
            String operator
    ) {
        String normalizedRecipeId = requireText(recipeId, "RECIPE_ID_REQUIRED", "处方号不能为空");
        if (printNum == null || printNum <= 0) {
            throw new BusinessException("PRINT_NUM_INVALID", "打印张数必须大于 0");
        }
        String printerCode = requireText(dmjCode, "PRINTER_CODE_REQUIRED", "打码机编号不能为空");
        String prescriptionNo = orderRepository.findPrescriptionNoByLegacyPdaRecipeId(normalizedRecipeId)
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
        Map<String, Object> requestParam = new LinkedHashMap<>();
        requestParam.put("source", "legacy-pda-label-print");
        requestParam.put("recipeId", normalizedRecipeId);
        requestParam.put("printNum", printNum);
        requestParam.put("dmjCode", printerCode);
        requestParam.put("dmjIp", cleanText(dmjIp));
        AdminLabelPrintRecord record = createAdminLabelPrintRecord(
                prescriptionNo,
                new AdminLabelPrintRecordCommand(
                        "SENT",
                        "CLOUD",
                        printerCode,
                        printerCode,
                        "LEGACY_PDA",
                        null,
                        null,
                        "legacy-pda-label",
                        writeJson(requestParam),
                        "{\"message\":\"legacy pda print request accepted\"}",
                        null,
                        defaultText(operator, "legacy-pda"),
                        null
                )
        );
        return new LegacyPdaLabelPrintResult(
                record.id(),
                record.prescriptionNo(),
                printNum,
                printerCode,
                cleanText(dmjIp),
                record.printStatus(),
                "legacy pda print request accepted",
                record.createdAt()
        );
    }

    private List<LegacyPdaLabelPrintInfo> legacyPdaLabelInfo(
            AdminPrescriptionPrintPayload payload,
            String withinName
    ) {
        List<LegacyPdaLabelPrintInfo> info = new ArrayList<>();
        addLegacyPdaLabelInfo(info, "prescri_id", "处方号", payload.prescriptionNo());
        addLegacyPdaLabelInfo(info, "patient_name", "患者姓名", payload.patientName());
        addLegacyPdaLabelInfo(info, "patient_gender", "患者性别", null);
        addLegacyPdaLabelInfo(info, "patient_age", "患者年龄", null);
        addLegacyPdaLabelInfo(info, "company_name", "医院名称", payload.institutionName());
        addLegacyPdaLabelInfo(info, "hos_prescri_num", "医院处方号", payload.externalPrescriptionNo());
        addLegacyPdaLabelInfo(info, "hos_bed_no", "床号", payload.bedNo());
        addLegacyPdaLabelInfo(info, "amount", "剂数", payload.doseCount() == null ? "" : String.valueOf(payload.doseCount()));
        addLegacyPdaLabelInfo(info, "hos_depart", "科室", payload.departmentName());
        addLegacyPdaLabelInfo(info, "is_within", "用法", withinName);
        return List.copyOf(info);
    }

    private void addLegacyPdaLabelInfo(
            List<LegacyPdaLabelPrintInfo> info,
            String param,
            String paramName,
            String value
    ) {
        info.add(new LegacyPdaLabelPrintInfo(
                String.valueOf(info.size() + 1),
                param,
                paramName,
                value(value)
        ));
    }

    @Transactional
    public AdminOrderSplitResult splitAdminOrder(String orderNo, AdminOrderSplitCommand command) {
        AdminOrderDetail current = getAdminOrderDetail(orderNo);
        if (command == null || command.items() == null || command.items().size() < 2) {
            throw new BusinessException("ORDER_SPLIT_ITEMS_REQUIRED", "拆单至少需要两份剂数");
        }
        AdminOrderDetail.Prescription prescription = findSplitPrescription(current, command.prescriptionNo());
        List<Integer> doseCounts = command.items().stream()
                .map(AdminOrderSplitCommand.SplitItem::doseCount)
                .map(dose -> dose == null ? 0 : dose)
                .toList();
        if (doseCounts.stream().anyMatch(dose -> dose <= 0)) {
            throw new BusinessException("ORDER_SPLIT_DOSE_INVALID", "拆单剂数必须大于 0");
        }
        int splitTotal = doseCounts.stream().mapToInt(Integer::intValue).sum();
        if (prescription.doseCount() != null && splitTotal != prescription.doseCount()) {
            throw new BusinessException("ORDER_SPLIT_DOSE_MISMATCH", "拆单剂数合计必须等于原处方剂数");
        }

        String operator = defaultText(command.operator(), "admin");
        AdminOrderSplitCommand.SplitItem firstItem = command.items().getFirst();
        orderRepository.updatePrescription(
                current.orderId(),
                prescription.prescriptionId(),
                prescription.prescriptionType(),
                prescription.hospitalType(),
                firstItem.doseCount(),
                splitDecoctionCount(firstItem.doseCount(), prescription.boilTimes(), prescription.decoctionCount()),
                prescription.boilTimes(),
                prescription.isWithin(),
                prescription.perPackNum(),
                prescription.perPackDose(),
                prescription.medicationMethod(),
                prescription.medicationInstruction(),
                appendRemark(prescription.prescriptionRemark(), "拆分前剂数为" + value(prescription.doseCount()) + "，操作人：" + operator)
        );
        orderRepository.updateOrderRemark(
                current.orderId(),
                appendRemark(current.orderRemark(), "订单已拆分，操作人：" + operator)
        );

        List<String> splitOrderNos = new ArrayList<>();
        splitOrderNos.add(current.orderNo());
        for (int i = 1; i < command.items().size(); i++) {
            splitOrderNos.add(createSplitOrder(current, prescription, command.items().get(i), i + 1, operator));
        }
        return new AdminOrderSplitResult(current.orderNo(), prescription.prescriptionNo(), splitOrderNos, "SUCCESS");
    }

    private String normalizeLabelPrintStatus(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "SENT", "PRINTED", "FAILED").contains(normalized)) {
            throw new BusinessException("LABEL_PRINT_STATUS_INVALID", "标签打印状态只能是 PENDING、SENT、PRINTED 或 FAILED");
        }
        return normalized;
    }

    private String normalizeLabelPrintChannel(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "BROWSER";
        if (!Set.of("BROWSER", "CLOUD").contains(normalized)) {
            throw new BusinessException("LABEL_PRINT_CHANNEL_INVALID", "标签打印渠道只能是 BROWSER 或 CLOUD");
        }
        return normalized;
    }

    private String labelPrintRequestParam(
            AdminOrderDetail detail,
            AdminOrderDetail.Prescription prescription,
            String printChannel,
            AdminLabelPrintRecordCommand command
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("printChannel", printChannel);
        payload.put("orderNo", detail.orderNo());
        payload.put("externalOrderNo", detail.externalOrderNo());
        payload.put("prescriptionNo", prescription.prescriptionNo());
        payload.put("externalPrescriptionNo", prescription.externalPrescriptionNo());
        payload.put("templateId", command.templateId());
        payload.put("templateName", command.templateName());
        payload.put("printerCode", command.printerCode());
        payload.put("printerName", command.printerName());
        payload.put("provider", command.provider());
        return writeJson(payload);
    }

    private AdminOrderDetail.Prescription findSplitPrescription(AdminOrderDetail current, String prescriptionNo) {
        if (StringUtils.hasText(prescriptionNo)) {
            return current.prescriptions().stream()
                    .filter(prescription -> prescription.prescriptionNo().equals(prescriptionNo.trim()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
        }
        if (current.prescriptions().size() != 1) {
            throw new BusinessException("PRESCRIPTION_NO_REQUIRED", "多处方订单拆单必须指定处方号");
        }
        return current.prescriptions().getFirst();
    }

    private String createSplitOrder(
            AdminOrderDetail current,
            AdminOrderDetail.Prescription prescription,
            AdminOrderSplitCommand.SplitItem item,
            int splitIndex,
            String operator
    ) {
        UUID splitOrderId = UUID.randomUUID();
        UUID splitPrescriptionId = UUID.randomUUID();
        String splitOrderNo = "ZHYF" + Instant.now().toEpochMilli() + "S" + splitIndex;
        String splitExternalOrderNo = current.externalOrderNo() + "_sub" + splitIndex;
        String splitPrescriptionNo = splitOrderNo + "-1";
        String splitExternalPrescriptionNo = prescription.externalPrescriptionNo() + "_sub" + splitIndex;
        orderRepository.insertOrder(
                splitOrderId,
                current.tenantId(),
                current.institutionId(),
                splitOrderNo,
                splitExternalOrderNo,
                OrderStatus.CREATED.name(),
                current.patientName(),
                current.patientPhone(),
                current.receiverName(),
                current.receiverPhone(),
                current.receiverProvince(),
                current.receiverCity(),
                current.receiverZone(),
                current.receiverAddress(),
                current.addressType(),
                item.deliveryTime() == null ? current.deliveryTime() : item.deliveryTime(),
                current.batchNo(),
                appendRemark(current.orderRemark(), "拆分来源订单：" + current.orderNo() + "，操作人：" + operator),
                null,
                current.logisticsFee(),
                current.discountAmount(),
                writeJson(Map.of(
                        "source", "admin-order-split",
                        "originOrderNo", current.orderNo(),
                        "originPrescriptionNo", prescription.prescriptionNo(),
                        "splitIndex", splitIndex
                ))
        );
        orderRepository.insertOrderStatusLog(
                UUID.randomUUID(),
                current.tenantId(),
                splitOrderId,
                null,
                OrderStatus.CREATED.name(),
                "ADMIN",
                "admin-order-split"
        );
        orderRepository.insertPrescription(
                splitPrescriptionId,
                current.tenantId(),
                current.institutionId(),
                splitOrderId,
                splitPrescriptionNo,
                splitExternalPrescriptionNo,
                prescription.prescriptionType(),
                PrescriptionStatus.CREATED.name(),
                prescription.hospitalType(),
                item.doseCount(),
                splitDecoctionCount(item.doseCount(), prescription.boilTimes(), prescription.decoctionCount()),
                prescription.boilTimes(),
                prescription.isWithin(),
                prescription.perPackNum(),
                prescription.perPackDose(),
                prescription.decoctionUnitPrice(),
                prescription.decoctionTotalPrice(),
                prescription.totalAmount(),
                prescription.doctorName(),
                prescription.diagnosis(),
                prescription.departmentName(),
                prescription.wardName(),
                prescription.bedNo(),
                prescription.medicationMethod(),
                prescription.medicationInstruction(),
                appendRemark(prescription.prescriptionRemark(), "拆分来源处方：" + prescription.prescriptionNo()),
                writeJson(Map.of(
                        "source", "admin-order-split",
                        "originPrescriptionNo", prescription.prescriptionNo(),
                        "splitIndex", splitIndex
                ))
        );
        for (AdminOrderDetail.DrugDetail detail : prescription.details()) {
            orderRepository.insertPrescriptionDetail(
                    UUID.randomUUID(),
                    current.tenantId(),
                    splitPrescriptionId,
                    detail.drugCode(),
                    detail.drugName(),
                    detail.platformDrugCode(),
                    detail.platformDrugName(),
                    detail.drugSpecs(),
                    detail.drugOrigin(),
                    detail.dose(),
                    detail.unit(),
                    detail.specialUsage(),
                    detail.quantity(),
                    detail.unitPrice(),
                    detail.settlementUnitPrice(),
                    detail.totalPrice(),
                    detail.settlementTotalPrice(),
                    detail.batchNo(),
                    detail.remark(),
                    detail.validationTips(),
                    detail.sortNo()
            );
        }
        return splitOrderNo;
    }

    private Integer splitDecoctionCount(Integer doseCount, Integer boilTimes, Integer fallback) {
        if (doseCount == null || boilTimes == null) {
            return fallback;
        }
        return doseCount * boilTimes;
    }

    private String appendRemark(String original, String addition) {
        String cleanedOriginal = cleanText(original);
        return cleanedOriginal == null ? addition : cleanedOriginal + "；" + addition;
    }

    @Transactional
    public AdminManualProcessResult manualProcessAdminOrder(String orderNo, AdminManualProcessCommand command) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        AdminOrderDetail current = getAdminOrderDetail(orderNo.trim());
        OrderStatus currentStatus = parseOrderStatus(current.orderStatus());
        if (!OrderStatus.CREATED.equals(currentStatus)) {
            throw new BusinessException("ORDER_MANUAL_PROCESS_NOT_ALLOWED", "只有待审核订单允许走流程补录");
        }

        ManualProcessContext context = manualProcessContext(command);
        int updated = orderRepository.updateOrderStatusAndAppendRemarkIfCurrent(
                current.orderId(),
                OrderStatus.CREATED.name(),
                OrderStatus.SIGNED.name(),
                context.remark()
        );
        if (updated == 0) {
            throw new BusinessException("ORDER_MANUAL_PROCESS_CONFLICT", "订单状态已变更，请刷新后重试");
        }
        orderRepository.completeManualProcessPrescriptionStatuses(current.orderId());

        UUID reviewTaskId = UUID.randomUUID();
        UUID dispenseTaskId = UUID.randomUUID();
        UUID recheckTaskId = UUID.randomUUID();
        int workflowTaskCount = 0;
        workflowTaskCount += insertManualWorkflowTask(
                current,
                reviewTaskId,
                "ORDER_REVIEW",
                "APPROVED",
                context.auditor(),
                context.remark(),
                context.auditTime()
        );
        workflowTaskCount += insertManualWorkflowTask(
                current,
                dispenseTaskId,
                "PRESCRIPTION_DISPENSE",
                "COMPLETED",
                context.dispenser(),
                context.remark(),
                context.dispenseTime()
        );
        workflowTaskCount += insertManualWorkflowTask(
                current,
                recheckTaskId,
                "PRESCRIPTION_RECHECK",
                "COMPLETED",
                context.rechecker(),
                context.remark(),
                context.recheckTime()
        );

        orderRepository.insertPrescriptionAuditRecord(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                reviewTaskId,
                context.auditor(),
                context.remark(),
                context.auditTime()
        );
        int dispenseRecordCount = orderRepository.insertDispenseRecord(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                dispenseTaskId,
                context.dispenser(),
                context.remark(),
                context.dispenseTime()
        );
        orderRepository.insertPrescriptionRecheckRecord(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                recheckTaskId,
                context.rechecker(),
                context.remark(),
                context.recheckTime()
        );

        int decoctionTaskCount = insertManualDecoctionTasks(current, context);
        insertManualProcessStatusLogs(current, decoctionTaskCount > 0);
        String logisticsNo = "MANUAL-" + current.orderNo();
        orderRepository.upsertSignedShipment(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                current.orderNo(),
                logisticsNo,
                MANUAL_PROCESS_LOGISTICS_COMPANY,
                context.outboundTime(),
                context.outboundTime(),
                context.signTime()
        );
        String storedLogisticsNo = orderRepository.findShipmentNoByOrderId(current.orderId()).orElse(logisticsNo);
        orderRepository.insertShipmentTrace(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                storedLogisticsNo,
                "SHIPPED",
                "手工走流程补录出库",
                context.outboundTime()
        );
        orderRepository.insertShipmentTrace(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                storedLogisticsNo,
                "SIGNED",
                "手工走流程补录签收",
                context.signTime()
        );
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                null,
                context.operator(),
                "ORDER_MANUAL_PROCESS",
                "SUCCESS",
                context.remark(),
                writeJson(manualProcessPayload(current, context, storedLogisticsNo, true))
        );
        return new AdminManualProcessResult(
                current.orderId(),
                current.orderNo(),
                OrderStatus.CREATED.name(),
                OrderStatus.SIGNED.name(),
                workflowTaskCount,
                dispenseRecordCount,
                decoctionTaskCount,
                storedLogisticsNo,
                true,
                Instant.now()
        );
    }

    @Transactional
    public AdminOrderReceiptResult receiptAdminOrder(String orderNo, AdminOrderReceiptCommand command) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        return receiptAdminOrderInternal(orderNo.trim(), command);
    }

    @Transactional
    public AdminBatchOrderReceiptResult batchReceiptAdminOrders(AdminBatchOrderReceiptCommand command) {
        if (command == null || command.orderNos() == null || command.orderNos().isEmpty()) {
            throw new BusinessException("ORDER_RECEIPT_BATCH_REQUIRED", "请输入要签收的订单号");
        }
        List<String> orderNos = command.orderNos().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (orderNos.isEmpty()) {
            throw new BusinessException("ORDER_RECEIPT_BATCH_REQUIRED", "请输入要签收的订单号");
        }
        List<AdminOrderReceiptResult> results = new ArrayList<>();
        for (String orderNo : orderNos) {
            try {
                results.add(receiptAdminOrderInternal(orderNo, new AdminOrderReceiptCommand(
                        command.operator(),
                        command.reason()
                )));
            } catch (BusinessException ex) {
                results.add(new AdminOrderReceiptResult(
                        orderNo,
                        null,
                        OrderStatus.SIGNED.name(),
                        false,
                        ex.getMessage(),
                        Instant.now()
                ));
            }
        }
        int successCount = (int) results.stream().filter(AdminOrderReceiptResult::success).count();
        return new AdminBatchOrderReceiptResult(
                results.size(),
                successCount,
                results.size() - successCount,
                results
        );
    }

    @Transactional
    public AdminOrderAddressUpdateResult updateAdminOrderAddress(
            String orderNo,
            AdminOrderAddressUpdateCommand command
    ) {
        if (command == null) {
            throw new BusinessException("ORDER_ADDRESS_COMMAND_REQUIRED", "地址修改参数不能为空");
        }
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        String receiverName = requireText(command.receiverName(), "RECEIVER_NAME_REQUIRED", "收货人不能为空");
        String receiverPhone = requireText(command.receiverPhone(), "RECEIVER_PHONE_REQUIRED", "收货电话不能为空");
        String receiverAddress = requireText(command.receiverAddress(), "RECEIVER_ADDRESS_REQUIRED", "详细地址不能为空");
        String normalizedOrderNo = orderNo.trim();
        Instant deliveryTime = readAddressDeliveryTime(command.deliveryTime());
        AdminOrderDetail current = getAdminOrderDetail(normalizedOrderNo);
        int updated = orderRepository.updateOrderAddress(
                current.orderId(),
                receiverName,
                receiverPhone,
                cleanText(command.receiverProvince()),
                cleanText(command.receiverCity()),
                cleanText(command.receiverZone()),
                receiverAddress,
                cleanText(command.addressType()),
                deliveryTime
        );
        if (updated == 0) {
            throw new BusinessException("ORDER_ADDRESS_UPDATE_FAILED", "订单地址更新失败");
        }
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                null,
                defaultText(command.operator(), "admin"),
                "ORDER_ADDRESS_UPDATE",
                "SUCCESS",
                cleanText(command.reason()),
                writeJson(command)
        );
        AdminOrderDetail next = getAdminOrderDetail(normalizedOrderNo);
        return new AdminOrderAddressUpdateResult(
                next.orderId(),
                next.orderNo(),
                next.receiverName(),
                next.receiverPhone(),
                next.receiverProvince(),
                next.receiverCity(),
                next.receiverZone(),
                next.receiverAddress(),
                next.addressType(),
                next.deliveryTime(),
                next.updatedAt()
        );
    }

    @Transactional
    public AdminOrderRemarkUpdateResult updateAdminOrderRemark(
            String orderNo,
            AdminOrderRemarkUpdateCommand command
    ) {
        if (command == null) {
            throw new BusinessException("ORDER_REMARK_COMMAND_REQUIRED", "订单备注修改参数不能为空");
        }
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        String remark = requireText(command.remark(), "ORDER_REMARK_REQUIRED", "订单备注不能为空");
        String normalizedOrderNo = orderNo.trim();
        AdminOrderDetail current = getAdminOrderDetail(normalizedOrderNo);
        int updated = orderRepository.updateOrderRemark(current.orderId(), remark);
        if (updated == 0) {
            throw new BusinessException("ORDER_REMARK_UPDATE_FAILED", "订单备注更新失败");
        }
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                null,
                defaultText(command.operator(), "admin"),
                "ORDER_REMARK_UPDATE",
                "SUCCESS",
                cleanText(command.reason()),
                writeJson(command)
        );
        AdminOrderDetail next = getAdminOrderDetail(normalizedOrderNo);
        return new AdminOrderRemarkUpdateResult(
                next.orderId(),
                next.orderNo(),
                next.orderRemark(),
                next.updatedAt()
        );
    }

    @Transactional
    public AdminOrderDetail.Prescription updateAdminPrescription(
            String orderNo,
            UUID prescriptionId,
            AdminPrescriptionUpdateCommand command
    ) {
        if (command == null) {
            throw new BusinessException("PRESCRIPTION_UPDATE_COMMAND_REQUIRED", "处方修改参数不能为空");
        }
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        if (prescriptionId == null) {
            throw new BusinessException("PRESCRIPTION_ID_REQUIRED", "处方 ID 不能为空");
        }
        AdminOrderDetail current = getAdminOrderDetail(orderNo.trim());
        OrderStatus currentStatus = parseOrderStatus(current.orderStatus());
        if (!ADMIN_PRESCRIPTION_EDITABLE_STATUSES.contains(currentStatus)) {
            throw new BusinessException("PRESCRIPTION_UPDATE_NOT_ALLOWED", "当前订单状态不允许修改处方");
        }
        AdminOrderDetail.Prescription oldPrescription = current.prescriptions().stream()
                .filter(prescription -> prescription.prescriptionId().equals(prescriptionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
        if (PrescriptionStatus.CANCELLED.name().equals(oldPrescription.prescriptionStatus())) {
            throw new BusinessException("PRESCRIPTION_UPDATE_NOT_ALLOWED", "已取消处方不允许修改");
        }

        String prescriptionType = requireText(command.prescriptionType(), "PRESCRIPTION_TYPE_REQUIRED", "处方类型不能为空");
        String hospitalType = cleanText(command.hospitalType());
        Integer doseCount = requireNonNegative(command.doseCount(), "DOSE_COUNT_INVALID", "剂数不能小于 0");
        Integer decoctionCount = requireNonNegative(
                command.decoctionCount(),
                "DECOCTION_COUNT_INVALID",
                "煎煮剂数不能小于 0"
        );
        Integer boilTimes = requireNonNegative(command.boilTimes(), "BOIL_TIMES_INVALID", "几煎不能小于 0");
        Integer perPackNum = requireNonNegative(command.perPackNum(), "PER_PACK_NUM_INVALID", "每剂包数不能小于 0");
        Integer perPackDose = requireNonNegative(command.perPackDose(), "PER_PACK_DOSE_INVALID", "每剂剂量不能小于 0");
        Integer isWithin = validateIsWithin(command.isWithin());
        if (boilTimes != null && doseCount != null) {
            decoctionCount = boilTimes * doseCount;
        }
        if (isDecoctionPrescription(prescriptionType) && (boilTimes == null || boilTimes == 0)) {
            throw new BusinessException("BOIL_TIMES_REQUIRED", "代煎处方的几煎必须大于 0");
        }
        int updated = orderRepository.updatePrescription(
                current.orderId(),
                prescriptionId,
                prescriptionType,
                hospitalType,
                doseCount,
                decoctionCount,
                boilTimes,
                isWithin,
                perPackNum,
                perPackDose,
                cleanText(command.medicationMethod()),
                cleanText(command.medicationInstruction()),
                cleanText(command.prescriptionRemark())
        );
        if (updated == 0) {
            throw new BusinessException("PRESCRIPTION_UPDATE_FAILED", "处方修改失败");
        }
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                prescriptionId,
                defaultText(command.operator(), "admin"),
                "ORDER_PRESCRIPTION_UPDATE",
                "SUCCESS",
                cleanText(command.reason()),
                writeJson(prescriptionUpdatePayload(oldPrescription, command, decoctionCount, boilTimes,
                        isWithin, perPackNum, perPackDose))
        );
        String eventPayload = """
                {"tenantId":"%s","orderId":"%s","orderNo":"%s","externalOrderNo":"%s","prescriptionIds":%s,"sourceAction":"ORDER_PRESCRIPTION_UPDATE"}
                """.formatted(
                current.tenantId(),
                current.orderId(),
                current.orderNo(),
                current.externalOrderNo(),
                writeJson(List.of(prescriptionId))
        );
        orderRepository.insertOutbox(
                UUID.randomUUID(),
                current.tenantId(),
                UUID.randomUUID().toString(),
                "ORDER_PRESCRIPTION_UPDATED",
                "ORDER",
                current.orderId().toString(),
                eventPayload
        );
        return getAdminOrderDetail(orderNo.trim()).prescriptions().stream()
                .filter(prescription -> prescription.prescriptionId().equals(prescriptionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
    }

    @Transactional
    public AdminPrescriptionActionResult initializeAdminPrescription(
            String orderNo,
            UUID prescriptionId,
            AdminPrescriptionActionCommand command
    ) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        if (prescriptionId == null) {
            throw new BusinessException("PRESCRIPTION_ID_REQUIRED", "处方 ID 不能为空");
        }
        AdminOrderDetail current = getAdminOrderDetail(orderNo.trim());
        AdminOrderDetail.Prescription prescription = findDetailPrescription(current, prescriptionId);
        OrderStatus currentOrderStatus = parseOrderStatus(current.orderStatus());
        PrescriptionStatus currentPrescriptionStatus = parsePrescriptionStatus(prescription.prescriptionStatus());
        if (OrderStatus.SIGNED.equals(currentOrderStatus)) {
            throw new BusinessException("PRESCRIPTION_INITIALIZE_NOT_ALLOWED", "已签收订单不允许初始化处方");
        }
        if (OrderStatus.CREATED.equals(currentOrderStatus) && PrescriptionStatus.CREATED.equals(currentPrescriptionStatus)) {
            throw new BusinessException("PRESCRIPTION_INITIALIZE_NOT_REQUIRED", "当前处方已经是初始状态");
        }
        String operator = command == null ? "admin" : defaultText(command.operator(), "admin");
        String reason = command == null ? "订单操作初始化处方" : defaultText(command.reason(), "订单操作初始化处方");
        int resetPrescription = orderRepository.updatePrescriptionStatus(
                current.orderId(),
                prescriptionId,
                PrescriptionStatus.CREATED.name()
        );
        boolean orderStatusChanged = !OrderStatus.CREATED.equals(currentOrderStatus);
        if (resetPrescription == 0 && !orderStatusChanged) {
            throw new BusinessException("PRESCRIPTION_INITIALIZE_CONFLICT", "处方状态已变更，请刷新后重试");
        }
        if (orderStatusChanged) {
            int updatedOrder = orderRepository.updateOrderStatusIfCurrent(
                    current.orderId(),
                    currentOrderStatus.name(),
                    OrderStatus.CREATED.name()
            );
            if (updatedOrder == 0) {
                throw new BusinessException("PRESCRIPTION_INITIALIZE_CONFLICT", "订单状态已变更，请刷新后重试");
            }
            orderRepository.cancelPendingWorkflowTasks(current.orderId(), operator, reason);
            orderRepository.cancelActiveDecoctionTasksByOrderId(current.orderId(), operator, reason);
            orderRepository.deleteShipmentRuntimeByOrderId(current.orderId());
            orderRepository.insertOrderStatusLog(
                    UUID.randomUUID(),
                    current.tenantId(),
                    current.orderId(),
                    currentOrderStatus.name(),
                    OrderStatus.CREATED.name(),
                    "ADMIN",
                    "admin-prescription-initialize"
            );
        }
        String eventId = UUID.randomUUID().toString();
        orderRepository.insertOutbox(
                UUID.randomUUID(),
                current.tenantId(),
                eventId,
                ORDER_INITIALIZE_EVENT_TYPE,
                "ORDER",
                current.orderId().toString(),
                writeJson(prescriptionActionPayload(current, prescriptionId, "PRESCRIPTION_INITIALIZE"))
        );
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                prescriptionId,
                operator,
                "PRESCRIPTION_INITIALIZE",
                resetPrescription > 0 || orderStatusChanged ? "SUCCESS" : "UNCHANGED",
                reason,
                writeJson(Map.of(
                        "fromPrescriptionStatus", currentPrescriptionStatus.name(),
                        "toPrescriptionStatus", PrescriptionStatus.CREATED.name(),
                        "fromOrderStatus", currentOrderStatus.name(),
                        "toOrderStatus", orderStatusChanged ? OrderStatus.CREATED.name() : currentOrderStatus.name(),
                        "eventId", eventId
                ))
        );
        return new AdminPrescriptionActionResult(
                current.orderId(),
                current.orderNo(),
                prescriptionId,
                prescription.prescriptionNo(),
                currentPrescriptionStatus.name(),
                PrescriptionStatus.CREATED.name(),
                orderStatusChanged,
                currentOrderStatus.name(),
                orderStatusChanged ? OrderStatus.CREATED.name() : currentOrderStatus.name(),
                eventId,
                Instant.now()
        );
    }

    @Transactional
    public AdminPrescriptionActionResult cancelAdminPrescription(
            String orderNo,
            UUID prescriptionId,
            AdminPrescriptionActionCommand command
    ) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        if (prescriptionId == null) {
            throw new BusinessException("PRESCRIPTION_ID_REQUIRED", "处方 ID 不能为空");
        }
        AdminOrderDetail current = getAdminOrderDetail(orderNo.trim());
        AdminOrderDetail.Prescription prescription = findDetailPrescription(current, prescriptionId);
        OrderStatus currentOrderStatus = parseOrderStatus(current.orderStatus());
        PrescriptionStatus currentPrescriptionStatus = parsePrescriptionStatus(prescription.prescriptionStatus());
        if (OrderStatus.SIGNED.equals(currentOrderStatus)
                || OrderStatus.CANCELLED.equals(currentOrderStatus)
                || OrderStatus.AUDIT_FAILED.equals(currentOrderStatus)) {
            throw new BusinessException("PRESCRIPTION_CANCEL_NOT_ALLOWED", "当前订单状态不允许取消处方");
        }
        if (PrescriptionStatus.CANCELLED.equals(currentPrescriptionStatus)) {
            throw new BusinessException("PRESCRIPTION_CANCEL_NOT_REQUIRED", "当前处方已经取消");
        }
        String operator = command == null ? "admin" : defaultText(command.operator(), "admin");
        String reason = command == null ? "订单操作取消处方" : defaultText(command.reason(), "订单操作取消处方");
        int cancelledPrescription = orderRepository.updatePrescriptionStatus(
                current.orderId(),
                prescriptionId,
                PrescriptionStatus.CANCELLED.name()
        );
        if (cancelledPrescription == 0) {
            throw new BusinessException("PRESCRIPTION_CANCEL_CONFLICT", "处方状态已变更，请刷新后重试");
        }
        int activePrescriptionCount = orderRepository.countActivePrescriptionsByOrderId(current.orderId());
        boolean orderStatusChanged = activePrescriptionCount == 0;
        if (orderStatusChanged) {
            int updatedOrder = orderRepository.updateOrderStatusIfCurrent(
                    current.orderId(),
                    currentOrderStatus.name(),
                    OrderStatus.CANCELLED.name()
            );
            if (updatedOrder == 0) {
                throw new BusinessException("PRESCRIPTION_CANCEL_CONFLICT", "订单状态已变更，请刷新后重试");
            }
            orderRepository.cancelPendingWorkflowTasks(current.orderId(), operator, reason);
            orderRepository.insertOrderStatusLog(
                    UUID.randomUUID(),
                    current.tenantId(),
                    current.orderId(),
                    currentOrderStatus.name(),
                    OrderStatus.CANCELLED.name(),
                    "ADMIN",
                    "admin-prescription-cancel"
            );
        }
        String eventId = UUID.randomUUID().toString();
        orderRepository.insertOutbox(
                UUID.randomUUID(),
                current.tenantId(),
                eventId,
                orderStatusChanged ? "ORDER_CANCELLED" : "PRESCRIPTION_CANCELLED",
                "ORDER",
                current.orderId().toString(),
                writeJson(prescriptionActionPayload(current, prescriptionId, "PRESCRIPTION_CANCEL"))
        );
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                prescriptionId,
                operator,
                "PRESCRIPTION_CANCEL",
                "SUCCESS",
                reason,
                writeJson(Map.of(
                        "fromPrescriptionStatus", currentPrescriptionStatus.name(),
                        "toPrescriptionStatus", PrescriptionStatus.CANCELLED.name(),
                        "fromOrderStatus", currentOrderStatus.name(),
                        "toOrderStatus", orderStatusChanged ? OrderStatus.CANCELLED.name() : currentOrderStatus.name(),
                        "activePrescriptionCount", activePrescriptionCount,
                        "eventId", eventId
                ))
        );
        return new AdminPrescriptionActionResult(
                current.orderId(),
                current.orderNo(),
                prescriptionId,
                prescription.prescriptionNo(),
                currentPrescriptionStatus.name(),
                PrescriptionStatus.CANCELLED.name(),
                orderStatusChanged,
                currentOrderStatus.name(),
                orderStatusChanged ? OrderStatus.CANCELLED.name() : currentOrderStatus.name(),
                eventId,
                Instant.now()
        );
    }

    @Transactional
    public AdminOrderCancelResult cancelAdminOrder(String orderNo, AdminOrderCancelCommand command) {
        if (command == null) {
            throw new BusinessException("ORDER_CANCEL_COMMAND_REQUIRED", "取消参数不能为空");
        }
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        String reason = requireText(command.reason(), "ORDER_CANCEL_REASON_REQUIRED", "取消原因不能为空");
        String operator = defaultText(command.operator(), "admin");
        String normalizedOrderNo = orderNo.trim();
        AdminOrderDetail current = getAdminOrderDetail(normalizedOrderNo);
        OrderStatus currentStatus = parseOrderStatus(current.orderStatus());
        if (!canAdminCancel(currentStatus)) {
            throw new BusinessException("ORDER_CANCEL_NOT_ALLOWED", "当前订单状态不允许取消");
        }
        int updated = orderRepository.updateOrderStatusIfCurrent(
                current.orderId(),
                currentStatus.name(),
                OrderStatus.CANCELLED.name()
        );
        if (updated == 0) {
            throw new BusinessException("ORDER_CANCEL_CONFLICT", "订单状态已变更，请刷新后重试");
        }
        int cancelledPrescriptions = orderRepository.updatePrescriptionsStatusByOrderId(
                current.orderId(),
                PrescriptionStatus.CANCELLED.name()
        );
        int cancelledTasks = orderRepository.cancelPendingWorkflowTasks(current.orderId(), operator, reason);
        orderRepository.insertOrderStatusLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                currentStatus.name(),
                OrderStatus.CANCELLED.name(),
                "ADMIN",
                "admin-order-cancel"
        );
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                null,
                operator,
                "ORDER_CANCEL",
                "SUCCESS",
                reason,
                writeJson(command)
        );
        return new AdminOrderCancelResult(
                current.orderId(),
                current.orderNo(),
                currentStatus.name(),
                OrderStatus.CANCELLED.name(),
                cancelledPrescriptions,
                cancelledTasks,
                Instant.now()
        );
    }

    @Transactional
    public AdminOrderInitializeResult initializeAdminOrder(String orderNo, AdminOrderInitializeCommand command) {
        if (command == null) {
            throw new BusinessException("ORDER_INITIALIZE_COMMAND_REQUIRED", "订单初始化参数不能为空");
        }
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException("ORDER_NO_REQUIRED", "订单号不能为空");
        }
        String normalizedOrderNo = orderNo.trim();
        AdminOrderDetail current = getAdminOrderDetail(normalizedOrderNo);
        OrderStatus currentStatus = parseOrderStatus(current.orderStatus());
        if (OrderStatus.CREATED.equals(currentStatus)) {
            throw new BusinessException("ORDER_INITIALIZE_NOT_REQUIRED", "当前订单已经是初始状态");
        }
        String operator = defaultText(command.operator(), "admin");
        String reason = defaultText(command.reason(), "订单中心手工初始化");

        int updated = orderRepository.updateOrderStatusIfCurrent(
                current.orderId(),
                currentStatus.name(),
                OrderStatus.CREATED.name()
        );
        if (updated == 0) {
            throw new BusinessException("ORDER_INITIALIZE_CONFLICT", "订单状态已变更，请刷新后重试");
        }
        int resetPrescriptions = orderRepository.updatePrescriptionsStatusByOrderId(
                current.orderId(),
                PrescriptionStatus.CREATED.name()
        );
        int cancelledWorkflowTasks = orderRepository.cancelPendingWorkflowTasks(current.orderId(), operator, reason);
        int cancelledDecoctionTasks = orderRepository.cancelActiveDecoctionTasksByOrderId(
                current.orderId(),
                operator,
                reason
        );
        int deletedShipments = orderRepository.deleteShipmentRuntimeByOrderId(current.orderId());

        orderRepository.insertOrderStatusLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                currentStatus.name(),
                OrderStatus.CREATED.name(),
                "ADMIN",
                "admin-order-initialize"
        );
        List<UUID> prescriptionIds = orderRepository.findPrescriptionIdsByOrderId(current.orderId());
        String eventId = UUID.randomUUID().toString();
        String eventPayload = """
                {"tenantId":"%s","orderId":"%s","orderNo":"%s","externalOrderNo":"%s","prescriptionIds":%s,"sourceAction":"ORDER_INITIALIZE"}
                """.formatted(
                current.tenantId(),
                current.orderId(),
                current.orderNo(),
                current.externalOrderNo(),
                writeJson(prescriptionIds)
        );
        orderRepository.insertOutbox(
                UUID.randomUUID(),
                current.tenantId(),
                eventId,
                ORDER_INITIALIZE_EVENT_TYPE,
                "ORDER",
                current.orderId().toString(),
                eventPayload
        );
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                null,
                operator,
                "ORDER_INITIALIZE",
                "SUCCESS",
                reason,
                writeJson(Map.of(
                        "fromStatus", currentStatus.name(),
                        "toStatus", OrderStatus.CREATED.name(),
                        "resetPrescriptionCount", resetPrescriptions,
                        "cancelledWorkflowTaskCount", cancelledWorkflowTasks,
                        "cancelledDecoctionTaskCount", cancelledDecoctionTasks,
                        "deletedShipmentCount", deletedShipments,
                        "eventId", eventId
                ))
        );
        return new AdminOrderInitializeResult(
                current.orderId(),
                current.orderNo(),
                currentStatus.name(),
                OrderStatus.CREATED.name(),
                resetPrescriptions,
                cancelledWorkflowTasks,
                cancelledDecoctionTasks,
                deletedShipments,
                eventId,
                Instant.now()
        );
    }

    private AdminOrderReceiptResult receiptAdminOrderInternal(String orderNo, AdminOrderReceiptCommand command) {
        AdminOrderDetail current = getAdminOrderDetail(orderNo);
        OrderStatus currentStatus = parseOrderStatus(current.orderStatus());
        if (!ADMIN_RECEIPTABLE_STATUSES.contains(currentStatus)) {
            throw new BusinessException("ORDER_RECEIPT_NOT_ALLOWED", "当前订单状态不允许签收");
        }
        String operator = command == null ? "admin" : defaultText(command.operator(), "admin");
        String reason = command == null ? null : cleanText(command.reason());
        int updated = orderRepository.updateOrderStatusIfCurrent(
                current.orderId(),
                currentStatus.name(),
                OrderStatus.SIGNED.name()
        );
        if (updated == 0) {
            throw new BusinessException("ORDER_RECEIPT_CONFLICT", "订单状态已变更，请刷新后重试");
        }
        orderRepository.updateLatestShipmentToSigned(current.orderId());
        orderRepository.insertOrderStatusLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                currentStatus.name(),
                OrderStatus.SIGNED.name(),
                "ADMIN",
                "admin-order-receipt"
        );
        orderRepository.insertOperationLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                null,
                operator,
                "ORDER_RECEIPT",
                "SUCCESS",
                reason,
                writeJson(Map.of(
                        "orderNo", current.orderNo(),
                        "fromStatus", currentStatus.name(),
                        "toStatus", OrderStatus.SIGNED.name(),
                        "reason", value(reason)
                ))
        );
        orderRepository.insertOutbox(
                UUID.randomUUID(),
                current.tenantId(),
                UUID.randomUUID().toString(),
                "ORDER_SIGNED",
                "ORDER",
                current.orderId().toString(),
                writeJson(Map.of(
                        "tenantId", current.tenantId(),
                        "orderId", current.orderId(),
                        "orderNo", current.orderNo(),
                        "externalOrderNo", current.externalOrderNo(),
                        "sourceAction", "ORDER_RECEIPT"
                ))
        );
        return new AdminOrderReceiptResult(
                current.orderNo(),
                currentStatus.name(),
                OrderStatus.SIGNED.name(),
                true,
                "SUCCESS",
                Instant.now()
        );
    }

    private AdminOrderWarehouseQuery normalizeOrderWarehouseQuery(
            AdminOrderWarehouseQuery query,
            boolean export
    ) {
        int page = export ? 1 : Math.max(query.page(), 1);
        int pageSize = export
                ? ADMIN_ORDER_WAREHOUSE_EXPORT_LIMIT
                : Math.min(Math.max(query.pageSize(), 1), 100);
        return new AdminOrderWarehouseQuery(
                query.startTime(),
                query.endTime(),
                query.institution(),
                query.prescriptionType(),
                query.hospitalType(),
                query.orderStatus(),
                query.decoctionCenter(),
                query.deliveryType(),
                query.logisticsCompany(),
                query.province(),
                query.orderNo(),
                query.prescriptionNo(),
                query.hospitalPrescriptionNo(),
                query.patientName(),
                query.receiverPhone(),
                query.nodeTime(),
                page,
                pageSize
        );
    }

    private ManualProcessContext manualProcessContext(AdminManualProcessCommand command) {
        Instant now = Instant.now();
        String operator = command == null ? "admin" : defaultText(command.operator(), "admin");
        Instant auditTime = command == null || command.auditTime() == null ? now : command.auditTime();
        Instant dispenseTime = command == null || command.dispenseTime() == null
                ? auditTime.plusSeconds(5 * 60L)
                : command.dispenseTime();
        Instant recheckTime = command == null || command.recheckTime() == null
                ? auditTime.plusSeconds(30 * 60L)
                : command.recheckTime();
        Instant soakTimeStart = command == null || command.soakTimeStart() == null
                ? auditTime.plusSeconds(60 * 60L)
                : command.soakTimeStart();
        Instant boilTimeStart = command == null || command.boilTimeStart() == null
                ? auditTime.plusSeconds(90 * 60L)
                : command.boilTimeStart();
        Instant outboundTime = command == null || command.outboundTime() == null
                ? auditTime.plusSeconds(150 * 60L)
                : command.outboundTime();
        Instant signTime = command == null || command.signTime() == null
                ? auditTime.plusSeconds(24 * 60 * 60L)
                : command.signTime();
        return new ManualProcessContext(
                operator,
                command == null ? operator : defaultText(command.auditor(), operator),
                command == null ? operator : defaultText(command.dispenser(), operator),
                command == null ? operator : defaultText(command.rechecker(), operator),
                command == null ? "MANUAL" : defaultText(command.pailNo(), "MANUAL"),
                cleanText(command == null ? null : command.remark()),
                auditTime,
                dispenseTime,
                recheckTime,
                soakTimeStart,
                boilTimeStart,
                outboundTime,
                signTime
        );
    }

    private int insertManualWorkflowTask(
            AdminOrderDetail current,
            UUID taskId,
            String taskType,
            String taskStatus,
            String assignee,
            String comment,
            Instant completedAt
    ) {
        return orderRepository.insertCompletedWorkflowTask(
                taskId,
                current.tenantId(),
                current.orderId(),
                taskType,
                taskStatus,
                MANUAL_PROCESS_SOURCE + ":" + current.orderId() + ":" + taskType + ":" + taskId,
                assignee,
                comment,
                writeJson(Map.of(
                        "source", MANUAL_PROCESS_SOURCE,
                        "orderNo", current.orderNo(),
                        "externalOrderNo", value(current.externalOrderNo())
                )),
                completedAt
        );
    }

    private int insertManualDecoctionTasks(AdminOrderDetail current, ManualProcessContext context) {
        int count = 0;
        for (AdminOrderDetail.Prescription prescription : current.prescriptions()) {
            if (!isDecoctionPrescription(prescription.prescriptionType())) {
                continue;
            }
            UUID taskId = UUID.randomUUID();
            count += orderRepository.insertCompletedDecoctionTask(
                    taskId,
                    "MP" + taskId.toString().replace("-", "").substring(0, 18).toUpperCase(),
                    current.tenantId(),
                    current.orderId(),
                    prescription.prescriptionId(),
                    prescription.prescriptionNo(),
                    MANUAL_PROCESS_DEVICE,
                    context.pailNo(),
                    "MANUAL-BIND-" + taskId,
                    "MANUAL-START-" + taskId,
                    "MANUAL-FINISH-" + taskId,
                    context.operator(),
                    context.boilTimeStart(),
                    context.outboundTime()
            );
        }
        return count;
    }

    private void insertManualProcessStatusLogs(AdminOrderDetail current, boolean hasDecoctionTask) {
        insertManualStatusLog(current, OrderStatus.CREATED.name(), OrderStatus.AUDIT_PASSED.name(), "AUDIT");
        insertManualStatusLog(current, OrderStatus.AUDIT_PASSED.name(), OrderStatus.RECHECKED.name(), "RECHECK");
        if (hasDecoctionTask) {
            insertManualStatusLog(current, OrderStatus.RECHECKED.name(), OrderStatus.DECOCTING.name(), "DECOCTION");
            insertManualStatusLog(current, OrderStatus.DECOCTING.name(), OrderStatus.DECOCTED.name(), "DECOCTION");
            insertManualStatusLog(current, OrderStatus.DECOCTED.name(), OrderStatus.PACKED.name(), "LOGISTICS");
        } else {
            insertManualStatusLog(current, OrderStatus.RECHECKED.name(), OrderStatus.PACKED.name(), "LOGISTICS");
        }
        insertManualStatusLog(current, OrderStatus.PACKED.name(), OrderStatus.SHIPPED.name(), "LOGISTICS");
        insertManualStatusLog(current, OrderStatus.SHIPPED.name(), OrderStatus.SIGNED.name(), "ADMIN");
    }

    private void insertManualStatusLog(AdminOrderDetail current, String fromStatus, String toStatus, String operatorType) {
        orderRepository.insertOrderStatusLog(
                UUID.randomUUID(),
                current.tenantId(),
                current.orderId(),
                fromStatus,
                toStatus,
                operatorType,
                MANUAL_PROCESS_SOURCE
        );
    }

    private Map<String, Object> manualProcessPayload(
            AdminOrderDetail current,
            ManualProcessContext context,
            String logisticsNo,
            boolean callbackSuppressed
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", current.orderNo());
        payload.put("externalOrderNo", current.externalOrderNo());
        payload.put("auditor", context.auditor());
        payload.put("auditTime", context.auditTime());
        payload.put("dispenser", context.dispenser());
        payload.put("dispenseTime", context.dispenseTime());
        payload.put("rechecker", context.rechecker());
        payload.put("recheckTime", context.recheckTime());
        payload.put("pailNo", context.pailNo());
        payload.put("soakTimeStart", context.soakTimeStart());
        payload.put("boilTimeStart", context.boilTimeStart());
        payload.put("outboundTime", context.outboundTime());
        payload.put("signTime", context.signTime());
        payload.put("logisticsNo", logisticsNo);
        payload.put("callbackSuppressed", callbackSuppressed);
        return payload;
    }

    private List<String> receiptableStatusNames() {
        return ADMIN_RECEIPTABLE_STATUSES.stream().map(OrderStatus::name).toList();
    }

    private List<String> reprintableStatusNames() {
        return ADMIN_REPRINTABLE_STATUSES.stream().map(OrderStatus::name).toList();
    }

    private OrderCreateResult createNewOrder(
            InstitutionApp app,
            String externalOrderNo,
            JsonNode payload,
            String rawPayload
    ) {
        UUID orderId = UUID.randomUUID();
        String orderNo = "ZHYF" + Instant.now().toEpochMilli();
        String patientName = readText(payload, "patientName", "patient_name", "patient");
        String patientPhone = readText(payload, "patientPhone", "patient_phone", "patientTel");
        String receiverName = readText(payload, "receiverName", "consignee", "receiver");
        String receiverPhone = readText(payload, "receiverPhone", "conTel", "receiverTel");
        String receiverAddress = readText(payload, "receiverAddress", "address", "addrDetail");
        String receiverProvince = readText(payload, "receiverProvince", "province");
        String receiverCity = readText(payload, "receiverCity", "city");
        String receiverZone = readText(payload, "receiverZone", "zone");
        String addressType = readText(payload, "addressType", "addrType");

        orderRepository.insertOrder(
                orderId,
                app.tenantId(),
                app.institutionId(),
                orderNo,
                externalOrderNo,
                OrderStatus.CREATED.name(),
                patientName,
                patientPhone,
                receiverName,
                receiverPhone,
                receiverProvince,
                receiverCity,
                receiverZone,
                receiverAddress,
                addressType,
                readInstant(payload, "deliveryTime", "delivery_time"),
                readText(payload, "batchNo", "classes"),
                readText(payload, "orderRemark", "order_remark", "remark"),
                app.callbackUrl(),
                readDecimal(payload, "logisticsFee", "logistics_fee", "freight", "freightFee",
                        "freight_fee", "shippingFee", "shipping_fee", "deliveryFee",
                        "delivery_fee", "expressFee", "express_fee"),
                readDecimal(payload, "discountAmount", "discount_amount", "discount", "couponAmount",
                        "coupon_amount", "preferentialAmount", "preferential_amount",
                        "reduceAmount", "reduce_amount", "promotionAmount", "promotion_amount"),
                rawPayload
        );
        orderRepository.insertOrderStatusLog(
                UUID.randomUUID(),
                app.tenantId(),
                orderId,
                null,
                OrderStatus.CREATED.name(),
                "INSTITUTION",
                "createOrder"
        );

        List<UUID> prescriptionIds = createPrescriptions(app, orderId, payload);
        String eventPayload = """
                {"tenantId":"%s","orderId":"%s","orderNo":"%s","externalOrderNo":"%s","prescriptionIds":%s}
                """.formatted(app.tenantId(), orderId, orderNo, externalOrderNo, writeJson(prescriptionIds));
        orderRepository.insertOutbox(
                UUID.randomUUID(),
                app.tenantId(),
                UUID.randomUUID().toString(),
                "ORDER_CREATED",
                "ORDER",
                orderId.toString(),
                eventPayload
        );

        return new OrderCreateResult(orderId, orderNo, externalOrderNo, OrderStatus.CREATED.name(), false);
    }

    private List<UUID> createPrescriptions(InstitutionApp app, UUID orderId, JsonNode payload) {
        JsonNode prescriptions = payload.get("prescriptions");
        List<UUID> prescriptionIds = new ArrayList<>();
        if (prescriptions == null || !prescriptions.isArray() || prescriptions.isEmpty()) {
            UUID prescriptionId = insertPrescription(app, orderId, payload, 1);
            prescriptionIds.add(prescriptionId);
            return prescriptionIds;
        }

        int sort = 1;
        for (JsonNode prescription : prescriptions) {
            UUID prescriptionId = insertPrescription(app, orderId, prescription, sort++);
            prescriptionIds.add(prescriptionId);
        }
        return prescriptionIds;
    }

    private UUID insertPrescription(InstitutionApp app, UUID orderId, JsonNode node, int sort) {
        UUID prescriptionId = UUID.randomUUID();
        String externalPrescriptionNo = readText(node, "externalPrescriptionNo", "prescriptionNo", "presNum");
        if (!StringUtils.hasText(externalPrescriptionNo)) {
            externalPrescriptionNo = "PRES-" + orderId + "-" + sort;
        }
        String prescriptionNo = "RX" + Instant.now().toEpochMilli() + sort;
        orderRepository.insertPrescription(
                prescriptionId,
                app.tenantId(),
                app.institutionId(),
                orderId,
                prescriptionNo,
                externalPrescriptionNo,
                readText(node, "prescriptionType", "prescriType", "type"),
                PrescriptionStatus.CREATED.name(),
                readText(node, "hospitalType", "isHos"),
                readInteger(node, "doseCount", "amount", "herbsNum", "herbs_num"),
                readInteger(node, "decoctionCount", "decoctAmount", "decoct_amount"),
                readInteger(node, "boilTimes", "boil_times"),
                readInteger(node, "isWithin", "is_within", "recipeUsage"),
                readInteger(node, "perPackNum", "per_pack_num"),
                readInteger(node, "perPackDose", "per_pack_dose"),
                readDecimal(node, "decoctionUnitPrice", "decoctUnitPrice", "decoct_unit_price"),
                readDecimal(node, "decoctionTotalPrice", "decoctTotalPrice", "decoct_total_price"),
                readDecimal(node, "totalAmount", "totalMoney", "total_money"),
                readText(node, "doctorName", "doctor"),
                readText(node, "diagnosis"),
                readText(node, "departmentName", "hosDepart", "hos_depart"),
                readText(node, "wardName", "hosAreaNo", "hos_area_no"),
                readText(node, "bedNo", "hosBedNo", "hos_bed_no"),
                readText(node, "medicationMethod", "medMethod", "med_method"),
                readText(node, "medicationInstruction", "medGuide", "med_guide"),
                readText(node, "prescriptionRemark", "prescriRemark", "prescri_remark"),
                writeJson(node)
        );

        JsonNode details = node.get("details");
        if (details == null) {
            details = node.get("drugs");
        }
        if (details != null && details.isArray()) {
            int detailSort = 1;
            for (JsonNode detail : details) {
                orderRepository.insertPrescriptionDetail(
                        UUID.randomUUID(),
                        app.tenantId(),
                        prescriptionId,
                        readText(detail, "drugCode", "medicineCode"),
                        readText(detail, "drugName", "medicineName"),
                        readText(detail, "platformDrugCode", "dcGoodsNum", "dc_goods_num", "ptGoodsNum", "pt_goods_num"),
                        readText(detail, "platformDrugName", "dcGoodsName", "dc_goods_name"),
                        readText(detail, "drugSpecs", "goodsNorms", "goods_norms", "specs"),
                        readText(detail, "drugOrigin", "goodsOrigin", "goods_origin", "origin"),
                        readText(detail, "dose", "dosage"),
                        readText(detail, "unit"),
                        readText(detail, "specialUsage", "special_usage"),
                        readDecimal(detail, "quantity", "goodsQuantity", "goods_quantity"),
                        readDecimal(detail, "unitPrice", "medSalePrice", "med_sale_price"),
                        readDecimal(detail, "settlementUnitPrice", "medSettlePrice", "med_settle_price"),
                        readDecimal(detail, "totalPrice", "lineTotalPrice", "line_total_price"),
                        readDecimal(detail, "settlementTotalPrice", "settlementTotalPrice", "settlement_total_price"),
                        readText(detail, "batchNo", "batch_no"),
                        readText(detail, "remark"),
                        readText(detail, "validationTips", "validation_tips"),
                        detailSort++
                );
            }
        }
        return prescriptionId;
    }

    private void verifySignature(OrderCreateCommand command, InstitutionApp app, String rawPayload) {
        String bodyHash = SignatureUtils.sha256Hex(rawPayload);
        String source = command.appKey() + "\n" + command.timestamp() + "\n" + bodyHash;
        String expected = SignatureUtils.hmacSha256Hex(app.appSecret(), source);
        if (!SignatureUtils.constantTimeEquals(expected, command.signature())) {
            throw new BusinessException("INVALID_SIGNATURE", "签名错误");
        }
    }

    private String readText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private String requireText(String value, String code, String message) {
        String cleaned = cleanText(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new BusinessException(code, message);
        }
        return cleaned;
    }

    private UUID requireUuid(UUID value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        return value;
    }

    private String cleanText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value, String fallback) {
        String cleaned = cleanText(value);
        return cleaned == null ? fallback : cleaned;
    }

    private String normalizeAdminRecheckStatus(String value) {
        String cleaned = cleanText(value);
        if (cleaned == null) {
            return "PENDING";
        }
        String normalized = cleaned.toUpperCase(Locale.ROOT);
        if ("1".equals(normalized) || "RECHECKED".equals(normalized) || "DONE".equals(normalized)) {
            return "RECHECKED";
        }
        return "PENDING";
    }

    private String normalizeAdminReviewStatus(String value) {
        String cleaned = cleanText(value);
        if (cleaned == null) {
            return "PENDING";
        }
        String normalized = cleaned.toUpperCase(Locale.ROOT);
        if ("1".equals(normalized) || "NOT_DUE".equals(normalized) || "UNDUE".equals(normalized)) {
            return "NOT_DUE";
        }
        if ("2".equals(normalized) || "REVIEWED".equals(normalized) || "APPROVED".equals(normalized)) {
            return "REVIEWED";
        }
        return "PENDING";
    }

    private String herbIndexAction(AdminHerbIndexRecord before, AdminHerbIndexRecord after) {
        if (before.enabled() != after.enabled()) {
            return after.enabled() ? "ENABLED" : "DISABLED";
        }
        return "UPDATED";
    }

    private void writeHerbIndexLog(AdminHerbIndexRecord record, String actionType, String operator, String remark) {
        orderRepository.insertAdminHerbIndexOperationLog(
                UUID.randomUUID(),
                record.tenantId(),
                record.id(),
                record.institutionId(),
                record.institutionCode(),
                record.institutionName(),
                record.externalHerbCode(),
                record.externalHerbName(),
                record.herbId(),
                record.herbCode(),
                record.herbName(),
                actionType,
                operator,
                remark
        );
    }

    private String areaText(String value) {
        String cleaned = cleanText(value);
        return cleaned == null ? "" : cleaned;
    }

    private List<String> normalizeOrderMergeOrderNos(List<String> orderNos) {
        if (orderNos == null) {
            throw new BusinessException("ORDER_MERGE_ORDER_NOS_REQUIRED", "Order numbers are required");
        }
        List<String> normalized = orderNos.stream()
                .map(this::cleanText)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (normalized.size() < 2) {
            throw new BusinessException("ORDER_MERGE_ORDER_NOS_REQUIRED", "At least two orders are required");
        }
        return normalized;
    }

    private int priorityOrDefault(Integer priority) {
        int normalized = priority == null ? 100 : priority;
        if (normalized < 0) {
            throw new BusinessException("INTERCEPT_PRIORITY_INVALID", "Priority cannot be negative");
        }
        return normalized;
    }

    private UUID verifiedTemplateInstitutionId(String scopeType, UUID institutionId) {
        String normalizedScope = defaultText(scopeType, "GLOBAL");
        if (!"INSTITUTION".equals(normalizedScope)) {
            return null;
        }
        if (institutionId == null) {
            throw new BusinessException("LABEL_TEMPLATE_INSTITUTION_REQUIRED", "Institution is required");
        }
        return orderRepository.findAdminInstitutionById(institutionId)
                .orElseThrow(() -> new BusinessException("INSTITUTION_NOT_FOUND", "Institution not found"))
                .id();
    }

    private int labelDimension(Integer value, int fallback, String code) {
        int normalized = value == null ? fallback : value;
        if (normalized <= 0 || normalized > 300) {
            throw new BusinessException(code, "Label dimension must be between 1 and 300");
        }
        return normalized;
    }

    private BigDecimal moneyOrZero(BigDecimal value, String code) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        if (normalized.signum() < 0) {
            throw new BusinessException(code, "Money value cannot be negative");
        }
        return normalized;
    }

    private OrderStatus parseOrderStatus(String status) {
        try {
            return OrderStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("ORDER_STATUS_INVALID", "订单状态不支持取消操作");
        }
    }

    private PrescriptionStatus parsePrescriptionStatus(String status) {
        try {
            return PrescriptionStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("PRESCRIPTION_STATUS_INVALID", "处方状态不支持当前操作");
        }
    }

    private AdminOrderDetail.Prescription findDetailPrescription(AdminOrderDetail current, UUID prescriptionId) {
        return current.prescriptions().stream()
                .filter(prescription -> prescription.prescriptionId().equals(prescriptionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在"));
    }

    private Map<String, Object> prescriptionActionPayload(
            AdminOrderDetail current,
            UUID prescriptionId,
            String sourceAction
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", current.tenantId());
        payload.put("orderId", current.orderId());
        payload.put("orderNo", current.orderNo());
        payload.put("externalOrderNo", current.externalOrderNo());
        payload.put("prescriptionIds", List.of(prescriptionId));
        payload.put("sourceAction", sourceAction);
        return payload;
    }

    private boolean canAdminCancel(OrderStatus status) {
        return ADMIN_CANCELLABLE_STATUSES.contains(status);
    }

    private Integer requireNonNegative(Integer value, String code, String message) {
        if (value != null && value < 0) {
            throw new BusinessException(code, message);
        }
        return value;
    }

    private Integer validateIsWithin(Integer value) {
        if (value != null && value != 0 && value != 1) {
            throw new BusinessException("IS_WITHIN_INVALID", "服用方式只能是内服或外用");
        }
        return value;
    }

    private boolean isDecoctionPrescription(String prescriptionType) {
        return "2".equals(prescriptionType)
                || "DECOCTION".equals(prescriptionType)
                || "代煎".equals(prescriptionType);
    }

    private Map<String, Object> prescriptionUpdatePayload(
            AdminOrderDetail.Prescription oldPrescription,
            AdminPrescriptionUpdateCommand command,
            Integer decoctionCount,
            Integer boilTimes,
            Integer isWithin,
            Integer perPackNum,
            Integer perPackDose
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("old", prescriptionSnapshot(oldPrescription));
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("prescriptionType", command.prescriptionType());
        next.put("hospitalType", command.hospitalType());
        next.put("doseCount", command.doseCount());
        next.put("decoctionCount", decoctionCount);
        next.put("boilTimes", boilTimes);
        next.put("isWithin", isWithin);
        next.put("perPackNum", perPackNum);
        next.put("perPackDose", perPackDose);
        next.put("medicationMethod", command.medicationMethod());
        next.put("medicationInstruction", command.medicationInstruction());
        next.put("prescriptionRemark", command.prescriptionRemark());
        payload.put("new", next);
        return payload;
    }

    private Map<String, Object> prescriptionSnapshot(AdminOrderDetail.Prescription prescription) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("prescriptionType", prescription.prescriptionType());
        snapshot.put("hospitalType", prescription.hospitalType());
        snapshot.put("doseCount", prescription.doseCount());
        snapshot.put("decoctionCount", prescription.decoctionCount());
        snapshot.put("boilTimes", prescription.boilTimes());
        snapshot.put("isWithin", prescription.isWithin());
        snapshot.put("perPackNum", prescription.perPackNum());
        snapshot.put("perPackDose", prescription.perPackDose());
        snapshot.put("medicationMethod", prescription.medicationMethod());
        snapshot.put("medicationInstruction", prescription.medicationInstruction());
        snapshot.put("prescriptionRemark", prescription.prescriptionRemark());
        return snapshot;
    }

    private void appendCsvRow(StringBuilder builder, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(csvCell(values.get(i)));
        }
        builder.append('\n');
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.startsWith("=") || escaped.startsWith("+") || escaped.startsWith("-") || escaped.startsWith("@")
                || escaped.startsWith("\t")) {
            escaped = "'" + escaped;
        }
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String dateTime(Instant value) {
        return value == null ? "" : EXPORT_DATE_TIME_FORMATTER.format(value);
    }

    private String receiverAddress(AdminOrderListItem row) {
        return List.of(
                        value(row.receiverProvince()),
                        value(row.receiverCity()),
                        value(row.receiverZone()),
                        value(row.receiverAddress())
                ).stream()
                .filter(StringUtils::hasText)
                .reduce("", String::concat);
    }

    private String warehouseAddress(AdminOrderWarehouseItem row) {
        return List.of(
                        value(row.receiverProvince()),
                        value(row.receiverCity()),
                        value(row.receiverZone()),
                        value(row.receiverAddress())
                ).stream()
                .filter(StringUtils::hasText)
                .reduce("", String::concat);
    }

    private String hospitalTypeText(String value) {
        return switch (value(value)) {
            case "1", "OUTPATIENT", "门诊" -> "门诊";
            case "2", "INPATIENT", "住院" -> "住院";
            case "3", "OTHER", "其他" -> "其他";
            default -> value(value);
        };
    }

    private String prescriptionTypeText(String value) {
        return switch (value(value)) {
            case "DECOCTION", "代煎" -> "代煎";
            case "SELF_DECOCTION", "自煎" -> "自煎";
            default -> value(value);
        };
    }

    private String deliveryTypeText(String value) {
        return switch (value(value)) {
            case "HOSPITAL", "送医院" -> "送医院";
            case "PATIENT", "送个人" -> "送个人";
            case "PICKUP", "自提" -> "自提";
            default -> value(value);
        };
    }

    private String batchText(String value) {
        return switch (value(value)) {
            case "1", "MORNING", "早批次" -> "早批次";
            case "2", "NOON", "午批次" -> "午批次";
            case "3", "EVENING", "晚批次" -> "晚批次";
            default -> value(value);
        };
    }

    private String orderStatusText(String value) {
        return switch (value(value)) {
            case "CREATED" -> "已创建";
            case "AUDIT_PASSED" -> "审核通过";
            case "AUDIT_FAILED" -> "审核失败";
            case "RECHECKED" -> "已复核";
            case "DECOCTING" -> "煎煮中";
            case "DECOCTED" -> "已煎煮";
            case "PACKED" -> "已打包";
            case "SHIPPED" -> "已发货";
            case "IN_TRANSIT" -> "运输中";
            case "SIGNED" -> "已签收";
            case "CANCELLED" -> "已取消";
            default -> value(value);
        };
    }

    private Instant readAddressDeliveryTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Instant deliveryTime = readInstantText(text);
        if (deliveryTime == null) {
            throw new BusinessException("DELIVERY_TIME_INVALID", "送货时间格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
        return deliveryTime;
    }

    private Integer readInteger(JsonNode node, String... names) {
        String text = readText(node, names);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.trim()).intValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal readDecimal(JsonNode node, String... names) {
        String text = readText(node, names);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Instant readInstant(JsonNode node, String... names) {
        String text = readText(node, names);
        return readInstantText(text);
    }

    private Instant readInstantText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // 兼容老后台时间格式。
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
            // 兼容老后台时间格式。
        }
        try {
            return LocalDateTime.parse(trimmed, LEGACY_DATE_TIME_FORMATTER)
                    .atZone(DEFAULT_PAYLOAD_ZONE)
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            // 兼容老后台只传日期的地址修改表单。
        }
        try {
            return LocalDate.parse(trimmed)
                    .atStartOfDay(DEFAULT_PAYLOAD_ZONE)
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("JSON_WRITE_FAILED", "JSON 序列化失败");
        }
    }
    private record ManualProcessContext(
            String operator,
            String auditor,
            String dispenser,
            String rechecker,
            String pailNo,
            String remark,
            Instant auditTime,
            Instant dispenseTime,
            Instant recheckTime,
            Instant soakTimeStart,
            Instant boilTimeStart,
            Instant outboundTime,
            Instant signTime
    ) {
    }
}
