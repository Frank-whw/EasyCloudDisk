package com.clouddisk.controller;

import com.clouddisk.dto.ApiResponse;
import com.clouddisk.storage.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final DataSource dataSource;
    private final StorageService storageService;

    public HealthController(DataSource dataSource, StorageService storageService) {
        this.dataSource = dataSource;
        this.storageService = storageService;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("database", checkDatabase());
        status.put("storage", storageService.isHealthy());
        status.put("status", "UP");
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    private boolean checkDatabase() {
        try {
            dataSource.getConnection().close();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
