package com.clouddisk.repository;

import com.clouddisk.entity.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, String> {
    
    List<UploadSession> findAllByUserId(String userId);
    
    Optional<UploadSession> findBySessionIdAndUserId(String sessionId, String userId);
    
    List<UploadSession> findAllByExpiresAtBefore(LocalDateTime dateTime);
    
    void deleteAllByExpiresAtBefore(LocalDateTime dateTime);
}
