package com.clouddisk.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private final boolean success;
    private final String message;
    private final String code;
    private final T data;
    private final Map<String, Object> details;

    private ApiResponse(boolean success, String message, String code, T data, Map<String, Object> details) {
        this.success = success;
        this.message = message;
        this.code = code;
        this.data = data;
        this.details = details;
    }

    public static <T> ApiResponse<T> success(String message, String code, T data) {
        return new ApiResponse<>(true, message, code, data, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", "SUCCESS", data, null);
    }

    public static <T> ApiResponse<T> error(String message, String code) {
        return new ApiResponse<>(false, message, code, null, null);
    }

    public static <T> ApiResponse<T> error(String message, String code, Map<String, Object> details) {
        return new ApiResponse<>(false, message, code, null, details);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getCode() {
        return code;
    }

    public T getData() {
        return data;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
