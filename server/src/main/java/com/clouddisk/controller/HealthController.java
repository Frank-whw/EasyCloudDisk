package com.clouddisk.controller;

import com.clouddisk.storage.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查接口，汇总数据库和存储服务的状态。
 */
@RestController
public class HealthController {

    private final DataSource dataSource;
    private final StorageService storageService;

    public HealthController(DataSource dataSource, StorageService storageService) {
        this.dataSource = dataSource;
        this.storageService = storageService;
    }

    /**
     * 返回系统各项依赖的健康状态。
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("database", checkDatabase());
        response.put("storage", storageService.isHealthy());
        return ResponseEntity.ok(response);
    }

    /**
     * 验证数据库连接是否可用。
     */
    private boolean checkDatabase() {
        try {
            dataSource.getConnection().close();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
