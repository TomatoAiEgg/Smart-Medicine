package com.zhyf.authinstitution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zhyf.admin-auth")
public class AdminAuthProperties {

    private String jwtSecret;
    private String issuer = "zhyf-admin";
    private long accessTokenSeconds = 28800;
    private int maxLoginFailures = 5;
    private long lockSeconds = 900;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAccessTokenSeconds() {
        return accessTokenSeconds;
    }

    public void setAccessTokenSeconds(long accessTokenSeconds) {
        this.accessTokenSeconds = Math.max(300, accessTokenSeconds);
    }

    public int getMaxLoginFailures() {
        return maxLoginFailures;
    }

    public void setMaxLoginFailures(int maxLoginFailures) {
        this.maxLoginFailures = Math.max(1, maxLoginFailures);
    }

    public long getLockSeconds() {
        return lockSeconds;
    }

    public void setLockSeconds(long lockSeconds) {
        this.lockSeconds = Math.max(60, lockSeconds);
    }
}
