package com.ai.aigent.exception;

public class TokenLimitExceed extends RuntimeException {
    private int code;
    public TokenLimitExceed(String message) {
        super(message);
    }
    public TokenLimitExceed(int code, String message) {
        super(message);
        this.code = code;
    }
    public int getCode() {
        return code;
    }
}
