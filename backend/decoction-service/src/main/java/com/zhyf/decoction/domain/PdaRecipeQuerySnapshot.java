package com.zhyf.decoction.domain;

public record PdaRecipeQuerySnapshot(
        String recipeId,
        String hlkyRecipeId,
        String orderId,
        String orderStatus,
        String patientName,
        String patientAge,
        String patientGender,
        Integer isWithin,
        Integer amount,
        Integer decoctAmount,
        Integer boilTimes,
        Integer perPackNum,
        String deviceCode,
        String pailNo,
        String taskStatus
) {
}
