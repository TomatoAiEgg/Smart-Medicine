package com.zhyf.decoction.application;

import com.zhyf.decoction.domain.DecoctionTaskSnapshot;
import com.zhyf.decoction.infrastructure.DecoctionTaskRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecoctionRejectedEventRecorder {

    private final DecoctionTaskRepository taskRepository;

    public DecoctionRejectedEventRecorder(DecoctionTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejected(
            DecoctionTaskSnapshot task,
            String operationId,
            String operator,
            String actionType,
            String source,
            String actualStatus,
            String expectedStatus,
            String payload,
            Instant eventTime
    ) {
        if (taskRepository.findEventByOperationId(operationId).isPresent()) {
            return;
        }
        try {
            taskRepository.createTaskEvent(
                    UUID.randomUUID(),
                    task.taskId(),
                    task.tenantId(),
                    task.orderId(),
                    "REJECTED",
                    operationId,
                    operator,
                    payload,
                    eventTime
            );
            taskRepository.createDeviceWorkRecord(
                    UUID.randomUUID(),
                    task,
                    actionType,
                    "REJECTED",
                    actualStatus,
                    actualStatus,
                    workRecordOperationId(task, actionType, operationId),
                    source,
                    operator,
                    payload,
                    eventTime
            );
        } catch (DataIntegrityViolationException ignored) {
            // Concurrent retry with the same operationId already recorded the rejection.
        }
    }

    private String workRecordOperationId(DecoctionTaskSnapshot task, String actionType, String eventOperationId) {
        String raw = task.taskId() + ":WORK:" + actionType + ":" + eventOperationId;
        return "WORK-REJECTED-" + UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
    }
}
