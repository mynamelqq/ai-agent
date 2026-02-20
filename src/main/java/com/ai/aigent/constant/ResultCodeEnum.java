package com.ai.aigent.constant;

public enum ResultCodeEnum {
     // 成功状态码
    SUCCESS(200, "操作成功"),

    // 客户端错误 400-499
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权访问"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "资源冲突"),

    // 业务错误 1000-1999
    USER_NOT_EXIST(1001, "用户不存在"),
    USER_EXIST(1002, "用户已存在"),
    USER_DISABLED(1003, "用户已被禁用"),
    PASSWORD_ERROR(1004, "密码错误"),
    CAPTCHA_ERROR(1005, "验证码错误"),
    TOKEN_INVALID(1006, "Token无效"),
    TOKEN_EXPIRED(1007, "Token已过期"),
    REFRESH_TOKEN_INVALID(1008, "刷新Token无效"),
    PERMISSION_DENIED(1009, "权限不足"),
    TOKEN_LIMIT_EXCEEDED(1010, "Token数量超限"),
    // 服务端错误 500-599
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),

    // 业务处理错误 2000-2999
    DATA_NOT_EXIST(2001, "数据不存在"),
    DATA_EXIST(2002, "数据已存在"),
    DATA_VALIDATE_FAILED(2003, "数据验证失败"),
    OPERATION_FAILED(2004, "操作失败"),
    FILE_UPLOAD_FAILED(2005, "文件上传失败"),
    FILE_DOWNLOAD_FAILED(2006, "文件下载失败"),
    FILE_NOT_EXIST(2007, "文件不存在"),

    // 第三方服务错误 3000-3999
    THIRD_PARTY_ERROR(3001, "第三方服务错误"),
    API_LIMIT_EXCEEDED(3002, "API调用次数超限"),

    // 系统错误 9000-9999
    SYSTEM_ERROR(9001, "系统错误"),
    DATABASE_ERROR(9002, "数据库错误"),
    NETWORK_ERROR(9003, "网络错误");

    private final int code;
    private final String message;

    ResultCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 根据code获取枚举
     */
    public static ResultCodeEnum getByCode(int code) {
        for (ResultCodeEnum resultCode : values()) {
            if (resultCode.getCode() == code) {
                return resultCode;
            }
        }
        return INTERNAL_SERVER_ERROR;
    }

    /**
     * 判断是否是成功状态码
     */
    public static boolean isSuccess(int code) {
        return code == SUCCESS.getCode();
    }
}
