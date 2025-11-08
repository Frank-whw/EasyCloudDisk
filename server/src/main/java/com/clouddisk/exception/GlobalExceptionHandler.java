package com.clouddisk.exception;

import com.clouddisk.dto.ApiResponse;
import com.clouddisk.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.NoSuchElementException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理业务异常，使用异常内置的业务状态码
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.error("业务异常: {}", e.getMessage(), e);
        int code = e.getCode();
        ApiResponse<Void> response = ApiResponse.error(e.getMessage(), code);
        return ResponseEntity.status(code).body(response);
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: {}", e.getMessage(), e);
        
        String message = e.getMessage();
        int code = 400;
        
        // 根据异常消息判断具体错误类型
        if ("文件不存在".equals(message)) {
            code = 404;
        } else if ("无权访问此文件".equals(message) || "无权删除此文件".equals(message)) {
            code = 403;
        } else if (message != null && message.contains("文件重复")) {
            code = 409;
        }
        
        ApiResponse<Void> response = ApiResponse.error(message, code);
        return ResponseEntity.status(code).body(response);
    }

    /**
     * 处理文件不存在异常
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoSuchElementException(NoSuchElementException e) {
        log.error("资源不存在异常: {}", e.getMessage(), e);
        ApiResponse<Void> response = ApiResponse.error("资源不存在", 404);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 处理认证异常
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(BadCredentialsException e) {
        log.error("认证失败: {}", e.getMessage(), e);
        ApiResponse<Void> response = ApiResponse.error("用户名或密码错误", 401);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * 处理权限异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.error("权限不足: {}", e.getMessage(), e);
        ApiResponse<Void> response = ApiResponse.error("权限不足", 403);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * 处理文件上传大小限制异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("文件上传大小超限: {}", e.getMessage(), e);
        ApiResponse<Void> response = ApiResponse.error("文件大小不能超过100MB", 413);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("非法参数: {}", e.getMessage(), e);
        ApiResponse<Void> response = ApiResponse.error("参数错误: " + e.getMessage(), 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("未处理的异常: {}", e.getMessage(), e);
        ApiResponse<Void> response = ApiResponse.error("服务器内部错误", 500);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}