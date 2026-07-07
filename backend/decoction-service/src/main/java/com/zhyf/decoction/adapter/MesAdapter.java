package com.zhyf.decoction.adapter;

import com.zhyf.decoction.application.DecoctionRecords;
import java.util.List;

public interface MesAdapter {

    DecoctionRecords.DecoctionTaskRecord getWaterByPailNo(String pailNo);

    DecoctionRecords.DecoctionTaskEventRecord waterEndNotice(DeviceOperationRequest request);

    DecoctionRecords.DecoctionTaskRecord getRecipeInfo(DeviceOperationRequest request);

    DecoctionRecords.DecoctionTaskEventRecord recipeBoilInfoCallback(DeviceOperationRequest request);

    List<DecoctionRecords.PrescriptionRecord> reviewFinishPushRecipe(String prescriptionNo, int limit);

    DecoctionRecords.DecoctionTaskRecord decoctingStatusNotice(DeviceOperationRequest request);
}
