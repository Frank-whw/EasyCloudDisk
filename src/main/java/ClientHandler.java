import java.io.IOException;
import java.net.Socket;

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
    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        try {
            socket.setSoTimeout(30000);
            System.out.println("新连接：" + socket.getInetAddress().getHostAddress());
        } catch (IOException e){
            System.err.println("设置超时失败: " + e.getMessage());
        }
    }

    @Override
    public void run() {

    }
}
