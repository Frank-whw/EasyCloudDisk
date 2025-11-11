package com.clouddisk.client.http;

import com.clouddisk.client.model.ApiResponse;
import com.clouddisk.client.model.AuthRequest;
import com.clouddisk.client.model.AuthResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import com.clouddisk.client.config.RetryTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
public class AuthApiClient {
    // 服务器基础URL，例如 "http://ec2-54-95-61-230.ap-northeast-1.compute.amazonaws.com:8080"
    private final String baseUrl;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AuthApiClient(String baseUrl) {
        this(baseUrl, HttpClients.createDefault());
    }

    public AuthApiClient(String baseUrl, CloseableHttpClient httpClient) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        // 初始化 ObjectMapper 实例
        // ObjectMapper 是线程安全的，可以复用
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 发送登录请求
     */
    public String login(String email, String password) {
        return RetryTemplate.executeWithRetry(() -> {
            try {
                AuthRequest request = new AuthRequest(email, password);
                
                String json = objectMapper.writeValueAsString(request);
                StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
                
                HttpPost httpPost = new HttpPost(baseUrl + "/api/auth/login");
                httpPost.setEntity(entity);
                
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    int statusCode = response.getCode();
                    String responseBody = EntityUtils.toString(response.getEntity());
                    
                    if (statusCode == 200) {
                        AuthResponse authResponse = objectMapper.readValue(responseBody, AuthResponse.class);
                        if (authResponse.getToken() != null) {
                            log.info("用户 {} 登录成功", email);
                            return authResponse.getToken();
                        } else {
                            log.error("登录响应中没有token");
                            return null;
                        }
                    } else {
                        log.error("登录失败，状态码: {}, 响应: {}", statusCode, responseBody);
                        throw new RuntimeException("登录失败: " + responseBody);
                    }
                }
            } catch (Exception e) {
                log.error("登录请求失败", e);
                throw new RuntimeException("登录请求失败: " + e.getMessage(), e);
            }
        }, 3); // 重试3次
    }

    /**
     * 用户注册
     * @param email 邮箱
     * @param password 密码
     * @return 注册是否成功
     */
    public boolean register(String email, String password) {
        try {
            AuthRequest request = new AuthRequest(email, password);
            AuthResponse response = sendAuthRequest("/auth/register", request);
            return response != null && response.getToken() != null;
        } catch (IOException | ParseException e) {
            System.out.println("注册请求失败: " + e.getMessage());
            return false;
        } catch (RuntimeException e) {
            System.out.println("注册失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 发送认证请求
     * @param endpoint 请求端点
     * @param request 请求数据
     * @return 认证响应
     * @throws IOException IO异常
     * @throws ParseException 解析异常
     */
    private AuthResponse sendAuthRequest(String endpoint, AuthRequest request) throws IOException, ParseException {
        // 创建一个 HTTP POST 请求对象，指定完整的 URL
        // baseUrl + endpoint 组合构成完整的请求地址
        HttpPost httpPost = new HttpPost(baseUrl + endpoint);
        
        // 设置请求头，指定内容类型为 JSON
        // 这是必须的，因为服务端期望接收 JSON 格式的数据
        httpPost.setHeader("Content-Type", "application/json");

        // 使用 Jackson ObjectMapper 将请求对象序列化为 JSON 字符串
        // 这样可以确保数据以正确的格式发送到服务端
        String json = objectMapper.writeValueAsString(request);
        
        // 将 JSON 字符串包装为 StringEntity 并设置字符编码
        // 使用 StandardCharsets.UTF_8 确保字符编码一致
        httpPost.setEntity(new StringEntity(json, StandardCharsets.UTF_8));

        // 执行 HTTP 请求并处理响应
        // 使用 httpClient.execute() 方法发送请求并接收响应
        // 第二个参数是一个 ResponseHandler，用于处理响应并返回结果
        return httpClient.execute(httpPost, response -> {
            return handleAuthResponse(response);
        });
    }

    /**
     * 处理认证响应
     * @param response HTTP响应
     * @return 认证响应
     * @throws IOException IO异常
     * @throws ParseException 解析异常
     */
    private AuthResponse handleAuthResponse(ClassicHttpResponse response) throws IOException, ParseException {
        String jsonResponse = EntityUtils.toString(response.getEntity());
        
        // 检查响应状态码
        if (response.getCode() >= 200 && response.getCode() < 300) {
            // 成功响应
            ApiResponse<AuthResponse> apiResponse = objectMapper.readValue(jsonResponse, 
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, AuthResponse.class));
            
            if (apiResponse.isSuccess()) {
                return apiResponse.getData();
            } else {
                throw new RuntimeException("认证失败: " + apiResponse.getMessage());
            }
        } else {
            // 错误响应
            try {
                ApiResponse<AuthResponse> apiResponse = objectMapper.readValue(jsonResponse, 
                    objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, AuthResponse.class));
                throw new RuntimeException("服务器错误: " + apiResponse.getMessage() + " (代码: " + apiResponse.getCode() + ")");
            } catch (Exception e) {
                // 如果无法解析为ApiResponse格式，则直接抛出原始响应
                throw new RuntimeException("HTTP错误: " + response.getCode() + " - " + jsonResponse);
            }
        }
    }
}