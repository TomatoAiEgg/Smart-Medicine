package com.zhyf.decoction.application;

public record WaterPailCommand(
        String pailNo,
        String pailName,
        String decoctionCenter,
        String pailGroup,
        Integer capacityMl,
        Boolean enabled,
        String remark
) {
}
