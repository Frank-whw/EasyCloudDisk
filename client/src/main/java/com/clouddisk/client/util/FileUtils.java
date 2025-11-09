package com.clouddisk.client.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 文件工具类，封装常用的路径与磁盘操作，兼顾跨平台兼容性。
 */
public final class FileUtils {

    private FileUtils() {
    }

    /**
     * 规范化路径并返回绝对路径。
     */
    public static Path normalize(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("path cannot be null");
        }
        return path.toAbsolutePath().normalize();
    }

    public static Path normalize(String path) {
        if (path == null) {
            return null;
        }
        return normalize(Paths.get(path));
    }

    /**
     * 处理跨平台路径字符串（兼容 Windows/Linux）。
     */
    public static Path handleCrossPlatformPath(String path) {
        return normalize(path);
    }

    /**
     * 确保目标目录存在。
     */
    public static void ensureDirectory(Path directory) throws IOException {
        Path normalized = normalize(directory);
        if (Files.exists(normalized)) {
            if (!Files.isDirectory(normalized)) {
                throw new IOException("路径已存在但不是目录: " + normalized);
            }
            return;
        }
        Files.createDirectories(normalized);
    }

    /**
     * 确保目标文件的父目录存在。
     */
    public static void ensureParentDirectory(Path file) throws IOException {
        Path parent = normalize(file).getParent();
        if (parent != null) {
            ensureDirectory(parent);
        }
    }

    /**
     * 检查文件是否存在并具备读/写权限。
     */
    public static void checkFile(Path path) {
        Path normalized = normalize(path);
        if (!Files.exists(normalized)) {
            throw new IllegalArgumentException("文件不存在: " + normalized);
        }
        if (!Files.isReadable(normalized)) {
            throw new IllegalArgumentException("文件不可读: " + normalized);
        }
        if (!Files.isWritable(normalized)) {
            throw new IllegalArgumentException("文件不可写: " + normalized);
        }
    }

    /**
     * 创建临时文件。
     */
    public static Path createTempFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(prefix, suffix);
    }

    /**
     * 删除文件（忽略不存在的情况）。
     */
    public static void deleteIfExists(Path path) throws IOException {
        if (path != null) {
            Files.deleteIfExists(path);
        }
    }

    /**
     * 删除临时文件（保留兼容 API）。
     */
    public static void deleteFile(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /**
     * 递归删除目录或文件。
     */
    public static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 检查磁盘是否有充足空间。
     */
    public static boolean checkDiskSpace(Path path, long requiredSpace) throws IOException {
        Path normalized = normalize(path);
        long usableSpace = Files.getFileStore(normalized).getUsableSpace();
        return usableSpace >= requiredSpace;
    }
}