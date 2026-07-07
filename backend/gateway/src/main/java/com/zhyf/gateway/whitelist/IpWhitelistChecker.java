package com.zhyf.gateway.whitelist;

import com.zhyf.common.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IpWhitelistChecker {

    public void check(List<String> whitelist, String requestIp) {
        if (whitelist == null || whitelist.isEmpty()) {
            return;
        }
        boolean matched = whitelist.stream().anyMatch(range -> matches(range, requestIp));
        if (!matched) {
            throw new BusinessException("IP_NOT_ALLOWED", "请求 IP 不在白名单内");
        }
    }

    private boolean matches(String range, String requestIp) {
        if (!StringUtils.hasText(range) || !StringUtils.hasText(requestIp)) {
            return false;
        }
        if ("*".equals(range.trim())) {
            return true;
        }
        if (!range.contains("/")) {
            return range.trim().equals(requestIp);
        }
        String[] parts = range.split("/", 2);
        if (parts.length != 2) {
            return false;
        }
        try {
            long base = ipv4ToLong(parts[0].trim());
            int prefix = Integer.parseInt(parts[1].trim());
            if (prefix < 0 || prefix > 32) {
                return false;
            }
            long mask = prefix == 0 ? 0 : 0xffffffffL << (32 - prefix);
            return (ipv4ToLong(requestIp) & mask) == (base & mask);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private long ipv4ToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address");
        }
        long result = 0;
        for (String part : parts) {
            int value = Integer.parseInt(part);
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("Invalid IPv4 segment");
            }
            result = (result << 8) + value;
        }
        return result;
    }
}
