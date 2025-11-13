package com.clouddisk.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 新建目录请求载荷。
 */
public class DirectoryRequest {

    @NotBlank(message = "目录路径不能为空")
    private String path;

    @NotBlank(message = "目录名称不能为空")
    private String name;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
