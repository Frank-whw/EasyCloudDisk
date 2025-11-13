package com.clouddisk;

import com.clouddisk.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import org.springframework.cache.annotation.EnableCaching;

/**
 * 服务端应用入口，负责启动 Spring Boot 并执行必要的初始化逻辑。
 */
@SpringBootApplication
@EnableCaching
public class ServerApplication {
    private static final Logger log = LoggerFactory.getLogger(ServerApplication.class);

    public static void main(String[] args) {
        log.info("Starting CloudDisk server...");
        SpringApplication.run(ServerApplication.class, args);
        log.info("CloudDisk server started successfully.");
    }

    /**
     * 启动后验证底层存储桶是否存在的初始化任务。
     */
    @Bean
    CommandLineRunner storageInitializer(StorageService storageService) {
        return args -> {
            try {
                storageService.ensureBucket();
                log.info("Storage bucket verified successfully.");
            } catch (Exception ex) {
                log.error("Failed to verify storage bucket", ex);
                throw ex;
            }
        };
    }
}
