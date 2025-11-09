package com.clouddisk.client.util;

import org.slf4j.Logger;

import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * 简易重试模板，支持指数退避与抖动。
 */
public class RetryTemplate {

    private static final Logger log = LoggerFactory.getLogger(RetryTemplate.class);
    private static final Random RANDOM = new Random();

    private int maxAttempts = 3;
    private Duration initialInterval = Duration.ofSeconds(1);
    private double multiplier = 2.0;
    private Duration maxInterval = Duration.ofSeconds(30);
    private double jitterFactor = 0.2;
    private Predicate<Throwable> retryPredicate = throwable -> true;

    public RetryTemplate() {
    }

    public RetryTemplate maxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        return this;
    }

    public RetryTemplate initialInterval(Duration initialInterval) {
        this.initialInterval = Objects.requireNonNull(initialInterval);
        return this;
    }

    public RetryTemplate multiplier(double multiplier) {
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
        this.multiplier = multiplier;
        return this;
    }

    public RetryTemplate maxInterval(Duration maxInterval) {
        this.maxInterval = Objects.requireNonNull(maxInterval);
        return this;
    }

    public RetryTemplate jitter(double jitterFactor) {
        if (jitterFactor < 0) {
            throw new IllegalArgumentException("jitterFactor must be >= 0");
        }
        this.jitterFactor = jitterFactor;
        return this;
    }

    public RetryTemplate retryOn(Predicate<Throwable> predicate) {
        this.retryPredicate = Objects.requireNonNull(predicate);
        return this;
    }

    /**
     * 执行带重试的操作。
     */
    public <T> T execute(Callable<T> callable) throws Exception {
        Objects.requireNonNull(callable, "callable");
        int attempt = 1;
        Duration interval = initialInterval;
        Throwable lastError = null;

        while (attempt <= maxAttempts) {
            try {
                return callable.call();
            } catch (Throwable throwable) {
                lastError = throwable;
                if (attempt >= maxAttempts || !retryPredicate.test(throwable)) {
                    break;
                }
                long sleepMillis = applyJitter(interval);
                log.warn("[CloudDisk] 重试第 {} 次失败，将在 {} ms 后重试: {}",
                    attempt, sleepMillis, throwable.getMessage());
                Thread.sleep(sleepMillis);
                interval = nextInterval(interval);
                attempt++;
            }
        }
        if (lastError instanceof Exception exception) {
            throw exception;
        }
        if (lastError instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("未知错误");
    }

    public void execute(Runnable runnable) throws Exception {
        execute(() -> {
            runnable.run();
            return null;
        });
    }

    private Duration nextInterval(Duration current) {
        double next = current.toMillis() * multiplier;
        if (next > maxInterval.toMillis()) {
            next = maxInterval.toMillis();
        }
        return Duration.ofMillis((long) next);
    }

    private long applyJitter(Duration interval) {
        long base = interval.toMillis();
        if (jitterFactor <= 0) {
            return base;
        }
        double jitterRange = base * jitterFactor;
        double jitter = (RANDOM.nextDouble() * 2 - 1) * jitterRange;
        long result = (long) (base + jitter);
        return Math.max(result, 0);
    }
}