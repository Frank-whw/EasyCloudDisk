package com.clouddisk.client.http;

import com.clouddisk.client.model.ApiResponse;

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
        } else {
            // 根据不同的错误码抛出不同的异常
            switch (response.getCode()) {
                case 400:
                    throw new IllegalArgumentException(response.getMessage());
                case 401:
                    throw new SecurityException(response.getMessage());
                default:
                    throw new RuntimeException(response.getMessage());
            }
        }
    }
}