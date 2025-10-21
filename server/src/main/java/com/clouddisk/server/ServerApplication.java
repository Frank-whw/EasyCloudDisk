package com.clouddisk.server;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.logging.Logger;

/**
 * 服务器主应用 入口
 */
@SpringBootApplication
public class ServerApplication {
    private static final Logger logger = Logger.getLogger(ServerApplication.class.getName()); // 获取日志记录器
    public static void main(String[] args) {
        logger.info("启动服务...");
        SpringApplication.run(ServerApplication.class, args);
        logger.info("服务启动成功...");
    }
}