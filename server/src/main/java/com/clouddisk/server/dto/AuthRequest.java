package com.clouddisk.server.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 认证请求DTO
 * 登录和注册的请求都使用这个DTO
 */
@Data
public class AuthRequest {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式错误")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20个字符之间")
    private String password;

    // 构造函数
    public AuthRequest(){}
    public AuthRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

}
