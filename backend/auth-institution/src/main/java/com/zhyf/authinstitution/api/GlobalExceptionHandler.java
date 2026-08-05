package com.zhyf.authinstitution.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus status = switch (ex.code()) {
            case "ADMIN_LOGIN_FAILED", "ADMIN_REFRESH_TOKEN_INVALID", "ADMIN_REFRESH_TOKEN_EXPIRED",
                    "ADMIN_SESSION_REVOKED" -> HttpStatus.UNAUTHORIZED;
            case "ADMIN_ACCOUNT_LOCKED" -> HttpStatus.LOCKED;
            case "ADMIN_ACCOUNT_DISABLED", "ADMIN_CREDENTIAL_NOT_READY", "ADMIN_ROLE_REQUIRED" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.code(), ex.getMessage()));
    }
}
