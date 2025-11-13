package com.clouddisk.client.util;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 通用的重试模板，封装指数退避的重试逻辑以提升网络调用的鲁棒性。
 */
@Slf4j
public class RetryTemplate {
    
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_INITIAL_DELAY_MS = 1000L;
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    
    /**
     * 执行带重试的操作
     */
    public static <T> T executeWithRetry(Supplier<T> operation, int maxRetries) {
        return executeWithRetry(operation, maxRetries, DEFAULT_INITIAL_DELAY_MS, DEFAULT_BACKOFF_MULTIPLIER);
    }
    
    /**
     * 执行带重试和指数退避的操作
     */
    public static <T> T executeWithRetry(Supplier<T> operation, int maxRetries, 
                                       long initialDelayMs, double backoffMultiplier) {
        Exception lastException = null;
        long delayMs = initialDelayMs;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("重试操作，第 {} 次尝试", attempt);
                }
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                
                if (attempt >= maxRetries) {
                    log.error("操作失败，已达到最大重试次数: {}", maxRetries);
                    break;
                }
                
                log.warn("操作失败，{} 毫秒后重试: {}", delayMs, e.getMessage());
                
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", ie);
                }
                
                // 指数退避
                delayMs = (long) (delayMs * backoffMultiplier);
            }
        }
        
        throw new RuntimeException("操作失败，已重试 " + maxRetries + " 次", lastException);
    }
    
    /**
     * 执行带重试的操作（无返回值）
     */
    public static void executeWithRetry(Runnable operation, int maxRetries) {
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, maxRetries);
    }
}