package com.clouddisk.server.exception;

public enum ErrorCode {
    SUCCESS("成功"),
    EMAIL_EXISTS("邮箱已存在"),
    INVALID_CREDENTIALS("用户名或密码错误"),
    USER_NOT_FOUND("用户不存在"),
    FILE_NOT_FOUND("文件不存在"),
    DIRECTORY_ALREADY_EXISTS("目录已存在"),
    STORAGE_ERROR("存储服务异常"),
    ACCESS_DENIED("无权限访问"),
    VALIDATION_ERROR("参数校验失败"),
    INTERNAL_ERROR("服务器内部错误");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
