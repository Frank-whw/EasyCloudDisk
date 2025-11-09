package com.clouddisk.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;

@Data
@Component
@ConfigurationProperties(prefix = "client")
public class ClientProperties {
    private String serverUrl = "http://ec2-54-95-61-230.ap-northeast-1.compute.amazonaws.com:8080";
    private String syncDir = "./local"; // 同步目录
    private String compressStrategy = "zip"; // 可选：zip、tar
    private Boolean enableAutoSync = true;
    private String email;
    private String password;

    @PostConstruct
    public void validate() {
        // 验证配置参数
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("服务器URL不能为空");
        }
        
        if (syncDir == null || syncDir.trim().isEmpty()) {
            throw new IllegalArgumentException("同步目录不能为空");
        }
        
        if (compressStrategy == null || 
            (!compressStrategy.equals("zip") && !compressStrategy.equals("tar"))) {
            throw new IllegalArgumentException("压缩策略必须是 'zip' 或 'tar'");
        }
        
        if (enableAutoSync == null) {
            enableAutoSync = true;
        }
        
        // 确保同步目录存在
        File dir = new File(syncDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                throw new IllegalArgumentException("无法创建同步目录: " + syncDir);
            }
        }
        
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("同步目录不是一个有效的目录: " + syncDir);
        }
    }

    public boolean isEnableAutoSync() {
        return enableAutoSync;
    }
}
