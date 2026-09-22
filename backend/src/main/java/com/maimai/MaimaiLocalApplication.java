package com.maimai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Windows 本地开发总入口。运行此类启动数据库、后端和前端。
 * 不属于 Spring 容器，不用于部署；生产入口仍是 MaimaiApplication。
 * 只关闭本次启动的进程，复用的服务保持运行；数据库通过 mysqladmin 正常关闭。
 */
public final class MaimaiLocalApplication {
    private static final int BACKEND_PORT = 8081;
    private static final int FRONTEND_PORT = 5173;
    private static final String BACKEND_URL = "http://127.0.0.1:" + BACKEND_PORT;
    private static final String WEBSITE_URL = "http://127.0.0.1:" + FRONTEND_PORT;

    private final Path root;
    private final Path logs;
    private final Properties settings = new Properties();
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2)).build();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private Process database;
    private Process backend;
    private Process frontend;
    private int databasePort;

    private MaimaiLocalApplication(Path root) throws IOException {
        this.root = root;
        this.logs = Files.createDirectories(root.resolve(".local/launcher-runs/")
                .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))));
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        if (!System.getProperty("os.name").startsWith("Windows")) {
            throw new IllegalStateException("此本地启动器适用于 Windows；其他系统请使用各服务的开发脚本。");
        }
        Path root = findProjectRoot();
        Files.createDirectories(root.resolve(".local"));
        try (FileChannel channel = FileChannel.open(root.resolve(".local/local-launcher.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) {
                throw new IllegalStateException("麦麦统一启动器已经运行，请回到它的终端操作，不要重复启动。");
            }
            MaimaiLocalApplication launcher = new MaimaiLocalApplication(root);
            Thread shutdown = new Thread(launcher::close, "maimai-local-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            try {
                launcher.start();
                if (!List.of(args).contains("--smoke-test")) {
                    launcher.waitForStop();
                }
            } finally {
                launcher.close();
                Runtime.getRuntime().removeShutdownHook(shutdown);
            }
        }
    }

    private static Path findProjectRoot() throws IOException {
        Path directory = Path.of("").toAbsolutePath().normalize();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("backend/pom.xml"))
                    && Files.isRegularFile(directory.resolve("frontend/package.json"))
                    && Files.isRegularFile(directory.resolve("scripts/environment.ps1"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IOException("请在 VS Code 打开麦麦项目根目录后运行此文件。");
    }

    private void start() throws Exception {
        System.out.println("麦麦二手 · 本地统一启动\n日志目录：" + logs);
        Process prepare = startProcess("prepare", root, powershell("prepare-vscode-backend.ps1", "-PrepareOnly"));
        if (!prepare.waitFor(30, TimeUnit.SECONDS)) {
            stopProcessTree(prepare);
            throw failure("准备本地配置超时", "prepare");
        }
        if (prepare.exitValue() != 0) {
            throw failure("准备配置失败，请检查本地数据库配置、Node.js 和 Java/Maven 路径", "prepare");
        }
        try (var input = Files.newInputStream(root.resolve(".local/private/vscode-backend.properties"))) {
            settings.load(input);
        }
        String databaseHost = settings.getProperty("MAIMAI_DB_HOST", "127.0.0.1");
        if (!List.of("127.0.0.1", "localhost").contains(databaseHost)) {
            throw new IllegalStateException("本地启动器只连接本机数据库，请检查 MAIMAI_DB_HOST。");
        }
        databasePort = Integer.parseInt(settings.getProperty("MAIMAI_DB_PORT", "3307"));
        requireReusablePort(BACKEND_PORT, this::backendReady);
        requireReusablePort(FRONTEND_PORT, this::frontendReady);
        Path vite = root.resolve("frontend/node_modules/vite/bin/vite.js");
        if (!Files.isRegularFile(vite)) {
            throw new IllegalStateException("缺少前端依赖，请在项目根目录执行 npm ci --prefix frontend 后重试。");
        }

        if (portOpen(databasePort)) {
            System.out.println("[1/3] 复用已有 MySQL：" + databasePort);
        } else {
            startDatabase();
            await("数据库", database, () -> portOpen(databasePort), "mysql", 60);
            System.out.println("[1/3] MySQL 已启动：" + databasePort);
        }
        if (portOpen(BACKEND_PORT)) {
            System.out.println("[2/3] 复用已有麦麦后端：" + BACKEND_PORT);
        } else {
            backend = startProcess("backend", root, powershell("dev-backend.ps1"));
            await("后端", backend, this::backendReady, "backend", 120);
            System.out.println("[2/3] 后端已启动：" + BACKEND_PORT);
        }
        if (portOpen(FRONTEND_PORT)) {
            System.out.println("[3/3] 复用已有麦麦前端：" + FRONTEND_PORT);
        } else {
            frontend = startProcess("frontend", root.resolve("frontend"), List.of(settings.getProperty("launcher.node"),
                    vite.toString(), "--host", "127.0.0.1", "--port", String.valueOf(FRONTEND_PORT), "--strictPort"));
            await("前端", frontend, this::frontendReady, "frontend", 45);
            System.out.println("[3/3] 前端已启动：" + FRONTEND_PORT);
        }
        if (!assistantReady(WEBSITE_URL)) {
            throw failure("前端已启动，但经前端访问后端接口失败", "frontend");
        }
        System.out.println("\n网站已就绪，请打开：" + WEBSITE_URL + "/");
        System.out.println("这是完整网站地址；8081 为后端接口端口。");
    }

    private void startDatabase() throws IOException {
        Path mysql = Path.of(settings.getProperty("launcher.mysql"));
        Path data = Path.of(settings.getProperty("launcher.mysqlData")).toRealPath();
        Path adminDefaults = Path.of(settings.getProperty("launcher.mysqlDefaults"));
        if (!data.startsWith(root.resolve(".local").toRealPath()) || !Files.isDirectory(data.resolve("mysql"))) {
            throw new IOException("没有找到项目专用数据库目录；启动器不会初始化或覆盖数据库。");
        }
        if (!Files.isRegularFile(mysql) || !Files.isRegularFile(mysql.resolveSibling("mysqladmin.exe"))
                || !Files.isRegularFile(adminDefaults)) {
            throw new IOException("缺少 MySQL 程序或本地关闭凭据，请先手动启动项目数据库再运行本入口。");
        }
        // Some Windows MySQL builds cannot open a data directory passed as a Chinese absolute path.
        // The verified data directory is still project-local; pass its relative name from .local.
        Path localDirectory = root.resolve(".local").toRealPath();
        database = startProcess("mysql", localDirectory, List.of(mysql.toString(), "--no-defaults",
                "--basedir=" + mysql.getParent().getParent(), "--datadir=./" + localDirectory.relativize(data),
                "--port=" + databasePort, "--bind-address=127.0.0.1", "--mysqlx=0",
                "--innodb-buffer-pool-size=128M", "--max-connections=60", "--console"));
    }

    private List<String> powershell(String script, String... extra) {
        List<String> command = new ArrayList<>(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", root.resolve("scripts").resolve(script).toString()));
        command.addAll(List.of(extra));
        return command;
    }

    private Process startProcess(String name, Path directory, List<String> command) throws IOException {
        return new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
                .redirectOutput(logs.resolve(name + ".log").toFile()).start();
    }

    private void requireReusablePort(int port, BooleanSupplier matchesProject) throws IOException {
        if (portOpen(port) && !matchesProject.getAsBoolean()) {
            throw new IOException("端口 " + port + " 已被其他或尚未就绪的服务占用，请检查；启动器不会自动结束它。");
        }
    }

    private static boolean portOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean backendReady() {
        return assistantReady(BACKEND_URL);
    }

    private boolean assistantReady(String origin) {
        return responseContains(origin + "/api/v1/support/assistant", "\"maxMessageChars\"");
    }

    private boolean frontendReady() {
        return responseContains(WEBSITE_URL + "/package.json", "\"maimai-frontend\"");
    }

    private boolean responseContains(String url, String marker) {
        try {
            var response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains(marker);
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void await(String label, Process process, BooleanSupplier ready, String logName, int seconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw failure(label + "退出，退出码 " + process.exitValue(), logName);
            }
            if (ready.getAsBoolean()) {
                return;
            }
            Thread.sleep(500);
        }
        throw failure(label + "启动超时", logName);
    }

    private IOException failure(String message, String logName) {
        return new IOException(message + "。详细日志：" + logs.resolve(logName + ".log"));
    }

    private void waitForStop() throws Exception {
        System.out.println("在本终端按回车停止本次启动的服务；复用的服务不会被关闭。");
        Thread input = new Thread(() -> {
            try {
                if (new BufferedReader(new InputStreamReader(System.in)).readLine() != null) {
                    stopRequested.set(true);
                }
            } catch (IOException ignored) {
                // A detached terminal has no input; keep serving until the process is stopped.
            }
        }, "maimai-local-input");
        input.setDaemon(true);
        input.start();
        while (!stopRequested.get()) {
            if ((database != null && !database.isAlive()) || (backend != null && !backend.isAlive())
                    || (frontend != null && !frontend.isAlive())) {
                throw new IOException("有服务意外退出，请查看日志目录：" + logs);
            }
            Thread.sleep(500);
        }
    }

    private void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        stopProcessTree(frontend);
        stopProcessTree(backend);
        if (database != null && database.isAlive()) {
            try {
                Path mysqlAdmin = Path.of(settings.getProperty("launcher.mysql")).resolveSibling("mysqladmin.exe");
                Path localDirectory = root.resolve(".local");
                Path defaultsFile = Path.of(settings.getProperty("launcher.mysqlDefaults"));
                Process shutdown = startProcess("mysql-shutdown", localDirectory, List.of(mysqlAdmin.toString(),
                        "--defaults-file=./" + localDirectory.relativize(defaultsFile), "--host=127.0.0.1",
                        "--port=" + databasePort, "--protocol=TCP", "--connect-timeout=5", "shutdown"));
                if (!shutdown.waitFor(20, TimeUnit.SECONDS)) {
                    stopProcessTree(shutdown);
                    System.err.println("MySQL 正常关闭超时，已保留数据库进程，请查看日志。");
                } else if (shutdown.exitValue() != 0 || !database.waitFor(20, TimeUnit.SECONDS)) {
                    System.err.println("MySQL 未能正常关闭，已保留数据库进程，请查看 mysql-shutdown.log。");
                }
            } catch (Exception exception) {
                System.err.println("MySQL 关闭未完成，已保留数据库进程：" + exception.getClass().getSimpleName());
            }
        }
        System.out.println("统一启动器已结束；未停止其他项目或复用的服务。");
    }

    private static void stopProcessTree(Process process) {
        if (process == null) {
            return;
        }
        List<ProcessHandle> children = process.descendants()
                .sorted(Comparator.comparingInt(MaimaiLocalApplication::processDepth).reversed()).toList();
        for (ProcessHandle child : children) {
            child.destroy();
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        for (ProcessHandle child : children) {
            if (child.isAlive()) {
                child.destroyForcibly();
            }
        }
    }

    private static int processDepth(ProcessHandle process) {
        int depth = 0;
        var parent = process.parent();
        while (parent.isPresent()) {
            process = parent.get();
            depth++;
            parent = process.parent();
        }
        return depth;
    }
}
