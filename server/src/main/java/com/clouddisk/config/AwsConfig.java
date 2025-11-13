package com.clouddisk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * AWS 客户端配置，负责构建复用的 HTTP 客户端与 S3 客户端实例。
 */
@Configuration
@EnableConfigurationProperties(AwsProperties.class)
public class AwsConfig {

    /**
     * 构建供 S3 客户端复用的 HTTP 客户端。
     */
    @Bean
    public SdkHttpClient s3HttpClient(AwsProperties properties) {
        return ApacheHttpClient.builder()
                .maxConnections(properties.getHttp().getMaxConnections())
                .connectionTimeout(java.time.Duration.ofMillis(properties.getHttp().getConnectionTimeoutMs()))
                .socketTimeout(java.time.Duration.ofMillis(properties.getHttp().getReadTimeoutMs()))
                .build();
    }

    /**
     * 根据配置创建 {@link S3Client} 实例。
     */
    @Bean
    public S3Client s3Client(AwsProperties properties, SdkHttpClient httpClient) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKeyId(),
                properties.getSecretAccessKey()
        );

        S3Configuration.Builder s3ConfigBuilder = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.getS3().isPathStyle());

        S3ClientBuilder builder = S3Client.builder()
                .httpClient(httpClient)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(s3ConfigBuilder.build());

        if (properties.getS3().getEndpoint() != null && !properties.getS3().getEndpoint().isBlank()) {
            builder = builder.endpointOverride(URI.create(properties.getS3().getEndpoint()));
        }
        return builder.build();
    }
}
