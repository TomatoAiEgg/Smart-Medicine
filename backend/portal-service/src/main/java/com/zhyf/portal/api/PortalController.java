package com.zhyf.portal.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.portal.application.PortalCommands;
import com.zhyf.portal.application.PortalQueryService;
import com.zhyf.portal.application.PortalRecords;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portal")
public class PortalController {

    private final PortalQueryService portalQueryService;

    public PortalController(PortalQueryService portalQueryService) {
        this.portalQueryService = portalQueryService;
    }

    @GetMapping("/orders/query")
    public ApiResponse<PortalRecords.PortalOrderRecord> queryOrder(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String externalOrderNo,
            @RequestParam String phone
    ) {
        return ApiResponse.ok(portalQueryService.queryOrder(
                new PortalCommands.PortalOrderQuery(orderNo, externalOrderNo, phone)
        ));
    }

    @PostMapping("/orders/{orderNo}/address-supplements")
    public ApiResponse<PortalRecords.AddressSupplementRecord> createAddressSupplement(
            @PathVariable String orderNo,
            @RequestBody PortalCommands.AddressSupplementCommand command
    ) {
        return ApiResponse.ok(portalQueryService.createAddressSupplement(orderNo, command));
    }
}
