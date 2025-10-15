import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EasyCloudDiskServer {

    private static final int PORT = 8080;
    private static final String CLOUD_DIR = "src/main/java/cloud/";
    // 处理并发请求 所以创建线程池
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    private static final int BUFFER_SIZE = 8192; // 缓冲区大小
    private static final int SOCKET_TIMEOUT = 30000; // Socket超时时间
    /**
     * 服务端启动，监听客户端连接
     * 1. 统一的 CLOUD_DIR 作为云盘根目录，用于后续做路径约束与隔离
     * 2. 创建线程池，避免为每一个连接创建新线程带来的线程开销
     * 3. 确保存储目录存在，避免文件操作失败
     * 4. 主循环阻塞在 accept() 方法，每个连接交由 ClientHandler 处理
     */
    public void start() {
        File cloudDir = new File(CLOUD_DIR);
        if(!cloudDir.exists())  cloudDir.mkdir();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("服务端已启动，端口：" + PORT);
            while (true) {
                // serverSocket.accept：等待并接受客户端连接，返回一个Socket对象，代表与客户端的连接
                // 如果没有客户端连接，则阻塞在这里
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket, CLOUD_DIR, BUFFER_SIZE, SOCKET_TIMEOUT));
            }
        } catch (Exception e) {
            System.err.println("服务端启动失败: " + e.getMessage());
        }
    }
}

