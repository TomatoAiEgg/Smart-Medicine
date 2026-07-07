package com.zhyf.decoction.application;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.common.status.OrderStatus;
import com.zhyf.decoction.config.DecoctionProperties;
import com.zhyf.decoction.domain.DecoctionTaskSnapshot;
import com.zhyf.decoction.domain.DecoctionTaskStatus;
import com.zhyf.decoction.domain.PrescriptionForDecoction;
import com.zhyf.decoction.infrastructure.DecoctionTaskRepository;
import com.zhyf.decoction.infrastructure.DecoctionTaskRepository.DecoctionTaskEventSnapshot;
import com.zhyf.decoction.infrastructure.DecoctionTaskRepository.DeviceWorkRecordSnapshot;
import com.zhyf.decoction.infrastructure.OrderStatusClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DecoctionSimulatorService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final DecoctionTaskRepository taskRepository;
    private final OrderStatusClient orderStatusClient;
    private final DecoctionRejectedEventRecorder rejectedEventRecorder;
    private final DecoctionProperties properties;
    private final Clock clock;

    @Autowired
    public DecoctionSimulatorService(
            DecoctionTaskRepository taskRepository,
            OrderStatusClient orderStatusClient,
            DecoctionRejectedEventRecorder rejectedEventRecorder,
            DecoctionProperties properties
    ) {
        this(taskRepository, orderStatusClient, rejectedEventRecorder, properties, Clock.systemUTC());
    }

    DecoctionSimulatorService(
            DecoctionTaskRepository taskRepository,
            OrderStatusClient orderStatusClient,
            DecoctionRejectedEventRecorder rejectedEventRecorder,
            DecoctionProperties properties,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.orderStatusClient = orderStatusClient;
        this.rejectedEventRecorder = rejectedEventRecorder;
        this.properties = properties;
        this.clock = clock;
    }

    public DecoctionRecords.PdaLoginResult login(PdaLoginCommand command) {
        requireText(command.operator(), "OPERATOR_REQUIRED", "Operator is required");
        requireText(command.deviceCode(), "DEVICE_CODE_REQUIRED", "Device code is required");
        return new DecoctionRecords.PdaLoginResult(
                command.operator(),
                command.deviceCode(),
                "sim-" + command.deviceCode() + "-" + command.operator(),
                Instant.now(clock)
        );
    }

    public List<DecoctionRecords.PrescriptionRecord> listCanOperatePrescriptions(int limit) {
        return taskRepository.findCanOperatePrescriptions(normalizeLimit(limit))
                .stream()
                .map(this::toPrescriptionRecord)
                .toList();
    }

    public List<DecoctionRecords.DeviceRecord> listDevices() {
        Map<String, DecoctionTaskSnapshot> activeTasks = taskRepository.findActiveTasks()
                .stream()
                .collect(Collectors.toMap(DecoctionTaskSnapshot::deviceCode, Function.identity(), (left, right) -> left));
        return properties.getDeviceCodes().stream()
                .map(deviceCode -> toDeviceRecord(deviceCode, activeTasks.get(deviceCode)))
                .toList();
    }

    public List<DecoctionRecords.DecoctionTaskRecord> listPendingMesTasks() {
        return taskRepository.findTasksByStatus(DecoctionTaskStatus.BOUND.name())
                .stream()
                .map(this::toTaskRecord)
                .toList();
    }

    public List<DecoctionRecords.DecoctionTaskRecord> listActiveMesTasks() {
        return taskRepository.findActiveTasks()
                .stream()
                .map(this::toTaskRecord)
                .toList();
    }

    public DecoctionRecords.DecoctionTaskRecord getTask(String taskNo) {
        requireText(taskNo, "TASK_NO_REQUIRED", "Task no is required");
        return taskRepository.findByTaskNo(taskNo)
                .map(this::toTaskRecord)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
    }

    public DecoctionRecords.DecoctionTaskRecord getActiveTaskByPailNo(String pailNo) {
        requireText(pailNo, "PAIL_NO_REQUIRED", "Pail no is required");
        return taskRepository.findActiveTaskByPailNo(pailNo)
                .map(this::toTaskRecord)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
    }

    public DecoctionRecords.DecoctionTaskRecord getActiveTaskByPrescriptionNo(String prescriptionNo) {
        requireText(prescriptionNo, "PRESCRIPTION_NO_REQUIRED", "Prescription no is required");
        return taskRepository.findActiveTaskByPrescriptionNo(prescriptionNo)
                .map(this::toTaskRecord)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
    }

    @Transactional
    public DecoctionRecords.DecoctionTaskRecord bindPrescription(SimulatorOperationCommand command) {
        validatePdaOperation(command);
        return taskRepository.findByBindOperationId(command.operationId())
                .map(this::toTaskRecord)
                .orElseGet(() -> createBinding(command));
    }

    @Transactional
    public DecoctionRecords.DecoctionTaskRecord startDecoction(SimulatorOperationCommand command) {
        validatePdaOperation(command);
        return taskRepository.findByStartOperationId(command.operationId())
                .map(this::toTaskRecord)
                .orElseGet(() -> startTaskByPrescription(command.prescriptionNo(), command.deviceCode(),
                        command.operationId(), command.operator(), "pda-decoction-start"));
    }

    @Transactional
    public DecoctionRecords.DecoctionTaskRecord finishDecoction(SimulatorOperationCommand command) {
        validatePdaOperation(command);
        return taskRepository.findByFinishOperationId(command.operationId())
                .map(this::toTaskRecord)
                .orElseGet(() -> finishTaskByPrescription(command.prescriptionNo(), command.deviceCode(),
                        command.operationId(), command.operator(), "pda-decoction-finish"));
    }

    @Transactional
    public DecoctionRecords.DecoctionTaskRecord cancelDecoction(SimulatorOperationCommand command) {
        validatePdaOperation(command);
        return taskRepository.findByCancelOperationId(command.operationId())
                .map(this::toTaskRecord)
                .orElseGet(() -> cancelTaskByPrescription(command.prescriptionNo(), command.deviceCode(),
                        command.operationId(), command.operator(), "pda-decoction-cancel", null, null));
    }

    @Transactional
    public DecoctionRecords.DecoctionTaskRecord terminateDecoction(SimulatorOperationCommand command) {
        validatePdaOperation(command);
        return taskRepository.findByTerminateOperationId(command.operationId())
                .map(this::toTaskRecord)
                .orElseGet(() -> terminateTaskByPrescription(command.prescriptionNo(), command.deviceCode(),
                        command.operationId(), command.operator(), "pda-decoction-terminate", null, null));
    }

    @Transactional
    public DecoctionRecords.DecoctionTaskRecord startMesTask(String taskNo, MesTaskOperationCommand command) {
        validateMesOperation(command);
        return taskRepository.findByStartOperationId(command.operationId())
                .map(this::toTaskRecord)
                .orElseGet(() -> startTaskByTaskNo(taskNo, command.operationId(), command.operator(),
                        "mes-decoction-start"));
    }

    @Transactional
    public DecoctionRecords.DecoctionTaskRecord finishMesTask(String taskNo, MesTaskOperationCommand command) {
        validateMesOperation(command);
        return taskRepository.findByFinishOperationId(command.operationId())
                .map(this::toTaskRecord)
                .orElseGet(() -> finishTaskByTaskNo(taskNo, command.operationId(), command.operator(),
                        "mes-decoction-finish"));
    }

    @Transactional
    public DecoctionRecords.DecoctionTaskRecord cancelMesTask(String taskNo, DecoctionEventCommand command) {
        validateEventOperation(command);
        return taskRepository.findByCancelOperationId(command.operationId())
                .map(this::toTaskRecord)
                .orElseGet(() -> cancelTaskByTaskNo(taskNo, command.operationId(), command.operator(),
                        "mes-decoction-cancel", command.reason(), command.remark()));
    }

    @Transactional
    public DecoctionRecords.DecoctionTaskRecord terminateMesTask(String taskNo, DecoctionEventCommand command) {
        validateEventOperation(command);
        return taskRepository.findByTerminateOperationId(command.operationId())
                .map(this::toTaskRecord)
                .orElseGet(() -> terminateTaskByTaskNo(taskNo, command.operationId(), command.operator(),
                        "mes-decoction-terminate", command.reason(), command.remark()));
    }

    @Transactional
    public DecoctionRecords.DecoctionTaskEventRecord recordWaterFinished(String taskNo, DecoctionEventCommand command) {
        validateEventOperation(command);
        return taskRepository.findEventByOperationId(command.operationId())
                .map(this::toEventRecord)
                .orElseGet(() -> createTaskEvent(
                        requireTaskForEvent(taskNo, "WATER_FINISHED", command),
                        "WATER_FINISHED",
                        command,
                        waterPayload(command)
                ));
    }

    @Transactional
    public DecoctionRecords.DecoctionTaskEventRecord recordTemperature(String taskNo, DecoctionEventCommand command) {
        validateEventOperation(command);
        if (command.temperatureCelsius() == null) {
            throw new BusinessException("TEMPERATURE_REQUIRED", "Temperature is required");
        }
        return taskRepository.findEventByOperationId(command.operationId())
                .map(this::toEventRecord)
                .orElseGet(() -> createTaskEvent(
                        requireTaskForEvent(taskNo, "TEMPERATURE", command),
                        "TEMPERATURE",
                        command,
                        temperaturePayload(command)
                ));
    }

    @Transactional
    public DecoctionRecords.DecoctionTaskEventRecord recordError(String taskNo, DecoctionEventCommand command) {
        validateEventOperation(command);
        requireText(command.reason(), "ERROR_REASON_REQUIRED", "Error reason is required");
        return taskRepository.findEventByOperationId(command.operationId())
                .map(this::toEventRecord)
                .orElseGet(() -> createTaskEvent(
                        requireTaskForEvent(taskNo, "ERROR", command),
                        "ERROR",
                        command,
                        errorPayload(command)
                ));
    }

    public List<DecoctionRecords.DecoctionTaskEventRecord> listTaskEvents(String taskNo) {
        return taskRepository.findEventsByTaskNo(taskNo)
                .stream()
                .map(this::toEventRecord)
                .toList();
    }

    private DecoctionRecords.DecoctionTaskRecord createBinding(SimulatorOperationCommand command) {
        PrescriptionForDecoction prescription = taskRepository.findPrescription(command.prescriptionNo())
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND", "Prescription not found"));
        if (!OrderStatus.RECHECKED.name().equals(prescription.orderStatus())) {
            throw new BusinessException("PRESCRIPTION_NOT_RECHECKED", "Prescription is not ready for decoction");
        }
        taskRepository.findActiveTaskByPrescriptionId(prescription.prescriptionId())
                .ifPresent(task -> {
                    throw new BusinessException("PRESCRIPTION_ALREADY_BOUND", "Prescription already has active task");
                });
        taskRepository.findActiveTaskByDeviceCode(command.deviceCode())
                .ifPresent(task -> {
                    throw new BusinessException("DEVICE_OCCUPIED", "Device is occupied by another task");
                });

        UUID taskId = UUID.randomUUID();
        String taskNo = newTaskNo(taskId);
        taskRepository.createTask(
                taskId,
                taskNo,
                prescription.tenantId(),
                prescription.orderId(),
                prescription.prescriptionId(),
                prescription.prescriptionNo(),
                command.deviceCode(),
                command.pailNo(),
                DecoctionTaskStatus.BOUND.name(),
                command.operationId(),
                command.operator()
        );
        DecoctionTaskSnapshot task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_CREATE_FAILED", "Decoction task create failed"));
        createDeviceWorkRecord(
                task,
                "BIND",
                "SUCCESS",
                null,
                task.taskStatus(),
                command.operationId(),
                "pda-bind-prescription",
                command.operator(),
                bindPayload(command),
                Instant.now(clock)
        );
        return toTaskRecord(task);
    }

    private DecoctionRecords.DecoctionTaskRecord startTaskByPrescription(
            String prescriptionNo,
            String deviceCode,
            String operationId,
            String operator,
            String source
    ) {
        DecoctionTaskSnapshot task = taskRepository.findActiveTaskByPrescriptionNo(prescriptionNo)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
        if (!deviceCode.equals(task.deviceCode())) {
            throw new BusinessException("DEVICE_NOT_MATCH", "Device does not match bound task");
        }
        return startTask(task, operationId, operator, source);
    }

    private DecoctionRecords.DecoctionTaskRecord finishTaskByPrescription(
            String prescriptionNo,
            String deviceCode,
            String operationId,
            String operator,
            String source
    ) {
        DecoctionTaskSnapshot task = taskRepository.findActiveTaskByPrescriptionNo(prescriptionNo)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
        if (!deviceCode.equals(task.deviceCode())) {
            throw new BusinessException("DEVICE_NOT_MATCH", "Device does not match bound task");
        }
        return finishTask(task, operationId, operator, source);
    }

    private DecoctionRecords.DecoctionTaskRecord cancelTaskByPrescription(
            String prescriptionNo,
            String deviceCode,
            String operationId,
            String operator,
            String source,
            String reason,
            String remark
    ) {
        DecoctionTaskSnapshot task = taskRepository.findActiveTaskByPrescriptionNo(prescriptionNo)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
        if (!deviceCode.equals(task.deviceCode())) {
            throw new BusinessException("DEVICE_NOT_MATCH", "Device does not match bound task");
        }
        return cancelTask(task, operationId, operator, source, reason, remark);
    }

    private DecoctionRecords.DecoctionTaskRecord terminateTaskByPrescription(
            String prescriptionNo,
            String deviceCode,
            String operationId,
            String operator,
            String source,
            String reason,
            String remark
    ) {
        DecoctionTaskSnapshot task = taskRepository.findActiveTaskByPrescriptionNo(prescriptionNo)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
        if (!deviceCode.equals(task.deviceCode())) {
            throw new BusinessException("DEVICE_NOT_MATCH", "Device does not match bound task");
        }
        return terminateTask(task, operationId, operator, source, reason, remark);
    }

    private DecoctionRecords.DecoctionTaskRecord startTaskByTaskNo(
            String taskNo,
            String operationId,
            String operator,
            String source
    ) {
        DecoctionTaskSnapshot task = taskRepository.findByTaskNo(taskNo)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
        return startTask(task, operationId, operator, source);
    }

    private DecoctionRecords.DecoctionTaskRecord finishTaskByTaskNo(
            String taskNo,
            String operationId,
            String operator,
            String source
    ) {
        DecoctionTaskSnapshot task = taskRepository.findByTaskNo(taskNo)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
        return finishTask(task, operationId, operator, source);
    }

    private DecoctionRecords.DecoctionTaskRecord cancelTaskByTaskNo(
            String taskNo,
            String operationId,
            String operator,
            String source,
            String reason,
            String remark
    ) {
        DecoctionTaskSnapshot task = taskRepository.findByTaskNo(taskNo)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
        return cancelTask(task, operationId, operator, source, reason, remark);
    }

    private DecoctionRecords.DecoctionTaskRecord terminateTaskByTaskNo(
            String taskNo,
            String operationId,
            String operator,
            String source,
            String reason,
            String remark
    ) {
        DecoctionTaskSnapshot task = taskRepository.findByTaskNo(taskNo)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
        return terminateTask(task, operationId, operator, source, reason, remark);
    }

    private DecoctionRecords.DecoctionTaskRecord startTask(
            DecoctionTaskSnapshot task,
            String operationId,
            String operator,
            String source
    ) {
        if (!DecoctionTaskStatus.BOUND.name().equals(task.taskStatus())) {
            rejectInvalidStatus(task, "START", operationId, operator, source, task.taskStatus(), "BOUND",
                    "Decoction task cannot be started");
        }
        orderStatusClient.updateStatus(task.orderId(), OrderStatus.DECOCTING.name(), "DECOCTION", source);
        int updated = taskRepository.markStarted(task.taskId(), operationId, operator);
        if (updated == 0) {
            throw new BusinessException("DECOCTION_TASK_START_FAILED", "Decoction task start failed");
        }
        DecoctionTaskSnapshot nextTask = taskRepository.findByTaskId(task.taskId())
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
        createDeviceWorkRecord(
                task,
                "START",
                "SUCCESS",
                task.taskStatus(),
                nextTask.taskStatus(),
                operationId,
                source,
                operator,
                controlPayload(source, null, null),
                Instant.now(clock)
        );
        return toTaskRecord(nextTask);
    }

    private DecoctionRecords.DecoctionTaskRecord finishTask(
            DecoctionTaskSnapshot task,
            String operationId,
            String operator,
            String source
    ) {
        if (!DecoctionTaskStatus.DECOCTING.name().equals(task.taskStatus())) {
            rejectInvalidStatus(task, "FINISH", operationId, operator, source, task.taskStatus(), "DECOCTING",
                    "Decoction task cannot be finished");
        }
        orderStatusClient.updateStatus(task.orderId(), OrderStatus.DECOCTED.name(), "DECOCTION", source);
        int updated = taskRepository.markFinished(task.taskId(), operationId, operator);
        if (updated == 0) {
            throw new BusinessException("DECOCTION_TASK_FINISH_FAILED", "Decoction task finish failed");
        }
        DecoctionTaskSnapshot nextTask = taskRepository.findByTaskId(task.taskId())
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
        createDeviceWorkRecord(
                task,
                "FINISH",
                "SUCCESS",
                task.taskStatus(),
                nextTask.taskStatus(),
                operationId,
                source,
                operator,
                controlPayload(source, null, null),
                Instant.now(clock)
        );
        return toTaskRecord(nextTask);
    }

    private DecoctionRecords.DecoctionTaskRecord cancelTask(
            DecoctionTaskSnapshot task,
            String operationId,
            String operator,
            String source,
            String reason,
            String remark
    ) {
        if (!DecoctionTaskStatus.BOUND.name().equals(task.taskStatus())) {
            rejectInvalidStatus(task, "CANCEL", operationId, operator, source, task.taskStatus(), "BOUND",
                    "Only bound decoction task can be cancelled");
        }
        int updated = taskRepository.markCancelled(task.taskId(), operationId, operator);
        if (updated == 0) {
            throw new BusinessException("DECOCTION_TASK_CANCEL_FAILED", "Decoction task cancel failed");
        }
        createControlEvent(task, "CANCELLED", operationId, operator, source, reason, remark, DecoctionTaskStatus.CANCELLED.name());
        return taskRepository.findByTaskId(task.taskId())
                .map(this::toTaskRecord)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
    }

    private DecoctionRecords.DecoctionTaskRecord terminateTask(
            DecoctionTaskSnapshot task,
            String operationId,
            String operator,
            String source,
            String reason,
            String remark
    ) {
        if (!DecoctionTaskStatus.DECOCTING.name().equals(task.taskStatus())) {
            rejectInvalidStatus(task, "TERMINATE", operationId, operator, source, task.taskStatus(), "DECOCTING",
                    "Only decocting task can be terminated");
        }
        orderStatusClient.updateStatus(task.orderId(), OrderStatus.CANCELLED.name(), "DECOCTION", source);
        int updated = taskRepository.markTerminated(task.taskId(), operationId, operator);
        if (updated == 0) {
            throw new BusinessException("DECOCTION_TASK_TERMINATE_FAILED", "Decoction task terminate failed");
        }
        createControlEvent(task, "TERMINATED", operationId, operator, source, reason, remark, DecoctionTaskStatus.TERMINATED.name());
        return taskRepository.findByTaskId(task.taskId())
                .map(this::toTaskRecord)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
    }

    private DecoctionTaskSnapshot requireTaskForEvent(String taskNo, String eventType, DecoctionEventCommand command) {
        DecoctionTaskSnapshot task = taskRepository.findByTaskNo(taskNo)
                .orElseThrow(() -> new BusinessException("DECOCTION_TASK_NOT_FOUND", "Decoction task not found"));
        if ("WATER_FINISHED".equals(eventType) && !DecoctionTaskStatus.BOUND.name().equals(task.taskStatus())) {
            rejectInvalidStatus(task, eventType, command.operationId(), command.operator(), "mes-event",
                    task.taskStatus(), "BOUND",
                    "Water can only be finished before decoction starts");
        }
        if ("TEMPERATURE".equals(eventType) && !DecoctionTaskStatus.DECOCTING.name().equals(task.taskStatus())) {
            rejectInvalidStatus(task, eventType, command.operationId(), command.operator(), "mes-event",
                    task.taskStatus(), "DECOCTING",
                    "Temperature can only be reported while decocting");
        }
        return task;
    }

    private void rejectInvalidStatus(
            DecoctionTaskSnapshot task,
            String action,
            String operationId,
            String operator,
            String source,
            String actualStatus,
            String expectedStatus,
            String message
    ) {
        String normalizedOperationId = rejectedOperationId(task, action, operationId);
        String normalizedOperator = StringUtils.hasText(operator) ? operator : "system";
        rejectedEventRecorder.recordRejected(
                task,
                normalizedOperationId,
                normalizedOperator,
                action,
                source,
                actualStatus,
                expectedStatus,
                rejectedPayload(action, source, operationId, actualStatus, expectedStatus, message),
                Instant.now(clock)
        );
        throw new BusinessException("DECOCTION_TASK_STATUS_INVALID", message);
    }

    private String rejectedOperationId(DecoctionTaskSnapshot task, String action, String operationId) {
        String raw = task.taskId() + ":" + action + ":" + (StringUtils.hasText(operationId) ? operationId : "none");
        UUID rejectedId = UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
        return "REJECTED-" + rejectedId;
    }

    private void createControlEvent(
            DecoctionTaskSnapshot task,
            String eventType,
            String operationId,
            String operator,
            String source,
            String reason,
            String remark,
            String taskStatusAfter
    ) {
        String payload = controlPayload(source, reason, remark);
        taskRepository.createTaskEvent(
                UUID.randomUUID(),
                task.taskId(),
                task.tenantId(),
                task.orderId(),
                eventType,
                operationId,
                operator,
                payload,
                Instant.now(clock)
        );
        createDeviceWorkRecord(
                task,
                eventType,
                "SUCCESS",
                task.taskStatus(),
                taskStatusAfter,
                operationId,
                source,
                operator,
                payload,
                Instant.now(clock)
        );
    }

    private DecoctionRecords.DecoctionTaskEventRecord createTaskEvent(
            DecoctionTaskSnapshot task,
            String eventType,
            DecoctionEventCommand command,
            String payload
    ) {
        UUID eventId = UUID.randomUUID();
        taskRepository.createTaskEvent(
                eventId,
                task.taskId(),
                task.tenantId(),
                task.orderId(),
                eventType,
                command.operationId(),
                command.operator(),
                payload,
                eventTime(command)
        );
        createDeviceWorkRecord(
                task,
                eventType,
                "SUCCESS",
                task.taskStatus(),
                task.taskStatus(),
                command.operationId(),
                "mes-event",
                command.operator(),
                payload,
                eventTime(command)
        );
        return taskRepository.findEventByOperationId(command.operationId())
                .map(this::toEventRecord)
                .orElseThrow(() -> new BusinessException("DECOCTION_EVENT_CREATE_FAILED", "Decoction event create failed"));
    }

    public List<DecoctionRecords.DeviceWorkRecord> listDeviceWorkRecords(String taskNo) {
        return taskRepository.findDeviceWorkRecordsByTaskNo(taskNo)
                .stream()
                .map(this::toDeviceWorkRecord)
                .toList();
    }

    private void validatePdaOperation(SimulatorOperationCommand command) {
        requireText(command.operationId(), "OPERATION_ID_REQUIRED", "Operation id is required");
        requireText(command.deviceCode(), "DEVICE_CODE_REQUIRED", "Device code is required");
        requireText(command.prescriptionNo(), "PRESCRIPTION_NO_REQUIRED", "Prescription no is required");
        requireText(command.operator(), "OPERATOR_REQUIRED", "Operator is required");
    }

    private void validateMesOperation(MesTaskOperationCommand command) {
        requireText(command.operationId(), "OPERATION_ID_REQUIRED", "Operation id is required");
        requireText(command.operator(), "OPERATOR_REQUIRED", "Operator is required");
    }

    private void validateEventOperation(DecoctionEventCommand command) {
        requireText(command.operationId(), "OPERATION_ID_REQUIRED", "Operation id is required");
        requireText(command.operator(), "OPERATOR_REQUIRED", "Operator is required");
    }

    private void requireText(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(code, message);
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String newTaskNo(UUID taskId) {
        return "DCT-" + taskId.toString().substring(0, 8).toUpperCase();
    }

    private Instant eventTime(DecoctionEventCommand command) {
        return command.timestamp() == null ? Instant.now(clock) : command.timestamp();
    }

    private String waterPayload(DecoctionEventCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("waterVolumeMl", command.waterVolumeMl());
        putIfPresent(payload, "remark", command.remark());
        return json(payload);
    }

    private String bindPayload(SimulatorOperationCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceCode", command.deviceCode());
        putIfPresent(payload, "pailNo", command.pailNo());
        payload.put("prescriptionNo", command.prescriptionNo());
        return json(payload);
    }

    private String temperaturePayload(DecoctionEventCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("temperatureCelsius", command.temperatureCelsius());
        payload.put("durationSeconds", command.durationSeconds());
        putIfPresent(payload, "remark", command.remark());
        return json(payload);
    }

    private String errorPayload(DecoctionEventCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", command.reason());
        putIfPresent(payload, "remark", command.remark());
        return json(payload);
    }

    private String controlPayload(String source, String reason, String remark) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", source);
        putIfPresent(payload, "reason", reason);
        putIfPresent(payload, "remark", remark);
        return json(payload);
    }

    private String rejectedPayload(
            String action,
            String source,
            String originalOperationId,
            String actualStatus,
            String expectedStatus,
            String reason
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("source", source);
        putIfPresent(payload, "originalOperationId", originalOperationId);
        payload.put("actualStatus", actualStatus);
        payload.put("expectedStatus", expectedStatus);
        payload.put("reason", reason);
        return json(payload);
    }

    private void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (StringUtils.hasText(value)) {
            payload.put(key, value);
        }
    }

    private String json(Map<String, Object> payload) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (!first) {
                builder.append(',');
            }
            builder.append('"').append(escapeJson(entry.getKey())).append('"').append(':');
            if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else if (value instanceof Instant instant) {
                builder.append('"').append(DateTimeFormatter.ISO_INSTANT.format(instant)).append('"');
            } else {
                builder.append('"').append(escapeJson(String.valueOf(value))).append('"');
            }
            first = false;
        }
        builder.append('}');
        return builder.toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void createDeviceWorkRecord(
            DecoctionTaskSnapshot task,
            String actionType,
            String actionResult,
            String taskStatusBefore,
            String taskStatusAfter,
            String operationId,
            String source,
            String operator,
            String detailPayload,
            Instant actionTime
    ) {
        if (taskRepository.findDeviceWorkRecordByOperationId(operationId).isPresent()) {
            return;
        }
        taskRepository.createDeviceWorkRecord(
                UUID.randomUUID(),
                task,
                actionType,
                actionResult,
                taskStatusBefore,
                taskStatusAfter,
                operationId,
                source,
                operator,
                detailPayload,
                actionTime
        );
    }

    private DecoctionRecords.PrescriptionRecord toPrescriptionRecord(PrescriptionForDecoction prescription) {
        return new DecoctionRecords.PrescriptionRecord(
                prescription.tenantId(),
                prescription.orderId(),
                prescription.prescriptionId(),
                prescription.orderNo(),
                prescription.externalOrderNo(),
                prescription.prescriptionNo(),
                prescription.orderStatus()
        );
    }

    private DecoctionRecords.DeviceRecord toDeviceRecord(String deviceCode, DecoctionTaskSnapshot task) {
        if (task == null) {
            return new DecoctionRecords.DeviceRecord(deviceCode, "Mock decoction device " + deviceCode, "IDLE", null, null);
        }
        String status = DecoctionTaskStatus.DECOCTING.name().equals(task.taskStatus()) ? "WORKING" : "OCCUPIED";
        return new DecoctionRecords.DeviceRecord(
                deviceCode,
                "Mock decoction device " + deviceCode,
                status,
                task.taskNo(),
                task.prescriptionNo()
        );
    }

    private DecoctionRecords.DecoctionTaskRecord toTaskRecord(DecoctionTaskSnapshot task) {
        return new DecoctionRecords.DecoctionTaskRecord(
                task.taskId(),
                task.taskNo(),
                task.tenantId(),
                task.orderId(),
                task.prescriptionId(),
                task.orderNo(),
                task.prescriptionNo(),
                task.deviceCode(),
                task.pailNo(),
                task.taskStatus(),
                task.operator(),
                task.startedAt(),
                task.finishedAt(),
                task.createdAt(),
                task.updatedAt()
        );
    }

    private DecoctionRecords.DecoctionTaskEventRecord toEventRecord(DecoctionTaskEventSnapshot event) {
        return new DecoctionRecords.DecoctionTaskEventRecord(
                event.eventId(),
                event.taskId(),
                event.taskNo(),
                event.tenantId(),
                event.orderId(),
                event.eventType(),
                event.operationId(),
                event.operator(),
                event.eventPayload(),
                event.eventTime(),
                event.createdAt()
        );
    }

    private DecoctionRecords.DeviceWorkRecord toDeviceWorkRecord(DeviceWorkRecordSnapshot record) {
        return new DecoctionRecords.DeviceWorkRecord(
                record.recordId(),
                record.taskId(),
                record.taskNo(),
                record.tenantId(),
                record.orderId(),
                record.prescriptionNo(),
                record.deviceCode(),
                record.pailNo(),
                record.actionType(),
                record.actionResult(),
                record.taskStatusBefore(),
                record.taskStatusAfter(),
                record.operationId(),
                record.source(),
                record.operator(),
                record.detailPayload(),
                record.actionTime(),
                record.createdAt()
        );
    }
}
