package com.zhyf.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("zhyf.admin-data-scope")
public class AdminDataScopeProperties {

    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
