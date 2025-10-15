import java.io.*;
import java.net.Socket;

// 指令合集（客户端 ←→ 服务端的简明协议）：
// UPLOAD <remote> <size>：单线程上传；先 OK，再按 size 发送二进制，最后期待 UPLOAD_COMPLETE。
// UPLOAD_CHUNK <remote> <start> <length>：分块上传；OK 后发送本分块字节，期待 CHUNK_UPLOAD_COMPLETE。
// COMBINE_CHUNKS <remote>：服务端将 <name>.part* 按偏移合并为最终文件。
// DOWNLOAD <remote>：服务端返回 SIZE <size>，随后发送二进制主体；客户端据 size 读取。
// LIST：返回云盘下的文件列表（name size\n）。
// UPLOAD_MD5 <remote> <size> <md5>：带校验上传；服务端落盘后计算 MD5 比对。
// DOWNLOAD_MD5 <remote>：返回 SIZE <size> <md5> 并发送主体；客户端本地校验。
// MKDIR <remoteFolder>：在云盘根下创建（可能包含子目录）。

/**
 * 客户端处理类
 * 处理客户端请求的线程类
 * 协议基于 DataInputStream 和 DataOutputStream 的文本指令 + 二级制数据流
 *  1. 文本行用于描述指令与元信息（如大小、偏移、md5）
 *  2. 二进制数据用于描述文件内容，按声明的 size 读取
 * 所有文件最终落在 CLOUD_DIR 下，普通上传按“仅文件名”存储；MD5 与 MKDIR 路径允许包含子目录，但需经过路径规范化以防穿越。
 * 客户端发送：
 * ┌─────────────────────────────────┬──────────────────────┐
 * │ 文本行: "UPLOAD test.txt 1024"   │ 二进制数据: 1024字节    │
 * └─────────────────────────────────┴──────────────────────┘
 * 服务器接收：
 * 1. 读取文本行 → 解析出文件名和大小
 * 2. 读取1024字节二进制数据 → 写入文件
 *
 */
public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private static String CLOUD_DIR ;
    private static  int BUFFER_SIZE;
    private static int SOCKET_TIMEOUT;

    private final CommandDispatcher commandDispatcher;

    public ClientHandler(Socket socket, String cloudDir, int bufferSize, int socketTimeout) {
        this.clientSocket = socket;
        this.CLOUD_DIR = cloudDir;
        this.BUFFER_SIZE = bufferSize;
        this.SOCKET_TIMEOUT = socketTimeout;
        this.commandDispatcher = new CommandDispatcher();
        configureSocket();
    }

    /**
     * 配置Socket
     */
    private void configureSocket() {
        try {
            clientSocket.setSoTimeout(SOCKET_TIMEOUT);
        } catch (IOException e){
            System.err.println("Socket配置失败: " + e.getMessage());
        }
    }
    @Override
    public void run() {
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
           DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream()))
        {
            System.out.println("客户端已连接：" + clientSocket.getRemoteSocketAddress());
            // 主循环
            while(isConnectionActive()){
                try{
                    String command = in.readUTF();
                    commandDispatcher.dispatch(command, in, out);
                    // 检查是否是退出命令
                    if (command.equals("EXIT")){
                        System.out.println("客户端主动断开连接");
                        break;
                    }
                }catch (EOFException e){
                    System.out.println("客户端已断开连接");
                    break;
                }catch (IOException e){
                    if(clientSocket.isClosed()){
                        System.out.println("客户端已主动断开连接");
                        break;
                    }
                    System.err.println("读取指令失败： " + e.getMessage());
                    sendErrorResponse(out, "Inernal server error");
                }
            }
        } catch (IOException e){
            System.err.println("建立连接流失败： " + e.getMessage());
        }finally {
            closeConnection();
        }
    }

    /**
     * 检查连接是否活跃
     */
    private boolean isConnectionActive() {
        return !clientSocket.isClosed() && clientSocket.isConnected();
    }
    /**
     * 关闭连接
     */
    private void closeConnection() {
        try {
            if (!clientSocket.isClosed()){
                clientSocket.close();
                System.out.println("已关闭连接：" + clientSocket.getRemoteSocketAddress());
            }
        }catch (IOException e){
            System.err.println("关闭连接失败： " + e.getMessage());
        }
    }

    /**
     * 发送错误响应
     * @param out
     * @param message
     */
    private void sendErrorResponse(DataOutputStream out, String message) {
        try{
            out.writeUTF("ERROR " + message);
            out.flush();
        }catch (IOException e) {
            System.err.println("发送错误响应失败： " + e.getMessage());
        }
    }
    /**
     * 命令分发器
     */
    private class CommandDispatcher {
        public void dispatch(String command, DataInputStream in, DataOutputStream out) throws  IOException {
            try{
                if (command.startsWith("UPLOAD ")){ // 注意要有空格，否则UPLOAD_CHUNK 也会被识别为UPLOAD
                    handleUpload(command, in, out);
                } else if (command.startsWith("UPLOAD_CHUNK ")) {
                    handleChunkUpload(command, in, out);
                } else if (command.startsWith("COMBINE_CHUNKS ")){
                    handleCombineChunks(command, in, out);
                }
            } catch (Exception e){
                System.err.println("命令处理失败： " + e.getMessage());
                out.writeUTF("ERROR " + e.getMessage());
                out.flush();
            }
        }
    }

    // ==================== 命令处理方法 ====================

    /**
     * 处理文件上传请求
     * @param command “UPLOAD test.txt 1024”
     * @param in
     * @param out
     * @throws IOException
     *
     * 仅保留文件名，避免客户端传入路径改变存储结构（统一存储结构）
     * 预写入.tmp，全部接收成功后原子性重命名为正式文件
     * 必须严格读取 fileSize 字节，短读视为失败并删除临时文件（后面如果要加断电续传，这边需要记录已接收的字节数）
     */
    private void handleUpload(String command, DataInputStream in, DataOutputStream out) throws IOException {
        // 解析命令参数
        String[] args = command.split(" ");
        if(args.length != 3){
            out.writeUTF("UPLOAD 参数错误：" + command);
            return;
        }
        String filename = args[1];
        long fileSize = Long.parseLong(args[2]);

        // 提取文件名（去除路径）
        String justFileName = new File(filename).getName();
        // 创建临时文件和最终文件路径
        File tmpFile = new File(CLOUD_DIR, justFileName + ".tmp");
        File finalFile = new File(CLOUD_DIR, justFileName);
        // 确保父目录存在
        ensureParentDirExists(tmpFile);
        // 发送确认
        out.writeUTF("OK");
        out.flush();
        try{
            // 接收文件数据
            receiverFileData(in, tmpFile, fileSize);
            // 原子性重命名
            if(finalizeFile(tmpFile, finalFile)){
                out.writeUTF("UPLOAD_COMPLETE");
                System.out.println("文件上传成功：" + finalFile.getAbsolutePath());
            }else{
                out.writeUTF("UPLOAD_FAILED");
                cleanupTempFile(tmpFile);
            }
        } catch (IOException e){
            cleanupTempFile(tmpFile);
            System.err.println("文件上传失败: " + e.getMessage());
        }
    }
    /**
     * 处理文件分片上传请求
     * @param command
     * @param in
     * @param out
     * @throws IOException
     * 每个分块以 <name>.part<start>命名，避免冲突
     * 严格按 Size 字节接收，短读视为失败并删除临时文件
     */
    private void handleChunkUpload(String command, DataInputStream in, DataOutputStream out) throws IOException {
        String[] args = command.split(" ");
        if(args.length != 4){
            out.writeUTF("CHUNK UPLOAD 参数错误：" +  command);
            return;
        }
        String filename = args[1];
        long start = Long.parseLong(args[2]);
        long size = Long.parseLong(args[3]);
        // 通过含路径的文件名获取文件对象
        File fileObj = new File(filename);
        // 获取文件的文件名，以防止路径穿越攻击
        String justFileName = fileObj.getName();

        String filePath = CLOUD_DIR + justFileName + ".part" + start;
        File chunkFile = new File(filePath);
        ensureParentDirExists(chunkFile);
        out.writeUTF("OK");
        try(FileOutputStream fos = new FileOutputStream(chunkFile)){
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long remaining = size;
            while (remaining > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                fos.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
            // 若未读取到指定大小，则认为失败并删除临时文件
            if (remaining > 0){
                fos.flush();
                chunkFile.delete();
                out.writeUTF("ERROR CHUNK_UPLOAD_INCOMPLETE");
                return;
            }
        }
        out.writeUTF("CHUNK_UPLOAD_COMPLETE");
    }

    /**
     * 处理文件分片合并请求
     * @param command
     * @param in
     * @param out
     * @throws IOException
     */
    private void handleCombineChunks(String command, DataInputStream in, DataOutputStream out) throws IOException {
        try{
            String[] args = command.split(" ");
            if(args.length != 2){
                out.writeUTF("COMBINE CHUNK 参数错误：" +  command);
            }
            String filename = args[1];
            File fileObj = new File(filename);
            String justFileName = fileObj.getName();
            File dir = new File(CLOUD_DIR);
            final String partPrefix = justFileName + ".part";
            File[] chunkFiles = dir.listFiles((a, name) -> name.startsWith(partPrefix));
            if(chunkFiles == null || chunkFiles.length == 0){
                out.writeUTF("没有找到分块文件");
                return;
            }
            // 为了保证合并顺序正确，按分块起始偏移升序排序
            java.util.Arrays.sort(chunkFiles, (a, b) -> {
                long aOffset = parseChunkStart(a.getName(), partPrefix);
                long bOffset = parseChunkStart(b.getName(), partPrefix);
                return Long.compare(aOffset, bOffset);
            });
            File finalFile = new File(CLOUD_DIR, justFileName);
            if(finalFile.exists())      finalFile.delete();
            try(FileOutputStream fos = new FileOutputStream(finalFile)){
                for(File chunkFile : chunkFiles){
                    try(FileInputStream fis = new FileInputStream(chunkFile)){
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int read;
                        while ((read = fis.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    }
                    chunkFile.delete();
                }
            }
            out.writeUTF("COMBINE_COMPLETE");
        }catch (Exception e){
            out.writeUTF("合并失败: " + e.getMessage());
        }
    }
    // ================= 辅助方法 =================

    /**
     * 确保父目录存在
     * @param file
     */
    private void ensureParentDirExists(File file) {
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
    }

    /**
     * 接收文件数据
     * @param in
     * @param tmpFile
     * @param fileSize
     */
    private void receiverFileData(DataInputStream in, File tmpFile, long fileSize) throws  IOException {
        try(FileOutputStream fos = new FileOutputStream(tmpFile)){
            byte[] buffer = new byte[BUFFER_SIZE];
            long remaining = fileSize;
            while (remaining > 0) {
                int bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (bytesRead == -1) {
                    throw new IOException("文件传输中断");
                }
                fos.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
        }
    }
    /**
     * 完成文件（原子性重命名）
     */
    private boolean finalizeFile(File tmpFile, File finalFile) {
        // 删除已存在的最终文件
        if (finalFile.exists()) {finalFile.delete();}
        return tmpFile.renameTo(finalFile);
    }
    /**
     * 清理临时文件
     */
    private void cleanupTempFile(File tmpFile) {
        if(tmpFile.exists()){
            tmpFile.delete();
        }
    }

    /**
     * 解析分块文件名中的起始偏移
     * @param chunkFileName
     * @param partPrefix
     * @return
     * 若解析失败，返回 Long.MAX_VALUE 以便排序时把异常块放到最后
     */
    private long parseChunkStart(String chunkFileName, String partPrefix) {
        try{
            int index = chunkFileName.indexOf(partPrefix);
            if(index == -1)     return Long.MAX_VALUE;
            String num = chunkFileName.substring(index + partPrefix.length());
            return Long.parseLong(num);
        } catch (Exception e){
            return Long.MAX_VALUE;
        }
    }


}
