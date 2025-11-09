package com.clouddisk;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.logging.Logger;

/**
 * 服务器主应用 入口
 */
@SpringBootApplication(scanBasePackages = {"com.clouddisk"})
public class CloudDiskApplication {
    private static final Logger logger = Logger.getLogger(CloudDiskApplication.class.getName()); // 获取日志记录器
    public static void main(String[] args) {
        logger.info("启动服务...");
        SpringApplication.run(CloudDiskApplication.class, args);
        logger.info("服务启动成功...");
    }
}