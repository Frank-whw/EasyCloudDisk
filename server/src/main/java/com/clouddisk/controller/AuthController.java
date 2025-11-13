package com.clouddisk.controller;

import com.clouddisk.dto.ApiResponse;
import com.clouddisk.dto.AuthRequest;
import com.clouddisk.dto.AuthResponse;
import com.clouddisk.dto.RegisterRequest;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.security.UserPrincipal;
import com.clouddisk.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 认证相关接口，提供注册、登录、刷新令牌及注销能力。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户注册接口。
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request for {}", request.getEmail());
        AuthResponse response = userService.register(request);
        return ResponseEntity.ok(ApiResponse.success("注册成功", ErrorCode.SUCCESS.name(), response));
    }

    /**
     * 用户登录接口。
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        log.info("Login attempt for {}", request.getEmail());
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success("登录成功", ErrorCode.SUCCESS.name(), response));
    }

    /**
     * 使用刷新令牌换取新的访问令牌。
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestParam("token") String refreshToken) {
        AuthResponse response = userService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success("刷新成功", ErrorCode.SUCCESS.name(), response));
    }

    /**
     * 注销当前登录的用户。
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        userService.logout(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("已退出", ErrorCode.SUCCESS.name(), null));
    }
}
