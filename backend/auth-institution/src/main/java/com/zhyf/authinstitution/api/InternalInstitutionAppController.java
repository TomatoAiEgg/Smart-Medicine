package com.zhyf.authinstitution.api;

import com.zhyf.authinstitution.app.InstitutionAppQueryService;
import com.zhyf.authinstitution.app.InstitutionAppView;
import com.zhyf.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/institution-apps")
public class InternalInstitutionAppController {

    private final InstitutionAppQueryService queryService;

    public InternalInstitutionAppController(InstitutionAppQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{appKey}")
    public ApiResponse<InstitutionAppView> getApp(@PathVariable String appKey) {
        return ApiResponse.ok(queryService.getEnabledApp(appKey));
    }
}
