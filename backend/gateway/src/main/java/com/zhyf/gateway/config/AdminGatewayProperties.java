package com.zhyf.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zhyf.admin-gateway")
public class AdminGatewayProperties {

    private String jwtSecret;
    private String issuer = "zhyf-admin";
    private String authInstitutionBaseUrl;
    private String orderServiceBaseUrl;
    private String workflowServiceBaseUrl;
    private String messageServiceBaseUrl;
    private String decoctionServiceBaseUrl;
    private String opsServiceBaseUrl;
    private String logisticsServiceBaseUrl;
    private String callbackServiceBaseUrl;
    private String portalServiceBaseUrl;
    private String reportServiceBaseUrl;
    private String integrationServiceBaseUrl;

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

    public String getAuthInstitutionBaseUrl() {
        return authInstitutionBaseUrl;
    }

    public void setAuthInstitutionBaseUrl(String authInstitutionBaseUrl) {
        this.authInstitutionBaseUrl = authInstitutionBaseUrl;
    }

    public String getOrderServiceBaseUrl() {
        return orderServiceBaseUrl;
    }

    public void setOrderServiceBaseUrl(String orderServiceBaseUrl) {
        this.orderServiceBaseUrl = orderServiceBaseUrl;
    }

    public String getWorkflowServiceBaseUrl() {
        return workflowServiceBaseUrl;
    }

    public void setWorkflowServiceBaseUrl(String workflowServiceBaseUrl) {
        this.workflowServiceBaseUrl = workflowServiceBaseUrl;
    }

    public String getMessageServiceBaseUrl() {
        return messageServiceBaseUrl;
    }

    public void setMessageServiceBaseUrl(String messageServiceBaseUrl) {
        this.messageServiceBaseUrl = messageServiceBaseUrl;
    }

    public String getDecoctionServiceBaseUrl() {
        return decoctionServiceBaseUrl;
    }

    public void setDecoctionServiceBaseUrl(String decoctionServiceBaseUrl) {
        this.decoctionServiceBaseUrl = decoctionServiceBaseUrl;
    }

    public String getOpsServiceBaseUrl() {
        return opsServiceBaseUrl;
    }

    public void setOpsServiceBaseUrl(String opsServiceBaseUrl) {
        this.opsServiceBaseUrl = opsServiceBaseUrl;
    }

    public String getLogisticsServiceBaseUrl() {
        return logisticsServiceBaseUrl;
    }

    public void setLogisticsServiceBaseUrl(String logisticsServiceBaseUrl) {
        this.logisticsServiceBaseUrl = logisticsServiceBaseUrl;
    }

    public String getCallbackServiceBaseUrl() {
        return callbackServiceBaseUrl;
    }

    public void setCallbackServiceBaseUrl(String callbackServiceBaseUrl) {
        this.callbackServiceBaseUrl = callbackServiceBaseUrl;
    }

    public String getPortalServiceBaseUrl() {
        return portalServiceBaseUrl;
    }

    public void setPortalServiceBaseUrl(String portalServiceBaseUrl) {
        this.portalServiceBaseUrl = portalServiceBaseUrl;
    }

    public String getReportServiceBaseUrl() {
        return reportServiceBaseUrl;
    }

    public void setReportServiceBaseUrl(String reportServiceBaseUrl) {
        this.reportServiceBaseUrl = reportServiceBaseUrl;
    }

    public String getIntegrationServiceBaseUrl() {
        return integrationServiceBaseUrl;
    }

    public void setIntegrationServiceBaseUrl(String integrationServiceBaseUrl) {
        this.integrationServiceBaseUrl = integrationServiceBaseUrl;
    }
}
