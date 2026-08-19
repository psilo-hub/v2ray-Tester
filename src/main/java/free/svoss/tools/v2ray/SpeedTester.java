package free.svoss.tools.v2ray;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.function.IntToDoubleFunction;
import java.util.stream.Stream;

/**
 * Measures the download speed of a {@link ServerConfig} by starting a local Xray core
 * with the server as outbound and downloading a test file through the SOCKS5 proxy.
 * Returns speed in MB/s, or -1 on failure. All state is local to a single call.
 */
public final class SpeedTester {

    private static final String[] SPEED_TEST_URLS = {
            "https://speed.cloudflare.com/__down?bytes=10000000",
            "http://speedtest.tele2.net/10MB.zip",
            "http://proof.ovh.net/files/10Mb.dat",
            "http://cachefly.cachefly.net/10mb.test"
    };

    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final int CORE_READY_WAIT_MS = 5_000;

    /** Hard wall-clock budget for one whole speed measurement, so a slow-but-alive proxy can never hang a worker. */
    private static final int MEASUREMENT_TIMEOUT_MS = 25_000;

    private static final int DELETE_TREE_MAX_RETRIES = 5;
    private static final int DELETE_TREE_RETRY_DELAY_MS = 200;
    private static final int FORCE_DESTROY_WAIT_MS = 1_500;
    private static final int PORT_CONNECT_TIMEOUT_MS = 300;
    private static final int PORT_FIND_MAX_ATTEMPTS = 6;
    /** Cached lowercase OS name for repeated checks. */
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);

    private SpeedTester() {
    }

    /**
     * Returns the download speed in MB/s, or -1 if the download did not work.
     */
    public static double measureDownloadSpeed(ServerConfig serverConfig) {
        return runWithProxy(serverConfig, SpeedTester::downloadThroughSocks);
    }

    /**
     * Returns a rough "real" latency in milliseconds measured through the tunnel
     * (v2rayN style, via generate_204), or -1 on failure.
     */
    public static double measureRealDelay(ServerConfig serverConfig) {
        return runWithProxy(serverConfig, SpeedTester::generate204Delay);
    }

    private static double runWithProxy(ServerConfig cfg, IntToDoubleFunction probe) {
        if (cfg == null)
            return -1;
        File core;
        try {
            core = XrayCoreManager.findOrDownloadCore();
        } catch (Exception e) {
            System.err.println("Warning: Failed to find or download core: " + e.getMessage());
            return -1;
        }
        if (core != null)
            core.setExecutable(true, false);
        int socksPort;
        try {
            socksPort = findFreePort();
        } catch (IOException e) {
            return -1;
        }

        Process process = null;
        Path tmp = null;
        long pid = -1;
        try {
            tmp = Files.createTempDirectory("v2raytest");
            Path configFile = tmp.resolve("config.json");
            String config = XrayConfigBuilder.buildConfig(cfg, socksPort);
            if (config == null)
                return -1;
            Files.write(configFile, config.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ProcessBuilder pb = new ProcessBuilder(core.getAbsolutePath(), "-config", configFile.toAbsolutePath().toString());
            pb.redirectOutput(tmp.resolve("xray.out.log").toFile());
            pb.redirectError(tmp.resolve("xray.err.log").toFile());
            process = pb.start();
            pid = pidOf(process);

            if (!waitForPort(socksPort, CORE_READY_WAIT_MS))
                return -1;
            return probe.applyAsDouble(socksPort);
        } catch (Exception e) {
            System.err.println("Warning: Speed test failed for port " + socksPort + ": " + e.getMessage());
            return -1;
        } finally {
            if (process != null)
                forceDestroy(process, pid);
            if (tmp != null)
                deleteTree(tmp);
        }
    }

    private static void forceDestroy(Process process, long pid) {
        process.destroy();
        if (waitForQuietly(process, FORCE_DESTROY_WAIT_MS))
            return;
        process.destroyForcibly();
        if (waitForQuietly(process, FORCE_DESTROY_WAIT_MS))
            return;
        if (pid > 0) {
            try {
                if (isWindows())
                    new ProcessBuilder("taskkill", "/F", "/T", "/PID", Long.toString(pid)).redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS);
                else
                    new ProcessBuilder("kill", "-9", Long.toString(pid)).redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("Warning: Failed to force-destroy process " + pid + ": " + e.getMessage());
            }
        }
    }

    private static boolean waitForQuietly(Process process, long millis) {
        try {
            return process.waitFor(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    private static long pidOf(Process process) {
        try {
            Method pidMethod = Process.class.getMethod("pid");
            return ((Number) pidMethod.invoke(process)).longValue();
        } catch (Exception e) {
            // Java 9+ pid() method not available, trying reflection fallback
        }
        try {
            Class<?> cls = process.getClass();
            while (cls != null) {
                try {
                    Field pid = cls.getDeclaredField("pid");
                    pid.setAccessible(true);
                    if (pid.getType() == long.class)
                        return pid.getLong(process);
                    return pid.getInt(process);
                } catch (NoSuchFieldException e) {
                    cls = cls.getSuperclass();
                }
            }
        } catch (Exception e) {
            // Could not determine PID via reflection
        }
        return -1;
    }

    private static void deleteTree(Path tmp) {
        for (int attempt = 0; attempt < DELETE_TREE_MAX_RETRIES; attempt++) {
            try (Stream<Path> walk = Files.walk(tmp)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException e) {
                return;
            }
            if (!Files.exists(tmp))
                return;
            try {
                Thread.sleep(DELETE_TREE_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static double downloadThroughSocks(int socksPort) {
        long deadlineNanos = System.nanoTime() + MEASUREMENT_TIMEOUT_MS * 1_000_000L;
        for (String url : SPEED_TEST_URLS) {
            double speed = tryDownload(url, socksPort, deadlineNanos);
            if (speed > 0)
                return speed;
            if (System.nanoTime() >= deadlineNanos)
                break;
        }
        return -1;
    }

    private static double tryDownload(String url, int socksPort, long deadlineNanos) {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", socksPort));
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection(proxy);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.setRequestProperty("Connection", "close");
            conn.setInstanceFollowRedirects(true);

            long bytes = 0;
            long start = System.nanoTime();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bytes += n;
                    if (System.nanoTime() >= deadlineNanos)
                        break;
                }
            }
            long elapsedNanos = System.nanoTime() - start;
            if (bytes <= 0 || elapsedNanos <= 0)
                return -1;
            double seconds = elapsedNanos / 1_000_000_000.0;
            return bytes / 1_000_000.0 / seconds;
        } catch (IOException e) {
            return -1;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    private static double generate204Delay(int socksPort) {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", socksPort));
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://www.gstatic.com/generate_204").openConnection(proxy);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Connection", "close");
            conn.setInstanceFollowRedirects(true);

            long start = System.nanoTime();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[64 * 1024];
                while (in.read(buf) > 0) {
                    // discard body
                }
            }
            return (System.nanoTime() - start) / 1_000_000.0;
        } catch (IOException e) {
            return -1;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    private static boolean waitForPort(int port, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", port), PORT_CONNECT_TIMEOUT_MS);
                return true;
            } catch (IOException ignored) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private static int findFreePort() throws IOException {
        for (int attempt = 0; attempt < PORT_FIND_MAX_ATTEMPTS; attempt++) {
            try (ServerSocket ss = new ServerSocket(0)) {
                ss.setReuseAddress(true);
                return ss.getLocalPort();
            }
        }
        throw new IOException("Could not find a free port after " + PORT_FIND_MAX_ATTEMPTS + " attempts");
    }
}
