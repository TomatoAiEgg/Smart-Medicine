package com.zhyf.decoction.application;

import java.util.List;

public record WaterPailBatchCommand(
        List<String> pailNos,
        String startPailNo,
        Integer addNum,
        String pailName,
        String decoctionCenter,
        String pailGroup,
        Integer capacityMl,
        Boolean enabled,
        String remark
) {
}
