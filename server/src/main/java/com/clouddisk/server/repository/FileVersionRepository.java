package com.clouddisk.server.repository;

import com.clouddisk.server.entity.FileEntity;
import com.clouddisk.server.entity.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileVersionRepository extends JpaRepository<FileVersion, String> {
    List<FileVersion> findAllByFileOrderByVersionNumberDesc(FileEntity file);
}
