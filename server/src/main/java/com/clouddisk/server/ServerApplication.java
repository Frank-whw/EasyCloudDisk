package com.clouddisk.server;

import com.clouddisk.server.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ServerApplication {
    private static final Logger log = LoggerFactory.getLogger(ServerApplication.class);

    public static void main(String[] args) {
        log.info("Starting CloudDisk server...");
        SpringApplication.run(ServerApplication.class, args);
        log.info("CloudDisk server started successfully.");
    }

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
