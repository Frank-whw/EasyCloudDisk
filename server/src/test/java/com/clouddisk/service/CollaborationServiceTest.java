package com.clouddisk.service;

import com.clouddisk.dto.FileMetadataDto;
import com.clouddisk.entity.FileEntity;
import com.clouddisk.entity.SharePermission;
import com.clouddisk.entity.ShareResourceType;
import com.clouddisk.entity.SharedResource;
import com.clouddisk.entity.User;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.repository.FileRepository;
import com.clouddisk.repository.SharedResourceRepository;
import com.clouddisk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationServiceTest {

    @Mock
    private SharedResourceRepository sharedResourceRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CollaborationService collaborationService;

    @Test
    void requireFileAccessShouldAllowDirectoryShareSubtree() {
        when(fileRepository.findByFileIdAndUserId("file-1", "guest"))
                .thenReturn(Optional.empty());

        FileEntity target = buildFile("file-1", "owner-1", "/projects", "spec.docx", false);
        when(fileRepository.findById("file-1")).thenReturn(Optional.of(target));

        SharedResource share = new SharedResource();
        share.setShareId("share-1");
        share.setFileId("dir-1");
        share.setOwnerId("owner-1");
        share.setTargetUserId("guest");
        share.setResourceType(ShareResourceType.DIRECTORY);
        share.setPermission(SharePermission.READ);
        share.setIncludeSubtree(true);
        share.setResourcePath("/projects/");
        share.setCreatedAt(Instant.now());

        when(sharedResourceRepository.findAllByTargetUserId("guest"))
                .thenReturn(List.of(share));

        FileEntity accessible = collaborationService.requireFileAccess("file-1", "guest", SharePermission.READ);
        assertEquals(target, accessible);

        assertThrows(BusinessException.class, () ->
                collaborationService.requireFileAccess("file-1", "guest", SharePermission.WRITE));
    }

    @Test
    void listSharedWithMeShouldFilterExpiredSharesAndEnrichMetadata() {
        SharedResource active = new SharedResource();
        active.setShareId("share-active");
        active.setFileId("file-1");
        active.setOwnerId("owner-1");
        active.setTargetUserId("guest");
        active.setPermission(SharePermission.WRITE);
        active.setResourceType(ShareResourceType.FILE);
        active.setIncludeSubtree(false);
        active.setCreatedAt(Instant.now());

        SharedResource expired = new SharedResource();
        expired.setShareId("share-expired");
        expired.setFileId("file-2");
        expired.setOwnerId("owner-2");
        expired.setTargetUserId("guest");
        expired.setPermission(SharePermission.READ);
        expired.setResourceType(ShareResourceType.FILE);
        expired.setIncludeSubtree(false);
        expired.setCreatedAt(Instant.now().minusSeconds(3600));
        expired.setExpiresAt(Instant.now().minusSeconds(60));

        when(sharedResourceRepository.findAllByTargetUserId("guest"))
                .thenReturn(List.of(active, expired));

        FileEntity activeFile = buildFile("file-1", "owner-1", "/", "plan.txt", false);
        when(fileRepository.findById("file-1")).thenReturn(Optional.of(activeFile));

        User owner1 = new User();
        owner1.setUserId("owner-1");
        owner1.setEmail("owner1@example.com");
        owner1.setPasswordHash("hash");
        owner1.setTokenVersion(1);
        owner1.setCreatedAt(Instant.now());
        owner1.setUpdatedAt(Instant.now());

        User owner2 = new User();
        owner2.setUserId("owner-2");
        owner2.setEmail("owner2@example.com");
        owner2.setPasswordHash("hash");
        owner2.setTokenVersion(1);
        owner2.setCreatedAt(Instant.now());
        owner2.setUpdatedAt(Instant.now());

        when(userRepository.findAll()).thenReturn(List.of(owner1, owner2));

        List<FileMetadataDto> results = collaborationService.listSharedWithMe("guest");
        assertEquals(1, results.size());

        FileMetadataDto dto = results.get(0);
        assertTrue(dto.isShared());
        assertEquals("share-active", dto.getShareId());
        assertEquals(SharePermission.WRITE.name(), dto.getPermission());
        assertEquals("owner1@example.com", dto.getOwnerEmail());
        assertEquals("plan.txt", dto.getName());
    }

    private FileEntity buildFile(String fileId, String ownerId, String path, String name, boolean directory) {
        FileEntity file = new FileEntity();
        file.setFileId(fileId);
        file.setUserId(ownerId);
        file.setDirectoryPath(path);
        file.setName(name);
        file.setDirectory(directory);
        file.setFileSize(1024L);
        file.setVersion(3);
        file.setUpdatedAt(Instant.now());
        return file;
    }
}

