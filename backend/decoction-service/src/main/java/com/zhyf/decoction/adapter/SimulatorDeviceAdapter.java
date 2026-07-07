package com.zhyf.decoction.adapter;

import com.zhyf.common.exception.BusinessException;
import com.zhyf.decoction.application.DecoctionRecords;
import com.zhyf.decoction.application.DecoctionSimulatorService;
import com.zhyf.decoction.application.PdaLoginCommand;
import com.zhyf.decoction.application.SimulatorOperationCommand;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SimulatorDeviceAdapter implements DeviceAdapter {

    private final DecoctionSimulatorService simulatorService;

    public SimulatorDeviceAdapter(DecoctionSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @Override
    public DecoctionRecords.PdaLoginResult login(DeviceOperationRequest request) {
        return simulatorService.login(new PdaLoginCommand(
                request.operator(),
                request.deviceCode(),
                request.sign()
        ));
    }

    @Override
    public List<DecoctionRecords.DeviceRecord> listDecoctionDevices() {
        return simulatorService.listDevices();
    }

    @Override
    public List<DecoctionRecords.PrescriptionRecord> listCanOperatePrescriptions(int limit) {
        return simulatorService.listCanOperatePrescriptions(limit);
    }

    @Override
    public DecoctionRecords.DecoctionTaskRecord bindPrescription(DeviceOperationRequest request) {
        return simulatorService.bindPrescription(toSimulatorCommand(request));
    }

    @Override
    public DecoctionRecords.DecoctionTaskRecord reportPdaStatus(DeviceOperationRequest request) {
        return switch (normalizeStatus(request.status())) {
            case "1" -> simulatorService.startDecoction(toSimulatorCommand(request));
            case "2" -> simulatorService.finishDecoction(toSimulatorCommand(request));
            case "8" -> simulatorService.cancelDecoction(toSimulatorCommand(request));
            case "9" -> simulatorService.terminateDecoction(toSimulatorCommand(request));
            default -> throw new BusinessException("LEGACY_PDA_STATUS_UNSUPPORTED", "Unsupported PDA decoction status");
        };
    }

    private SimulatorOperationCommand toSimulatorCommand(DeviceOperationRequest request) {
        return new SimulatorOperationCommand(
                request.operationId(),
                request.deviceCode(),
                request.prescriptionNo(),
                request.pailNo(),
                request.operator(),
                request.timestamp(),
                request.sign()
        );
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "";
        }
        return status.trim().toUpperCase();
    }
}
