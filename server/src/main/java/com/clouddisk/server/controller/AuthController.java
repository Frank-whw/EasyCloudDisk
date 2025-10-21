package com.clouddisk.server.controller;

import com.clouddisk.server.dto.ApiResponse;
import com.clouddisk.server.dto.AuthRequest;
import com.clouddisk.server.dto.AuthResponse;
import com.clouddisk.server.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 处理用户注册和登录请求
 */
@RestController // 标识为控制器
@RequestMapping("/auth") // 请求映射
@CrossOrigin(origins = "*") // 允许跨域请求
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
        try {
            // 1. 调用UserService进行注册
            AuthResponse authResponse = userService.register(authRequest);

            // 2. 构造并返回成功响应
            ApiResponse<AuthResponse> response = new ApiResponse<AuthResponse>(true, "注册成功", authResponse, 200);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<AuthResponse> errorResponse = new ApiResponse<AuthResponse>(false, "注册失败" + e.getMessage(), null, 400);
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    /**
     * 用户登录
     * @param authRequest 登录请求
     * @return 登录响应
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest authRequest){
        try {
            // 1. 调用UserService进行登录
            AuthResponse authResponse = userService.login(authRequest);

            // 2. 构造并返回成功响应
            ApiResponse<AuthResponse> response = new ApiResponse<AuthResponse>(true, "登录成功", authResponse, 200);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<AuthResponse> errorResponse = new ApiResponse<AuthResponse>(false, "登录失败" + e.getMessage(), null, 400);
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

}
