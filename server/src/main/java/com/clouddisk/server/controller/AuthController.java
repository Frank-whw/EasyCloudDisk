package com.clouddisk.server.controller;

import com.clouddisk.dto.ApiResponse;
import com.clouddisk.server.dto.AuthRequest;
import com.clouddisk.server.dto.AuthResponse;
import com.clouddisk.server.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 处理用户注册和登录请求
 */
@RestController // 标识为控制器
@RequestMapping("/auth") // 请求映射
@Slf4j
public class AuthController {
    private final UserService userService;
    @Autowired
    public AuthController(UserService userService) {
        this.userService = userService;
    }
    /**
     * 用户注册
     * @param authRequest 注册请求
     * @return 注册响应
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody AuthRequest authRequest){
        // 调用UserService进行注册，异常由全局异常处理器统一处理
        AuthResponse authResponse = userService.register(authRequest);
        return ResponseEntity.ok(ApiResponse.success("注册成功", authResponse));
    }
    /**
     * 用户登录
     * @param authRequest 登录请求
     * @return 登录响应
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest authRequest){
        // 调用UserService进行登录，异常由全局异常处理器统一处理
        AuthResponse authResponse = userService.login(authRequest);
        return ResponseEntity.ok(ApiResponse.success("登录成功", authResponse));
    }

}
