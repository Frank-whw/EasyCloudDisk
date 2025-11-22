package com.clouddisk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 使用 SSE 推送文件变更通知的服务。
 */
@Service
public class FileSyncService {

    private static final Logger log = LoggerFactory.getLogger(FileSyncService.class);

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 注册 SSE 连接并发送初始消息。
     */
    public SseEmitter register(String userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(userId, emitter);
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        try {
            emitter.send(SseEmitter.event().name("connected").data("connected at " + Instant.now()));
        } catch (IOException ex) {
            log.warn("Failed to send initial SSE event", ex);
        }
        return emitter;
    }

    /**
     * 向指定用户推送文件变更事件。
     */
    public void notifyChange(String userId, Object payload) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("file-change").data(payload));
            } catch (IOException ex) {
                log.warn("Failed to notify change for user {}", userId, ex);
                emitters.remove(userId);
            }
        }
    }
    
    /**
     * 向所有协作者推送冲突通知
     */
    public void notifyConflict(String fileId, String conflictUserId, Map<String, Object> conflictDetails) {
        Map<String, Object> notification = Map.of(
            "type", "conflict_detected",
            "fileId", fileId,
            "conflictUserId", conflictUserId,
            "timestamp", Instant.now(),
            "details", conflictDetails
        );
        
        // 向所有在线用户推送冲突通知
        emitters.forEach((userId, emitter) -> {
            if (!userId.equals(conflictUserId)) { // 不通知冲突发起者
                try {
                    emitter.send(SseEmitter.event().name("conflict").data(notification));
                } catch (IOException ex) {
                    log.warn("Failed to notify conflict to user {}", userId, ex);
                    emitters.remove(userId);
                }
            }
        });
        
        log.info("冲突通知已发送: fileId={}, conflictUserId={}", fileId, conflictUserId);
    }
    
    /**
     * 向所有协作者推送版本恢复通知
     */
    public void notifyVersionRestore(String fileId, String userId, Integer fromVersion, Integer toVersion) {
        Map<String, Object> notification = Map.of(
            "type", "version_restored",
            "fileId", fileId,
            "userId", userId,
            "fromVersion", fromVersion,
            "toVersion", toVersion,
            "timestamp", Instant.now()
        );
        
        // 向所有在线用户推送版本恢复通知
        emitters.forEach((uid, emitter) -> {
            if (!uid.equals(userId)) { // 不通知操作者自己
                try {
                    emitter.send(SseEmitter.event().name("version-restored").data(notification));
                } catch (IOException ex) {
                    log.warn("Failed to notify version restore to user {}", uid, ex);
                    emitters.remove(uid);
                }
            }
        });
        
        log.info("版本恢复通知已发送: fileId={}, userId={}, fromVersion={}, toVersion={}", 
                fileId, userId, fromVersion, toVersion);
    }
}
