package com.clouddisk.client.sync;

import com.clouddisk.client.util.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

/**
 * 压缩/解压服务，根据文件大小决定是否启用压缩。
 */
public class CompressionService {

    private static final int BUFFER_SIZE = 8 * 1024;
    private long thresholdBytes = 1_048_576L; // 默认 1MB

    public CompressionService() {
    }

    public CompressionService(long thresholdBytes) {
        setThresholdBytes(thresholdBytes);
    }

    public void setThresholdBytes(long thresholdBytes) {
        if (thresholdBytes < 0) {
            throw new IllegalArgumentException("thresholdBytes must be >= 0");
        }
        this.thresholdBytes = thresholdBytes;
    }

    public long getThresholdBytes() {
        return thresholdBytes;
    }

    public boolean shouldCompress(long fileSizeBytes) {
        return fileSizeBytes >= thresholdBytes;
    }

    /**
     * 压缩文件，当文件小于阈值时直接返回原始字节。
     */
    public byte[] compress(Path source) throws IOException {
        FileUtils.checkFile(source);
        Path normalized = FileUtils.normalize(source);
        long size = Files.size(normalized);
        if (!shouldCompress(size)) {
            return Files.readAllBytes(normalized);
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             InputStream inputStream = Files.newInputStream(normalized);
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos)) {
            copy(inputStream, gzipOutputStream);
            gzipOutputStream.finish();
            return baos.toByteArray();
        }
    }

    /**
     * 解压数据，若发现数据未压缩则原样写入目标文件。
     */
    public void decompress(byte[] payload, Path target) throws IOException {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("payload cannot be null or empty");
        }
        if (target == null) {
            throw new IllegalArgumentException("target cannot be null");
        }
        FileUtils.ensureParentDirectory(target);
        try (OutputStream out = Files.newOutputStream(target)) {
            if (!tryDecompress(payload, out)) {
                out.write(payload);
            }
        }
    }

    private boolean tryDecompress(byte[] payload, OutputStream outputStream) throws IOException {
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(payload))) {
            copy(gzipInputStream, outputStream);
            return true;
        } catch (ZipException ex) {
            return false;
        }
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}