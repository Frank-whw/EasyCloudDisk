package com.clouddisk.dto;

import com.clouddisk.entity.SharePermission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

/**
 * 创建或更新共享的请求体。
 */
@Data
public class ShareRequest {

    @NotBlank(message = "目标用户邮箱不能为空")
    @Email(message = "目标用户邮箱格式不正确")
    private String targetEmail;

    @NotNull(message = "权限类型必填")
    private SharePermission permission;

    /**
     * 共享到期时间，可选。为空表示永久有效。
     */
    private Instant expiresAt;
}

