package com.clouddisk.server.controller;

import com.clouddisk.server.dto.ApiResponse;
import com.clouddisk.server.dto.AuthRequest;
import com.clouddisk.server.dto.AuthResponse;
import com.clouddisk.server.dto.RegisterRequest;
import com.clouddisk.server.exception.BusinessException;
import com.clouddisk.server.exception.ErrorCode;
import com.clouddisk.server.security.UserPrincipal;
import com.clouddisk.server.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request for {}", request.getEmail());
        AuthResponse response = userService.register(request);
        return ResponseEntity.ok(ApiResponse.success("注册成功", ErrorCode.SUCCESS.name(), response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        log.info("Login attempt for {}", request.getEmail());
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success("登录成功", ErrorCode.SUCCESS.name(), response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestParam("token") String refreshToken) {
        AuthResponse response = userService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success("刷新成功", ErrorCode.SUCCESS.name(), response));
    }

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
