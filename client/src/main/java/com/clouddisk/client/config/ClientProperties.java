package com.clouddisk.client.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "client")
public class ClientProperties {
    private static final String SERVER_URL = "http://ec2-54-95-61-230.ap-northeast-1.compute.amazonaws.com:8080";
    // DIR使用相对路径
    private static final String SYNC_DIR = "./local"; // 同步目录
    private static final String COMPRESS_STRATEGY = "zip"; // 可选：zip、tar
    private static final Boolean ENABLE_AUTO_SYNC = true;

    private String email;
    private String password;

}
