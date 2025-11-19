package com.clouddisk.client.http;

import com.clouddisk.client.model.AuthRequest;
import com.clouddisk.client.model.AuthResponse;
import com.clouddisk.client.model.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthApiClient 单元测试
 * 测试客户端API封装的认证逻辑
 */
@ExtendWith(MockitoExtension.class)
class AuthApiClientTest {

    @Mock
    private CloseableHttpClient httpClient;

    @Mock
    private CloseableHttpResponse httpResponse;

    @Mock
    private HttpEntity httpEntity;

    private AuthApiClient authApiClient;
    private String baseUrl;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:8080";
        authApiClient = new AuthApiClient(baseUrl, httpClient);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    void testLogin_Success() throws Exception {
        // Given
        String email = "test@example.com";
        String password = "password123";
        String token = "test-token-123";

        AuthResponse authResponse = new AuthResponse();
        authResponse.setToken(token);
        authResponse.setEmail(email);

        ApiResponse<AuthResponse> apiResponse = new ApiResponse<>();
        apiResponse.setSuccess(true);
        apiResponse.setData(authResponse);

        String responseJson = objectMapper.writeValueAsString(apiResponse);
        when(httpClient.execute(any(HttpPost.class), any())).thenAnswer(invocation -> {
            // 模拟响应处理器
            Object handler = invocation.getArgument(1);
            if (handler instanceof org.apache.hc.client5.http.classic.HttpClientResponseHandler) {
                @SuppressWarnings("unchecked")
                org.apache.hc.client5.http.classic.HttpClientResponseHandler<AuthResponse> responseHandler =
                    (org.apache.hc.client5.http.classic.HttpClientResponseHandler<AuthResponse>) handler;
                
                when(httpResponse.getCode()).thenReturn(200);
                when(httpResponse.getEntity()).thenReturn(httpEntity);
                when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(responseJson.getBytes()));
                
                return responseHandler.handleResponse((ClassicHttpResponse) httpResponse);
            }
            return null;
        });

        // When
        String result = authApiClient.login(email, password);

        // Then
        assertNotNull(result);
        verify(httpClient, atLeastOnce()).execute(any(HttpPost.class), any());
    }

    @Test
    void testLogin_NoToken_ThrowsException() throws Exception {
        // Given
        String email = "test@example.com";
        String password = "password123";

        AuthResponse authResponse = new AuthResponse();
        authResponse.setToken(""); // 空token

        ApiResponse<AuthResponse> apiResponse = new ApiResponse<>();
        apiResponse.setSuccess(true);
        apiResponse.setData(authResponse);

        String responseJson = objectMapper.writeValueAsString(apiResponse);
        when(httpClient.execute(any(HttpPost.class), any())).thenAnswer(invocation -> {
            Object handler = invocation.getArgument(1);
            if (handler instanceof org.apache.hc.client5.http.classic.HttpClientResponseHandler) {
                @SuppressWarnings("unchecked")
                org.apache.hc.client5.http.classic.HttpClientResponseHandler<AuthResponse> responseHandler =
                    (org.apache.hc.client5.http.classic.HttpClientResponseHandler<AuthResponse>) handler;
                
                when(httpResponse.getCode()).thenReturn(200);
                when(httpResponse.getEntity()).thenReturn(httpEntity);
                when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(responseJson.getBytes()));
                
                return responseHandler.handleResponse((ClassicHttpResponse) httpResponse);
            }
            return null;
        });

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authApiClient.login(email, password);
        });
        assertTrue(exception.getMessage().contains("登录失败"));
    }

    @Test
    void testRegister_Success() throws Exception {
        // Given
        String email = "test@example.com";
        String password = "password123";
        String token = "test-token-123";

        AuthResponse authResponse = new AuthResponse();
        authResponse.setToken(token);
        authResponse.setEmail(email);

        ApiResponse<AuthResponse> apiResponse = new ApiResponse<>();
        apiResponse.setSuccess(true);
        apiResponse.setData(authResponse);

        String responseJson = objectMapper.writeValueAsString(apiResponse);
        when(httpClient.execute(any(HttpPost.class), any())).thenAnswer(invocation -> {
            Object handler = invocation.getArgument(1);
            if (handler instanceof org.apache.hc.client5.http.classic.HttpClientResponseHandler) {
                @SuppressWarnings("unchecked")
                org.apache.hc.client5.http.classic.HttpClientResponseHandler<AuthResponse> responseHandler =
                    (org.apache.hc.client5.http.classic.HttpClientResponseHandler<AuthResponse>) handler;
                
                when(httpResponse.getCode()).thenReturn(200);
                when(httpResponse.getEntity()).thenReturn(httpEntity);
                when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(responseJson.getBytes()));
                
                return responseHandler.handleResponse((ClassicHttpResponse) httpResponse);
            }
            return null;
        });

        // When
        boolean result = authApiClient.register(email, password);

        // Then
        assertTrue(result);
        verify(httpClient, atLeastOnce()).execute(any(HttpPost.class), any());
    }

    @Test
    void testRegister_Failure_ReturnsFalse() throws Exception {
        // Given
        String email = "test@example.com";
        String password = "password123";

        when(httpClient.execute(any(HttpPost.class), any())).thenThrow(new IOException("Connection error"));

        // When
        boolean result = authApiClient.register(email, password);

        // Then
        assertFalse(result);
    }

    @Test
    void testLogin_IOException_Retries() throws Exception {
        // Given
        String email = "test@example.com";
        String password = "password123";

        // 前两次失败，第三次成功
        when(httpClient.execute(any(HttpPost.class), any()))
                .thenThrow(new IOException("Network error"))
                .thenThrow(new IOException("Network error"))
                .thenAnswer(invocation -> {
                    AuthResponse authResponse = new AuthResponse();
                    authResponse.setToken("test-token");

                    ApiResponse<AuthResponse> apiResponse = new ApiResponse<>();
                    apiResponse.setSuccess(true);
                    apiResponse.setData(authResponse);

                    String responseJson = objectMapper.writeValueAsString(apiResponse);
                    Object handler = invocation.getArgument(1);
                    if (handler instanceof org.apache.hc.client5.http.classic.HttpClientResponseHandler) {
                        @SuppressWarnings("unchecked")
                        org.apache.hc.client5.http.classic.HttpClientResponseHandler<AuthResponse> responseHandler =
                            (org.apache.hc.client5.http.classic.HttpClientResponseHandler<AuthResponse>) handler;
                        
                        when(httpResponse.getCode()).thenReturn(200);
                        when(httpResponse.getEntity()).thenReturn(httpEntity);
                        when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(responseJson.getBytes()));
                        
                        return responseHandler.handleResponse((ClassicHttpResponse) httpResponse);
                    }
                    return null;
                });

        // When
        String result = authApiClient.login(email, password);

        // Then
        assertNotNull(result);
        // 验证重试了3次
        verify(httpClient, atLeast(3)).execute(any(HttpPost.class), any());
    }
}

