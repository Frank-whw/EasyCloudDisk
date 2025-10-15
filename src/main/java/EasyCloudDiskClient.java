
// 指令合集（客户端 ←→ 服务端的简明协议）：
// UPLOAD <remote> <size>：单线程上传；先 OK，再按 size 发送二进制，最后期待 UPLOAD_COMPLETE。
// UPLOAD_CHUNK <remote> <start> <length>：分块上传；OK 后发送本分块字节，期待 CHUNK_UPLOAD_COMPLETE。
// COMBINE_CHUNKS <remote>：服务端将 <name>.part* 按偏移合并为最终文件。
// DOWNLOAD <remote>：服务端返回 SIZE <size>，随后发送二进制主体；客户端据 size 读取。
// LIST：返回云盘下的文件列表（name size\n）。
// UPLOAD_MD5 <remote> <size> <md5>：带校验上传；服务端落盘后计算 MD5 比对。
// DOWNLOAD_MD5 <remote>：返回 SIZE <size> <md5> 并发送主体；客户端本地校验。
// MKDIR <remoteFolder>：在云盘根下创建（可能包含子目录）。


import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class EasyCloudDiskClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;
    private static final int BUFFER_SIZE = 8192; // 8KB
    private static final int DEFAULT_TGREAD_COUNT = 4; // 默认并发线程数
    private static final long PROGRESS_INTERVAL = 1024 * 1024; // 1MB 进度间隔

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    /**
     * 启动客户端
     * 与服务端建立连接，初始化输入/输出流
     * 说明：本客户端复用一条连接发送控制指令与数据，简单高效；异常时需调用close()
     */
    public void start() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            System.err.println("连接服务端失败: " + e.getMessage());
        }
    }


    /**
     * 单线程上传文件
     *
     * @param localFilePath  本地文件路径
     * @param remoteFilePath 云盘文件路径
     */
    public void uploadFileSingleThread(String localFilePath, String remoteFilePath) {
        if(!validateInput(localFilePath, remoteFilePath) || !isConnected())   return;
        try {
            File file = new File(localFilePath);
            long fileSize = file.length();
            // 发送上传命令
            if(!sendUploadCommand(remoteFilePath, fileSize))  return;
            // 传输文件数据
            transferFileData(file);
            // 确认上传完成
            confirmUploadComplete();
        } catch (IOException e) {
            System.err.println("单线程上传文件失败: " + e.getMessage());
        }
    }

    /**
     * 多线程上传文件
     *
     * @param localFilePath  本地文件路径
     * @param remoteFilePath 云盘文件路径
     */
    public void uploadFileMultiThread(String localFilePath, String remoteFilePath) {
        if(!validateInput(localFilePath, remoteFilePath) || !isConnected()) return;
        File file = new File(localFilePath);
        long fileSize = file.length();
        // 创建线程池并上传
        ExecutorService threadPool = Executors.newFixedThreadPool(DEFAULT_TGREAD_COUNT);
        List<Future<Boolean>> futures = createChunkUploadTasks(
                threadPool, localFilePath, remoteFilePath, fileSize, DEFAULT_TGREAD_COUNT);
        // 等待所有分块上传完成
        boolean success = waitForChunkUploads(futures);
        threadPool.shutdown();
        if (success) {
            combineChunks(new File(remoteFilePath).getName());
            System.out.println("多线程上传完成");
        }else System.out.println("多线程上传失败");
    }

    /**
     * 下载文件
     *
     * @param remoteFilePath 云盘文件路径
     * @param localFilePath  本地文件路径
     */
    public void downloadFile(String remoteFilePath, String localFilePath) {
        // TODO
    }

// ================================= 辅助方法 =================================

    /**
     * 验证输入的文件路径
     * @param localFilePath  本地文件路径
     * @param remoteFilePath  云盘文件路径
     * @return true 验证通过
     */
    private boolean validateInput(String localFilePath, String remoteFilePath){
        // String.trim().isEmpty()判断是否为空字符串
        // .trim去除字符串头尾空格
        if (localFilePath == null || localFilePath.trim().isEmpty()){
            System.err.println("本地文件路径不能为空");
            return false;
        }
        if (remoteFilePath == null || remoteFilePath.trim().isEmpty()){
            System.err.println("云盘文件路径不能为空");
            return false;
        }
        File file = new File(localFilePath);
        if(!file.exists()){
            System.err.println("本地文件不存在: " + localFilePath);
            return false;
        }
        if (!file.isFile()){
            System.err.println("制定路径不是文件： " + localFilePath);
            return false;
        }
        if (!file.canRead()) {
            System.err.println("无法读取文件: " + localFilePath);
            return false;
        }

        return true;
    }

    /**
     * 判断是否已连接服务端
     * @return true 已连接
     */
    private boolean isConnected() {
        if(!(socket != null && socket.isConnected() && !socket.isClosed())){
            System.err.println("未连接服务端");
            return false;
        }
        return true;
    }

    /**
     * 发送上传命令
     * @param remoteFilePath 云盘文件路径
     * @param fileSize 文件大小
     * @return true 发送成功
     */
    private boolean sendUploadCommand(String remoteFilePath, long fileSize) throws IOException{
        String command = "UPLOAD " + remoteFilePath + " " + fileSize;
        out.writeUTF(command); // 数据写入缓冲区
        out.flush(); // 强制发送缓冲区的数据
        String response = in.readUTF();
        if(!"OK".equals(response)){
            System.err.println("上传失败: " + response);
            return false;
        }
        return true;
     }
    /**
     * 传输文件数据
     * @param file  文件
     */
    private void transferFileData(File file) throws IOException{
        // 使用 try-with-resources 自动关闭文件流
        try (FileInputStream fis = new FileInputStream(file)) { 
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytesRead = 0;
            long fileSize = file.length();
            // fis.read(buffer) 从输入流中读取数据到缓冲区，返回实际读取的字节数
            while ((bytesRead = fis.read(buffer)) != -1) {
                // off: 偏移量  len: 读取的长度
                out.write(buffer,0,bytesRead);
                totalBytesRead += bytesRead;

                // 显示上传进度
                if (totalBytesRead % PROGRESS_INTERVAL == 0) {
                    System.out.printf("已上传: %d%% (%d/%d bytes)%n",
                            (totalBytesRead * 100) / fileSize, totalBytesRead, fileSize);
                }
            }
            out.flush();
        }
    }

    /**
     * 确认上传完成
     */
    private void confirmUploadComplete() throws IOException{
        String response = in.readUTF();
        if("UPLOAD_COMPLETE".equals(response)){
            System.out.println("上传完成");
        }else{
            System.err.println("上传失败: " + response);
        }
    }

    /**
     * 创建多线程上传任务
     * @param threadPool 线程池
     * @param localFilePath  本地文件路径
     * @param remoteFilePath  云盘文件路径
     * @param fileSize 文件大小
     * @param threadCount 线程数
     * @return List<Future<Boolean>>  任务列表
     */
    private List<Future<Boolean>> createChunkUploadTasks(ExecutorService threadPool, String localFilePath, String remoteFilePath, long fileSize, int threadCount){
        List<Future<Boolean>> futures = new ArrayList<>();
        long chunkSize = fileSize / threadCount;
        for (int i = 0; i < threadCount; i++) {
            long start = i * chunkSize;
            long end = (i == threadCount - 1) ? fileSize : start + chunkSize;
            futures.add(threadPool.submit(()-> uploadChunk(localFilePath, remoteFilePath, start, end)));
        }
        return futures;

    }

    /**
     * 上传文件分块
     * @param localFilePath  本地文件路径
     * @param remoteFilePath  云盘文件路径
     * @param start 开始位置
     * @param end 结束位置
     * @return true 上传成功
     */
    private boolean uploadChunk(String localFilePath, String remoteFilePath, long start, long end) {
        try(Socket chunkSocket = new Socket(SERVER_HOST, SERVER_PORT);
            DataOutputStream chunkOut = new DataOutputStream(chunkSocket.getOutputStream());
            DataInputStream chunkIn = new DataInputStream(chunkSocket.getInputStream());)
        {
            long chunkSize = end - start;
            // 发送分块上传命令
            String chunkCommand = "UPLOAD_CHUNK " + remoteFilePath + " " + start + " " + chunkSize;
            chunkOut.writeUTF(chunkCommand);
            chunkOut.flush();
            // 等待服务器响应
            String chunkResponse = chunkIn.readUTF();
            if (!"OK".equals(chunkResponse)) {
                System.err.println("分块上传失败：" + chunkResponse);
                return false;
            }
            // 传输文件分块数据
            return transferFileChunkData(localFilePath, chunkOut, start, chunkSize);
        }catch (IOException e){
            System.err.println("分块上传错误："+e.getMessage());
            return false;
        }
    }

    /**
     * 传输文件分块数据
     * @param localFilePath  本地文件路径
     * @param chunkOut  输出流
     * @param start 块开始位置
     * @param chunkSize 块的大小
     * @return true 上传成功
     */
    private boolean transferFileChunkData(String localFilePath, DataOutputStream chunkOut, long start, long chunkSize) throws  IOException{
        // 对于工具函数，直接抛出 异常，让调用者处理
        // 使用 RandomAccessFile 可以随机访问文件，通过seek() 方法可以设置文件指针的位置，然后使用 read() 方法可以读取指定位置的字节。
        try(RandomAccessFile raf = new RandomAccessFile(localFilePath, "r");) {
            raf.seek(start);
            byte[] buffer = new byte[BUFFER_SIZE];
            long remaining = chunkSize;
            while (remaining > 0) {
                int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if(read == -1)  break; // 说明文件已读完
                chunkOut.write(buffer, 0, read);
                remaining -= read;
            }
            chunkOut.flush();
        }
        return true;
    }

    /**
     * 等待分块上传完成
     */
    private boolean waitForChunkUploads(List<Future<Boolean>> futures){
        // 记录success，而不是上传失败直接返回false，因为存在多个线程上传分块，如果某个分块上传失败，需要继续上传其他分块
        boolean success = true;

        for(int i = 0; i < futures.size(); i++){
            try{
                if(!futures.get(i).get()){
                    System.err.println("分块 " + (i + 1) + " 上传失败");
                    success = false;
                }
            } catch (Exception e){
                System.err.println("分块 " + (i + 1) + " 上传异常：" + e.getMessage());
                success = false;
            }
        }
        return success;
    }

    /**
     * 向服务器发送合并文件分块的命令
     */
    private void combineChunks(String remoteFilePath){
        try{
            out.writeUTF("COMBINE_CHUNKS " + remoteFilePath);
            if(!in.readUTF().equals("COMBINE_COMPLETE")){
                System.err.println("合并文件分块失败");
            }
        }catch (IOException e){
            System.err.println("合并文件分块错误：" + e.getMessage());
        }
    }

}
