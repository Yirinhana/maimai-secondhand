package com.maimai.common;

import org.springframework.http.HttpStatus;

/** 业务异常：统一携带业务码与 HTTP 状态，全局处理器转换为一致响应。 */
public class BizException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BizException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BizException badRequest(String code, String message) {
        return new BizException(code, message, HttpStatus.BAD_REQUEST);
    }

    public static BizException unauthorized(String message) {
        return new BizException("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }

    public static BizException forbidden(String message) {
        return new BizException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }

    public static BizException notFound(String message) {
        return new BizException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public static BizException conflict(String code, String message) {
        return new BizException(code, message, HttpStatus.CONFLICT);
    }

    public static BizException tooMany(String message) {
        return new BizException("RATE_LIMITED", message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
