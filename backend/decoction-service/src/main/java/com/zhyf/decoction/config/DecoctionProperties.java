package com.zhyf.decoction.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zhyf.decoction")
public class DecoctionProperties {

    private String orderServiceBaseUrl = "http://localhost:18082";
    private List<String> deviceCodes = new ArrayList<>(List.of(
            "DECOCT-001",
            "DECOCT-002",
            "DECOCT-003",
            "DECOCT-004",
            "DECOCT-005"
    ));

    public String getOrderServiceBaseUrl() {
        return orderServiceBaseUrl;
    }

    public void setOrderServiceBaseUrl(String orderServiceBaseUrl) {
        this.orderServiceBaseUrl = orderServiceBaseUrl;
    }

    public List<String> getDeviceCodes() {
        return deviceCodes;
    }

    public void setDeviceCodes(List<String> deviceCodes) {
        this.deviceCodes = deviceCodes;
    }
}
