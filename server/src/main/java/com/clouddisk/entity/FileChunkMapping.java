package com.clouddisk.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 文件与块的映射关系。
 * 一个文件由多个块按顺序组成。
 */
@Entity
@Table(name = "file_chunk_mappings", indexes = {
        @Index(name = "idx_file_id_seq", columnList = "fileId,sequenceNumber", unique = true),
        @Index(name = "idx_chunk_id", columnList = "chunkId")
})
@Data
public class FileChunkMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long mappingId;

    /**
     * 文件ID。
     */
    @Column(nullable = false, length = 36)
    private String fileId;

    /**
     * 文件版本号。
     */
    @Column(nullable = false)
    private Integer versionNumber;

    /**
     * 块ID。
     */
    @Column(nullable = false)
    private Long chunkId;

    /**
     * 块在文件中的顺序(从0开始)。
     */
    @Column(nullable = false)
    private Integer sequenceNumber;

    /**
     * 块在文件中的起始偏移量。
     */
    @Column(nullable = false)
    private Long offsetInFile;
}
