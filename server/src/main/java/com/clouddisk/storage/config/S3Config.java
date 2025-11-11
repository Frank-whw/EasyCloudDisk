package com.clouddisk.storage.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Duration;

@Configuration
public class S3Config {
    
    @Value("${aws.access-key-id:}")
    private String accessKeyId;
    
    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;
    
    @Value("${aws.region}")
    private String region;
    
    @Value("${aws.s3.endpoint:}")
    private String endpoint; // 用于兼容其他S3服务，如MinIO

    @Value("${aws.s3.path-style:false}")
    private boolean pathStyleAccess;

    @Value("${aws.http.max-connections:64}")
    private int maxConnections;

    @Value("${aws.http.connection-timeout-ms:10000}")
    private int connectionTimeoutMs;

    @Value("${aws.http.read-timeout-ms:60000}")
    private int readTimeoutMs;
    
    @Bean
    public S3Client s3Client() {
        // 配置 HTTP 客户端：连接池、超时等
        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder()
                .maxConnections(maxConnections)
                .connectionTimeout(Duration.ofMillis(connectionTimeoutMs))
                .socketTimeout(Duration.ofMillis(readTimeoutMs));

        // 可选：支持代理（如果环境需要，保持默认关闭）
        httpClientBuilder.proxyConfiguration(ProxyConfiguration.builder().build());

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccess)
                        .build())
                .httpClientBuilder(httpClientBuilder)
                .overrideConfiguration(cfg -> cfg.retryPolicy(RetryPolicy.defaultRetryPolicy()));
        
        // 如果显式配置了访问密钥，则使用静态凭证
        if (accessKeyId != null && !accessKeyId.isEmpty() 
                && secretAccessKey != null && !secretAccessKey.isEmpty()) {
            AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(
                    accessKeyId, secretAccessKey);
            builder.credentialsProvider(StaticCredentialsProvider.create(awsCredentials));
        } else {
            // 否则使用默认凭证链（从环境变量、~/.aws/credentials、EC2实例角色等读取）
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        
        // 如果有自定义endpoint（如MinIO、Ceph 等兼容服务），则设置 endpoint
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }
}