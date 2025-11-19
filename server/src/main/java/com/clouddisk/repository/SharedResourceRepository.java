package com.clouddisk.repository;

import com.clouddisk.entity.SharedResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 共享资源仓储。
 */
@Repository
public interface SharedResourceRepository extends JpaRepository<SharedResource, String> {

    List<SharedResource> findAllByOwnerId(String ownerId);

    List<SharedResource> findAllByTargetUserId(String targetUserId);

    List<SharedResource> findAllByFileId(String fileId);

    Optional<SharedResource> findByShareIdAndOwnerId(String shareId, String ownerId);

    Optional<SharedResource> findByFileIdAndTargetUserId(String fileId, String targetUserId);
}

