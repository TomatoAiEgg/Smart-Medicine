package com.zhyf.decoction.adapter;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.decoction.application.DecoctionEventCommand;
import com.zhyf.decoction.application.DecoctionRecords;
import com.zhyf.decoction.application.DecoctionSimulatorService;
import com.zhyf.decoction.application.MesTaskOperationCommand;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SimulatorMesAdapter implements MesAdapter {

    private final DecoctionSimulatorService simulatorService;

    public SimulatorMesAdapter(DecoctionSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @Override
    public DecoctionRecords.DecoctionTaskRecord getWaterByPailNo(String pailNo) {
        return simulatorService.getActiveTaskByPailNo(pailNo);
    }

    @Override
    public DecoctionRecords.DecoctionTaskEventRecord waterEndNotice(DeviceOperationRequest request) {
        return simulatorService.recordWaterFinished(resolveTaskNo(request), toEventCommand(request));
    }

    @Override
    public DecoctionRecords.DecoctionTaskRecord getRecipeInfo(DeviceOperationRequest request) {
        if (StringUtils.hasText(request.taskNo())) {
            return simulatorService.getTask(request.taskNo());
        }
        return simulatorService.getActiveTaskByPrescriptionNo(request.prescriptionNo());
    }

    @Override
    public DecoctionRecords.DecoctionTaskEventRecord recipeBoilInfoCallback(DeviceOperationRequest request) {
        return simulatorService.recordTemperature(resolveTaskNo(request), toEventCommand(request));
    }

    @Override
    public List<DecoctionRecords.PrescriptionRecord> reviewFinishPushRecipe(String prescriptionNo, int limit) {
        List<DecoctionRecords.PrescriptionRecord> records = simulatorService.listCanOperatePrescriptions(limit);
        if (!StringUtils.hasText(prescriptionNo)) {
            return records;
        }
        return records.stream()
                .filter(record -> prescriptionNo.equals(record.prescriptionNo()))
                .toList();
    }

    @Override
    public DecoctionRecords.DecoctionTaskRecord decoctingStatusNotice(DeviceOperationRequest request) {
        String taskNo = resolveTaskNo(request);
        return switch (normalizeStatus(request.status())) {
            case "1" -> simulatorService.startMesTask(taskNo, toMesCommand(request));
            case "2" -> simulatorService.finishMesTask(taskNo, toMesCommand(request));
            case "8" -> simulatorService.cancelMesTask(taskNo, toEventCommand(request));
            case "9" -> simulatorService.terminateMesTask(taskNo, toEventCommand(request));
            default -> throw new BusinessException("LEGACY_MES_STATUS_UNSUPPORTED", "Unsupported MES decoction status");
        };
    }

    private String resolveTaskNo(DeviceOperationRequest request) {
        if (StringUtils.hasText(request.taskNo())) {
            return request.taskNo();
        }
        if (StringUtils.hasText(request.pailNo())) {
            return simulatorService.getActiveTaskByPailNo(request.pailNo()).taskNo();
        }
        if (StringUtils.hasText(request.prescriptionNo())) {
            return simulatorService.getActiveTaskByPrescriptionNo(request.prescriptionNo()).taskNo();
        }
        throw new BusinessException("DECOCTION_TASK_KEY_REQUIRED", "Task no, pail no or prescription no is required");
    }

    private MesTaskOperationCommand toMesCommand(DeviceOperationRequest request) {
        return new MesTaskOperationCommand(
                request.operationId(),
                request.operator(),
                request.timestamp(),
                request.sign()
        );
    }

    private DecoctionEventCommand toEventCommand(DeviceOperationRequest request) {
        return new DecoctionEventCommand(
                request.operationId(),
                request.operator(),
                request.timestamp(),
                request.sign(),
                request.reason(),
                request.waterVolumeMl(),
                request.temperatureCelsius(),
                request.durationSeconds(),
                request.remark()
        );
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "";
        }
        return status.trim().toUpperCase();
    }
}
