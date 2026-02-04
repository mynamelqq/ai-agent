package com.ai.aigent.exception;

public class CosServiceException extends RuntimeException {
    private String code;

    public CosServiceException(String message) {
        super(message);
    }

    public CosServiceException(String code, String message) {
        super(message);
        this.code = code;
    }

    public CosServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getCode() {
        return code;
    }
}