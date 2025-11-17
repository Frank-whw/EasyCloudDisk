package com.clouddisk.client.sync;

import com.clouddisk.client.util.FileUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 负责对本地文件进行压缩与解压缩，供同步流程上传或还原文件使用。
 */
public class CompressionService {
    
    /**
     * 将指定文件压缩为 ZIP 格式的二进制数组。
     */
    public byte[] compress(Path source) throws IOException {
        FileUtils.checkFile(source);
        Path tempPath = FileUtils.createTempFile("compressed", ".zip");
        try (FileOutputStream fos = new FileOutputStream(tempPath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos);
             FileInputStream fis = new FileInputStream(source.toFile())) {
            
            zos.putNextEntry(new ZipEntry(source.getFileName().toString()));
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
            zos.finish();
        }
        // 读取压缩后的数据
        byte[] compressedData = Files.readAllBytes(tempPath);
        FileUtils.deleteFile(tempPath.toFile()); // 使用正确的删除方法
        return compressedData;
    }
    
    /**
     * 将压缩后的字节数组解压到目标路径。
     */
    public void decompress(byte[] payload, Path target) throws IOException {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("payload cannot be null or empty");
        }
        if (target == null) {
            throw new IllegalArgumentException("target cannot be null");
        }

        // 确保目标目录存在
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempPath = FileUtils.createTempFile("decompressed", ".zip");
        try {
            Files.write(tempPath, payload);
            try (FileInputStream fis = new FileInputStream(tempPath.toFile());
                 ZipInputStream zis = new ZipInputStream(fis)) {

                ZipEntry entry = zis.getNextEntry();
                if (entry != null) {
                    try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                    zis.closeEntry();
                }

            }
        } finally {
            FileUtils.deleteFile(tempPath.toFile());
        }
    }
}