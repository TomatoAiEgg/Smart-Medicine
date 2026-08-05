package com.zhyf.gateway.api;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus status = switch (ex.code()) {
            case "ADMIN_BACKEND_UNAVAILABLE", "ADMIN_BACKEND_INTERRUPTED" -> HttpStatus.BAD_GATEWAY;
            case "ADMIN_BACKEND_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "ADMIN_TENANT_WIDE_REQUIRED", "ADMIN_SELF_REVOKE_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingRequestHeader(MissingRequestHeaderException ex) {
        return ApiResponse.fail("SIGN_HEADER_REQUIRED", "签名请求头不能为空");
    }
}
