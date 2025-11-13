package com.clouddisk.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新建目录请求载荷。
 */
@Data
public class DirectoryRequest {

    @NotBlank(message = "目录路径不能为空")
    private String path;

    @NotBlank(message = "目录名称不能为空")
    private String name;

}
