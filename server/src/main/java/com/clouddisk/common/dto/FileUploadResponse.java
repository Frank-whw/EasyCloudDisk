package com.clouddisk.common.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    
    private UUID fileId;
    private String name;
    private Long fileSize;
    private String filePath;
}