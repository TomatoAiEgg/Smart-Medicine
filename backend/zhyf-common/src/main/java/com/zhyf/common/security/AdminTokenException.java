package com.zhyf.common.security;

public class AdminTokenException extends RuntimeException {

    public AdminTokenException(String message) {
        super(message);
    }

    public AdminTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
