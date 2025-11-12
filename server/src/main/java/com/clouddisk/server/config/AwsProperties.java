package com.clouddisk.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "aws")
@Validated
public class AwsProperties {
    @NotBlank
    private String accessKeyId;

    @NotBlank
    private String secretAccessKey;

    @NotBlank
    private String region;

    @NotNull
    private final S3Properties s3 = new S3Properties();

    @NotNull
    private final HttpProperties http = new HttpProperties();

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public S3Properties getS3() {
        return s3;
    }

    public HttpProperties getHttp() {
        return http;
    }

    public static class S3Properties {
        @NotBlank
        private String bucketName;
        private String endpoint;
        private boolean pathStyle;
        private Multipart multipart = new Multipart();

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public boolean isPathStyle() {
            return pathStyle;
        }

        public void setPathStyle(boolean pathStyle) {
            this.pathStyle = pathStyle;
        }

        public Multipart getMultipart() {
            return multipart;
        }

        public void setMultipart(Multipart multipart) {
            this.multipart = multipart;
        }
    }

    public static class Multipart {
        private int partSizeMb = 8;
        private int maxRetries = 3;

        public int getPartSizeMb() {
            return partSizeMb;
        }

        public void setPartSizeMb(int partSizeMb) {
            this.partSizeMb = partSizeMb;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }

    public static class HttpProperties {
        private int maxConnections = 64;
        private int connectionTimeoutMs = 10_000;
        private int readTimeoutMs = 60_000;

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }
}
