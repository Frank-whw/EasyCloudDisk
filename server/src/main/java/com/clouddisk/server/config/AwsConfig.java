package com.clouddisk.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(AwsProperties.class)
public class AwsConfig {

    @Bean
    public SdkHttpClient s3HttpClient(AwsProperties properties) {
        return ApacheHttpClient.builder()
                .maxConnections(properties.getHttp().getMaxConnections())
                .connectionTimeout(java.time.Duration.ofMillis(properties.getHttp().getConnectionTimeoutMs()))
                .socketTimeout(java.time.Duration.ofMillis(properties.getHttp().getReadTimeoutMs()))
                .build();
    }

    @Bean
    public S3Client s3Client(AwsProperties properties, SdkHttpClient httpClient) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKeyId(),
                properties.getSecretAccessKey()
        );

        S3Configuration.Builder s3ConfigBuilder = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.getS3().isPathStyle());

        S3Client.Builder builder = S3Client.builder()
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
