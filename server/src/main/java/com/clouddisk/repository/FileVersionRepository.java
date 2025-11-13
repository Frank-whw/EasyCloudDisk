package com.clouddisk.repository;

import com.clouddisk.entity.FileEntity;
import com.clouddisk.entity.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 文件版本实体的 JPA 仓储接口。
 */
public interface FileVersionRepository extends JpaRepository<FileVersion, String> {
    List<FileVersion> findAllByFileOrderByVersionNumberDesc(FileEntity file);
}
