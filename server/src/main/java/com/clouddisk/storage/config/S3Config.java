package com.clouddisk.storage.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

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
    
    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region));
        
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
        
        // 如果有自定义endpoint（如MinIO），则设置endpoint
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        
        return builder.build();
    }
}