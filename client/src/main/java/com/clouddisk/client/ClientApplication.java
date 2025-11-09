package com.clouddisk.client;

import com.clouddisk.client.config.ClientProperties;
import com.clouddisk.client.http.AuthApiClient;
import com.clouddisk.client.http.FileApiClient;

import com.clouddisk.client.sync.FileEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootApplication
public class ClientApplication implements CommandLineRunner {

    private ClientRuntimeContext context;
    private AuthApiClient authApiClient;
    private FileApiClient fileApiClient;
    private ScheduledExecutorService syncExecutor;
    private volatile boolean isRunning = true;

    @Autowired
    public ClientApplication(ClientRuntimeContext context) {
        this.context = context;
    }

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("启动云盘客户端...");
        
        try {
            // 1. 初始化上下文
            initializeContext();
            
            // 2. 用户认证
            if (!authenticateUser()) {
                log.error("用户认证失败，程序退出");
                System.exit(1);
            }
            
            // 3. 启动同步循环
            startSyncLoop();
            
            // 4. 注册关闭钩子
            registerShutdownHook();
            
            // 5. 启动交互式命令行界面
            startInteractiveMode();
            
        } catch (Exception e) {
            log.error("客户端启动失败", e);
            shutdown();
            System.exit(1);
        }
    }

    /**
     * 初始化运行时上下文
     */
    private void initializeContext() {
        log.info("初始化运行时上下文...");
        
        context.initialize();
        
        // 初始化API客户端（使用配置的服务器地址）
        authApiClient = new AuthApiClient(context.getConfig().getServerUrl());
        fileApiClient = new FileApiClient(context.getConfig().getServerUrl(), context.getHttpClient());
        
        // 启动文件监听
        context.getSyncManager().startWatching();
        
        log.info("运行时上下文初始化完成");
    }

    /**
     * 用户认证
     */
    private boolean authenticateUser() {
        log.info("开始用户认证...");
        
        try {
            String email = context.getConfig().getEmail();
            String password = context.getConfig().getPassword();
            
            if (email == null || password == null) {
                log.info("未找到配置文件中的认证信息，使用交互式登录");
                return interactiveLogin();
            }
            
            // 尝试自动登录
            String token = authApiClient.login(email, password);
            if (token != null) {
                context.setToken(token);
                context.setUserId(email); // 使用email作为userId
                log.info("用户 {} 登录成功", email);
                return true;
            } else {
                log.warn("自动登录失败，尝试交互式登录");
                return interactiveLogin();
            }
            
        } catch (Exception e) {
            log.error("认证过程发生错误", e);
            return false;
        }
    }

    /**
     * 交互式登录
     */
    private boolean interactiveLogin() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("=== 云盘客户端登录 ===");
        System.out.print("请选择操作 (1-登录, 2-注册): ");
        
        try {
            int choice = Integer.parseInt(scanner.nextLine());
            
            System.out.print("邮箱: ");
            String email = scanner.nextLine();
            System.out.print("密码: ");
            String password = scanner.nextLine();
            
            String token;
            if (choice == 2) {
                // 注册
                if (authApiClient.register(email, password)) {
                    token = authApiClient.login(email, password);
                } else {
                    log.error("注册失败");
                    return false;
                }
            } else {
                // 登录
                token = authApiClient.login(email, password);
            }
            
            if (token != null) {
                context.setToken(token);
                context.setUserId(email);
                // 设置文件API客户端的认证令牌
                context.getFileApiClient().setAuthToken(token);
                log.info("用户 {} 登录成功", email);
                return true;
            } else {
                log.error("登录失败");
                return false;
            }
            
        } catch (Exception e) {
            log.error("交互式登录失败", e);
            return false;
        }
    }

    /**
     * 启动文件同步循环
     */
    private void startSyncLoop() {
        log.info("启动文件同步服务...");
        
        if (!context.getConfig().isEnableAutoSync()) {
            log.info("自动同步已禁用");
            return;
        }
        
        syncExecutor = Executors.newSingleThreadScheduledExecutor();
        
        // 立即执行一次同步
        syncExecutor.execute(() -> {
            try {
                performSync();
            } catch (Exception e) {
                log.error("初始同步失败", e);
            }
        });
        
        // 定期执行同步（每30秒）
        syncExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (isRunning) {
                    performSync();
                }
            } catch (Exception e) {
                log.error("定期同步失败", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
        
        log.info("文件同步服务已启动");
    }

    /**
     * 执行同步操作
     */
    private void performSync() {
        try {
            log.debug("开始文件同步...");
            
            if (context != null && context.getSyncManager() != null) {
                // 同步远程变更
                context.getSyncManager().synchronizeRemoteChanges();
            } else {
                log.warn("同步管理器未初始化");
            }
            
            log.debug("文件同步完成");
            
        } catch (Exception e) {
            log.error("同步操作失败", e);
        }
    }

    /**
     * 启动交互式命令行界面
     */
    private void startInteractiveMode() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\n=== 云盘客户端已启动 ===");
        System.out.println("可用命令:");
        System.out.println("  sync - 手动同步文件");
        System.out.println("  list - 查看文件列表");
        System.out.println("  upload <文件名> - 上传文件");
        System.out.println("  download <文件名> - 下载文件");
        System.out.println("  delete <文件名> - 删除文件");
        System.out.println("  status - 查看状态");
        System.out.println("  help - 显示帮助");
        System.out.println("  exit - 退出程序");
        System.out.println();
        
        while (isRunning) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) {
                continue;
            }
            
            String[] parts = input.split("\\s+");
            String command = parts[0].toLowerCase();
            
            try {
                switch (command) {
                    case "sync":
                        performSync();
                        break;
                    case "list":
                        listFiles();
                        break;
                    case "upload":
                        if (parts.length > 1) {
                            uploadFile(parts[1]);
                        } else {
                            System.out.println("用法: upload <文件名>");
                        }
                        break;
                    case "download":
                        if (parts.length > 1) {
                            downloadFile(parts[1]);
                        } else {
                            System.out.println("用法: download <文件名>");
                        }
                        break;
                    case "delete":
                        if (parts.length > 1) {
                            deleteFile(parts[1]);
                        } else {
                            System.out.println("用法: delete <文件名>");
                        }
                        break;
                    case "status":
                        showStatus();
                        break;
                    case "help":
                        showHelp();
                        break;
                    case "exit":
                        shutdown();
                        break;
                    default:
                        System.out.println("未知命令: " + command);
                        System.out.println("输入 'help' 查看可用命令");
                }
            } catch (Exception e) {
                log.error("执行命令失败: " + command, e);
                System.out.println("执行命令失败: " + e.getMessage());
            }
        }
    }

    /**
     * 列出文件
     */
    private void listFiles() {
        // TODO: 实现文件列表功能
        System.out.println("文件列表功能待实现");
    }

    /**
     * 上传文件
     */
    private void uploadFile(String filename) {
        try {
            Path filePath = Paths.get(context.getConfig().getSyncDir(), filename);
            // 检查文件是否存在
            if (!filePath.toFile().exists()) {
                System.out.println("文件不存在: " + filename);
                System.out.println("请确保文件存在于同步目录中: " + context.getConfig().getSyncDir());
                return;
            }
            context.getSyncManager().handleLocalEvent(
                new FileEvent(FileEvent.EventType.CREATE, filePath, null)
            );
            System.out.println("文件上传任务已提交: " + filename);
        } catch (Exception e) {
            log.error("上传文件失败: " + filename, e);
            System.out.println("上传文件失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件
     */
    private void downloadFile(String filename) {
        // TODO: 实现文件下载功能
        System.out.println("文件下载功能待实现");
    }

    /**
     * 删除文件
     */
    private void deleteFile(String filename) {
        // TODO: 实现文件删除功能
        System.out.println("文件删除功能待实现");
    }

    /**
     * 显示状态
     */
    private void showStatus() {
        System.out.println("=== 客户端状态 ===");
        System.out.println("用户: " + context.getUserId());
        System.out.println("同步目录: " + context.getConfig().getSyncDir());
        System.out.println("自动同步: " + (context.getConfig().isEnableAutoSync() ? "开启" : "关闭"));
        System.out.println("压缩策略: " + context.getConfig().getCompressStrategy());
        System.out.println("运行状态: " + (isRunning ? "正常" : "停止"));
    }

    /**
     * 显示帮助
     */
    private void showHelp() {
        System.out.println("=== 帮助信息 ===");
        System.out.println("sync - 手动同步文件");
        System.out.println("list - 查看文件列表");
        System.out.println("upload <文件名> - 上传文件");
        System.out.println("download <文件名> - 下载文件");
        System.out.println("delete <文件名> - 删除文件");
        System.out.println("status - 查看状态");
        System.out.println("help - 显示帮助");
        System.out.println("exit - 退出程序");
    }

    /**
     * 注册关闭钩子
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("接收到关闭信号，正在清理资源...");
            shutdown();
        }));
    }

    /**
     * 关闭客户端
     */
    private void shutdown() {
        log.info("正在关闭客户端...");
        isRunning = false;
        
        if (syncExecutor != null && !syncExecutor.isShutdown()) {
            syncExecutor.shutdown();
            try {
                if (!syncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    syncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                syncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (context != null) {
            context.shutdown();
        }
        
        log.info("客户端已关闭");
        System.exit(0);
    }
}