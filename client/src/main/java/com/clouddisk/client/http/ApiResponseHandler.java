package com.clouddisk.client.http;

import com.clouddisk.client.model.ApiResponse;

/**
 * 通用的 API 响应解包工具，用于根据服务端的统一返回结构提取数据或抛出对应异常。
 */
public class ApiResponseHandler {
    /**
     * 解包API响应
     * @param response API响应
     * @param <T> 响应数据类型
     * @return 响应数据
     */
    public <T> T unwrap(ApiResponse<T> response) {
        if (response.isSuccess()) {
            return response.getData();
        }

        String message = response.getMessage() != null ? response.getMessage() : "服务器返回未知错误";
        String code = response.getCode();
        if (code == null) {
            throw new RuntimeException(message);
        }

        switch (code) {
            case "VALIDATION_ERROR":
            case "FILE_NOT_FOUND":
            case "DIRECTORY_ALREADY_EXISTS":
                throw new IllegalArgumentException(message);
            case "ACCESS_DENIED":
            case "UNAUTHORIZED":
            case "FORBIDDEN":
                throw new SecurityException(message);
            default:
                throw new RuntimeException(String.format("%s (code=%s)", message, code));
        }
    }
}
