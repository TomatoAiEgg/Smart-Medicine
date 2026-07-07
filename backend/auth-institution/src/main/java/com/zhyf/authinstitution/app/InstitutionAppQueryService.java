package com.zhyf.authinstitution.app;

import com.zhyf.authinstitution.infrastructure.InstitutionAppRepository;
import com.zhyf.common.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class InstitutionAppQueryService {

    private final InstitutionAppRepository repository;

    public InstitutionAppQueryService(InstitutionAppRepository repository) {
        this.repository = repository;
    }

    public InstitutionAppView getEnabledApp(String appKey) {
        return repository.findEnabledApp(appKey)
                .orElseThrow(() -> new BusinessException("APP_NOT_FOUND", "机构应用不存在或已停用"));
    }
}
