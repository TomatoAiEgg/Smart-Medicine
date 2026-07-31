package com.zhyf.order.application;

import java.util.List;

public record LegacyPdaLabelPrintInitResult(
        String recipeId,
        String equipmentXlh,
        String patientName,
        String patientAge,
        Integer patientGender,
        String withinName,
        Integer amount,
        String amountDesc,
        Integer decoctAmount,
        Integer boilTimes,
        Integer totalPackNum,
        String companyName,
        String hosPrescrinum,
        String hosDepart,
        String productionDate,
        String prescriUsage,
        String hosAreaNo,
        String hosBedNo,
        List<String> ipList,
        String labelUrl,
        String htmlUrl,
        String printModel,
        String model,
        String sizetype,
        List<LegacyPdaLabelPrintInfo> info,
        List<String> contentList
) {
}
