package com.clouddisk.client.http;

import com.clouddisk.client.model.AuthRequest;
import com.clouddisk.client.model.AuthResponse;
import com.clouddisk.client.model.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.ClassicHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class AuthApiClient {
    // 服务器基础URL，例如 "http://localhost:8080"
    private final String baseUrl;
    private final CloseableHttpClient httpClient;
    
    // Jackson ObjectMapper 实例，用于JSON序列化和反序列化
    // 将其作为字段存储是为了避免重复创建ObjectMapper实例，提高性能
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
     * 用户登录
     * @param email 邮箱
     * @param password 密码
     * @return JWT令牌，如果登录失败返回null
     */
    public String login(String email, String password) {
        try {
            AuthRequest request = new AuthRequest(email, password);
            AuthResponse response = sendAuthRequest("/auth/login", request);
            return response.getToken();
        } catch (IOException | ParseException e) {
            System.err.println("登录请求失败: " + e.getMessage());
            return null;
        } catch (RuntimeException e) {
            System.err.println("登录失败: " + e.getMessage());
            return null;
        }
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
            System.err.println("注册请求失败: " + e.getMessage());
            return false;
        } catch (RuntimeException e) {
            System.err.println("注册失败: " + e.getMessage());
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