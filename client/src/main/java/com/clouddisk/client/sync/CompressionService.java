package com.clouddisk.client.sync;

import com.clouddisk.client.util.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class CompressionService {
    
    /**
     * 压缩源文件
     * @param source 源文件路径
     * @return 压缩后的字节数组
     */
    public byte[] compress(Path source) throws IOException {
        FileUtils.checkFile(source);
        Path tempPath = FileUtils.createTempFile("compressed", ".zip");
        try(FileOutputStream fos = new FileOutputStream(tempPath.toFile())) {
            ZipOutputStream zos = new ZipOutputStream(fos);
            FileInputStream fis = new FileInputStream(source.toFile());
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
     * 解压数据到目标文件
     * @param payload 压缩的数据
     * @param target 目标路径
     */
    public void decompress(byte[] payload, Path target) throws  IOException{
        // check the agruments
        if(payload == null || payload.length == 0){
            throw new IllegalArgumentException("payload cannot be null or empty");
        }
        if(target == null){
            throw new IllegalArgumentException("target cannot be null");
        }
        // check the target file
        FileUtils.checkFile(target);
        // create a temp file to store the decompressed data
        Path tempPath = FileUtils.createTempFile("decompressed", ".zip");
        try{
            Files.write(tempPath, payload);
            try(FileInputStream fis = new FileInputStream(tempPath.toFile());
                ZipInputStream zis = new ZipInputStream(fis)) {

                ZipEntry entry = zis.getNextEntry();
                if (entry != null){
                    try(FileOutputStream fos = new FileOutputStream(target.toFile())){
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                    zis.closeEntry();
                }

            }
        }finally {
            FileUtils.deleteFile(tempPath.toFile());
        }
    }
}