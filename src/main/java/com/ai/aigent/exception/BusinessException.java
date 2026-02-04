package com.ai.aigent.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {
     private final int code;
    private final HttpStatus httpStatus;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = HttpStatus.BAD_REQUEST;
    }

    public BusinessException(int code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = HttpStatus.BAD_REQUEST;
    }

    // 常用业务异常快捷方法
    public static BusinessException badRequest(String message) {
        return new BusinessException(400, message, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, message, HttpStatus.UNAUTHORIZED);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message, HttpStatus.FORBIDDEN);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(404, message, HttpStatus.NOT_FOUND);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(409, message, HttpStatus.CONFLICT);
    }

    public static BusinessException internalError(String message) {
        return new BusinessException(500, message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
