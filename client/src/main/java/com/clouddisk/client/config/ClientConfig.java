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

@Configuration
@RequiredArgsConstructor
public class ClientConfig {
    
    private final ClientProperties clientProperties;
    
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
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(clientProperties.getS3MaxRetries(), TimeValue.ofSeconds(1L)))
                .build();
    }
}