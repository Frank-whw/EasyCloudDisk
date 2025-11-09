package com.clouddisk.client.util;

import org.slf4j.Logger;

/**
 * 简单的日志工厂，统一添加项目前缀。
 */
public final class LoggerFactory {

    private static final String PREFIX = "[CloudDisk] ";

    private LoggerFactory() {
    }

    public static Logger getLogger(Class<?> type) {
        return org.slf4j.LoggerFactory.getLogger(type);
    }

    public static Logger getLogger(String name) {
        return org.slf4j.LoggerFactory.getLogger(name);
    }

    public static String format(String message) {
        return PREFIX + message;
    }

    public static String format(String template, Object... args) {
        return PREFIX + template.formatted(args);
    }
}