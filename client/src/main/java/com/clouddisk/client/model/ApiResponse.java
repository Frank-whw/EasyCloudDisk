package com.clouddisk.client.model;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    /**
     * 后端使用字符串枚举表示错误码（例如 SUCCESS、VALIDATION_ERROR）。
     */
    private String code;
    /**
     * 当服务端返回额外的字段说明错误细节时，保留这些键值信息，方便客户端展示。
     */
    private Map<String, Object> details;
}
