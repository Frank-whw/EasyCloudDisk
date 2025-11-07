package com.clouddisk.client.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtils {
    /**
     * 检查文件是否存在、可读、可写
     * @param path 文件路径
     * @throws IllegalArgumentException 如果文件不存在、不可读、不可写，则抛出此异常
     */
    public static void checkFile(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("文件不存在: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("文件不可读: " + path);
        }
        if (!Files.isWritable(path)) {
            throw new IllegalArgumentException("文件不可写: " + path);
        }
    }

    /**
     * 处理跨平台路径：在 Windows 和 Linux 等不同操作系统上使用的路径格式。
     * @param path 待处理的路径
     * @return 处理后的路径
     */
    public static Path handleCrossPlatformPath(String path) {
        if (path == null) {
            return null;
        }
        return Paths.get(path).normalize();
    }

    /**
     * 创建临时文件
     * @param prefix 文件名前缀
     * @param suffix 文件名后缀
     * @return 创建的临时文件路径
     */
    public static Path createTempFile(String prefix, String suffix) {
        return Path.of(System.getProperty("java.io.tmpdir")).resolve(prefix + System.currentTimeMillis() + suffix);
    }
    /**
     * 删除临时文件
     * @param file 文件路径
     */
    public static void deleteFile(File file) {
        file.delete();
    }
    /**
     * 检查磁盘空间是否充足
     * @param path 磁盘路径
     * @param requiredSpace 需求空间大小
     * @return 是否充足
     */
    public static boolean checkDiskSpace(Path path, long requiredSpace) {
        return path.toFile().getUsableSpace() >= requiredSpace;
    }

}