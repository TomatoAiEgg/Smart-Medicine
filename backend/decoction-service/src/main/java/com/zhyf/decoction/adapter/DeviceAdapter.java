package com.zhyf.decoction.adapter;

import com.zhyf.decoction.application.DecoctionRecords;
import java.util.List;

public interface DeviceAdapter {

    DecoctionRecords.PdaLoginResult login(DeviceOperationRequest request);

    List<DecoctionRecords.DeviceRecord> listDecoctionDevices();

    List<DecoctionRecords.PrescriptionRecord> listCanOperatePrescriptions(int limit);

    DecoctionRecords.DecoctionTaskRecord bindPrescription(DeviceOperationRequest request);

    DecoctionRecords.DecoctionTaskRecord reportPdaStatus(DeviceOperationRequest request);
}
