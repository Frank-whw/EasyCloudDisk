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
}
