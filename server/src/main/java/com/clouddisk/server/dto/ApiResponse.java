package com.clouddisk.server.dto;


import lombok.Data;

/**
 * 统一API响应DTO
 * 用于标准化所有api的返回结果
 */
@Data
public class ApiResponse<T> {
    private String message;
    private T data;
    private Boolean success;
    private int code; // 业务状态码
    // 构造函数
    public ApiResponse(){}
    public ApiResponse(Boolean success, String message, T data, int code) {
        this.message = message;
        this.data = data;
        this.success = success;
        this.code = code;
    }
    // 静态工厂方法 - 创建成功响应
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "操作成功", data, 200);
    }
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, 200);
    }
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, 200);
    }
    // 静态工厂方法 - 创建失败响应
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, 500);
    }
    public static <T> ApiResponse<T> error(String message, int code) {
        return new ApiResponse<>(false, message, null, code);
    }
    public static <T> ApiResponse<T> error(String message, T data, int code) {
        return new ApiResponse<>(false, message, data, code);
    }
}
