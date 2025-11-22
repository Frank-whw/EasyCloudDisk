package com.clouddisk.client.config;

import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 客户端 Spring 配置类。
 * <p>
 * 提供共享的 HTTP 客户端实例，确保与服务器及 S3 通信时的连接、超时和重试策略统一。
 */
@Configuration
@RequiredArgsConstructor
public class ClientConfig {
    
    private final ClientProperties clientProperties;
    
    /**
     * 构建配置完善的 {@link CloseableHttpClient}，供 REST 与 S3 调用复用。
     */
    @Bean
    public CloseableHttpClient httpClient() {
        // 连接池配置 - 与S3配置保持一致
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(clientProperties.getHttpMaxConnections());
        connectionManager.setDefaultMaxPerRoute(clientProperties.getHttpMaxConnections() / 3);
        
        // 连接配置 - 与S3配置保持一致
        connectionManager.setDefaultConnectionConfig(
                ConnectionConfig.custom()
                        .setConnectTimeout(clientProperties.getHttpConnTimeoutMs(), TimeUnit.MILLISECONDS)
                        .setSocketTimeout(clientProperties.getHttpReadTimeoutMs(), TimeUnit.MILLISECONDS)
                        .setTimeToLive(TimeValue.of(30, TimeUnit.SECONDS))
                        .build());
        
        // 请求配置 - 统一超时设置
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(clientProperties.getHttpConnTimeoutMs(), TimeUnit.MILLISECONDS)
                .setResponseTimeout(clientProperties.getHttpReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
        
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(
                        clientProperties.getS3MaxRetries(), TimeValue.ofSeconds(1L)))
                .build();
    }
}