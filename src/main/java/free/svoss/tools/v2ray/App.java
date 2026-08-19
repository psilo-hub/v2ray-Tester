package free.svoss.tools.v2ray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import static free.svoss.tools.v2ray.Util.isEmpty;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class App {
    // --- Exit codes ---
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_ERROR = 1;
    private static final int EXIT_PARTIAL = 2;

    // --- Constants ---
    private static final int PING_TIMEOUT_MS = 2_000;
    private static final int PING_POLL_INTERVAL_MS = 200;
    private static final int DL_POLL_TIMEOUT_MS = 300;
    private static final int STATUS_REFRESH_MS = 400;
    private static final int STATUS_THREAD_JOIN_MS = 2_000;
    private static final int STATUS_LINE_MIN_WIDTH = 120;
    private static final int BEST_SERVERS_TOP_N = 50;
    private static final int QR_PNG_SIZE = 640;
    private static final int SCORE_PING_CEILING = 5_000;
    private static final int MAX_SUBSCRIPTION_BODY_BYTES = 100_000_000;
    private static final int SUBSCRIPTION_TIMEOUT_MS = 180_000;
    private static final long GLOBAL_TEST_TIMEOUT_MS = 600_000; // 10 minutes total budget
    private static final int POOL_TERMINATION_TIMEOUT_SECONDS = 60;

    /** Shared Gson instance — pretty-printed with null serialization. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private final static File serverListFile = new File(System.getProperty("user.home") + File.separator + "v2rayservers.json");
    private static final String APP_VERSION;
    private static volatile Set<ServerConfig> serverConfigs = ConcurrentHashMap.newKeySet();

    static {
        String v = "unknown";
        try (InputStream is = App.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                v = props.getProperty("version", v);
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to load version.properties: " + e.getMessage());
        }
        APP_VERSION = v;
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> System.err.println("\nUncaught exception in thread \"" + t.getName() + "\": " + e.getMessage()));

        try {
            mainInternal(args);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(EXIT_ERROR);
        }
    }

    private static void mainInternal(String[] args) throws IOException {

        File outDir = JarFolderTool.getRunningFromFolder();
        if (outDir == null) {
            System.err.println("Error: Could not determine output directory. The application must be run from a folder.");
            System.exit(1);
            return;
        }
        if (!outDir.exists() && !outDir.mkdirs()) {
            System.err.println("Error: Could not create output directory: " + outDir.getAbsolutePath());
            System.exit(1);
            return;
        }
        if (!outDir.canWrite()) {
            System.err.println("Error: Output directory is not writable: " + outDir.getAbsolutePath());
            System.exit(1);
            return;
        }
        System.out.println("Output folder: " + outDir.getAbsolutePath());

        SubscriptionManager.ensureDefaultSubscriptions();

        if (hasFlag(args, "--help")) {
            printUsage();
            return;
        }

        if (hasFlag(args, "--version")) {
            System.out.println(APP_VERSION);
            return;
        }

        if (hasFlag(args, "--list")) {
            for (String url : SubscriptionManager.loadSubscriptions()) System.out.println(url);
            return;
        }

        boolean wantsAdd = hasFlag(args, "--add");
        String addUrl = argValue(args, "--add");
        if (wantsAdd && addUrl == null) {
            System.err.println("--add requires a url argument");
            printUsage();
            System.exit(EXIT_ERROR);
        }
        if (addUrl != null) {
            SubscriptionManager.addSubscription(addUrl);
            return;
        }

        boolean wantsRemove = hasFlag(args, "--remove");
        String removeUrl = argValue(args, "--remove");
        if (wantsRemove && removeUrl == null) {
            System.err.println("--remove requires a url argument");
            printUsage();
            System.exit(EXIT_ERROR);
        }
        if (removeUrl != null) {
            SubscriptionManager.removeSubscription(removeUrl);
            return;
        }

        boolean noFetching = hasFlag(args, "--no-fetching");
        boolean justFetch = hasFlag(args, "--just-fetch");
        if (justFetch && noFetching) {
            System.err.println("--just-fetch and --no-fetching are mutually exclusive");
            printUsage();
            System.exit(EXIT_ERROR);
        }

        // 1. load stored server configs
        serverConfigs.addAll(loadServerConfigs());
        System.out.println(serverConfigs.size() + " old server configs loaded");

        // 2. fetch new server configs (unless --no-fetching)
        if (!noFetching) fetchServerConfigs(SubscriptionManager.loadSubscriptions());

        // 3. dedupe server configs (the set takes care of that) and save
        saveServerConfigs();

        if (justFetch) {
            System.out.println("Fetched and saved " + serverConfigs.size() + " server configs without testing.");
            return;
        }

        // 4. test and remove unreachable and slow servers; writes all.txt/best.txt/best.png to the jar folder
        boolean serversPassed = testServers(outDir);
        System.exit(serversPassed ? EXIT_SUCCESS : EXIT_PARTIAL);
    }

    /** Rebuild serverConfigs from JSON to deduplicate. */
    private static void rebuildServerConfigs() {
        Set<ServerConfig> setRebuild = new HashSet<>();
        for (ServerConfig sc : serverConfigs) {
            String json = GSON.toJson(sc);
            ServerConfig rebuilt = GSON.fromJson(json, ServerConfig.class);
            if (rebuilt != null) setRebuild.add(rebuilt);
        }
        serverConfigs = setRebuild;
    }

    /** Submit ping tasks; passing results go into the passedPings queue. */
    private static void submitPingPhase(ExecutorService pool, List<ServerConfig> servers,
            BlockingQueue<ServerConfigWithTestResult> passedPings, AtomicInteger pingDone,
            AtomicInteger passedPingCount, CountDownLatch pingDoneLatch, Set<ServerConfig> pingFailed) {
        for (ServerConfig serverConfig : servers) {
            pool.submit(() -> {
                try {
                    ServerConfigWithTestResult result = new ServerConfigWithTestResult(serverConfig);
                    result.setPing(getPing(serverConfig));
                    if (result.getPing() >= 0) {
                        passedPings.add(result);
                        passedPingCount.incrementAndGet();
                    } else pingFailed.add(serverConfig);
                } finally {
                    pingDone.incrementAndGet();
                    pingDoneLatch.countDown();
                }
            });
        }
    }

    /** Start download workers that poll from the passedPings queue. */
    private static void startDownloadWorkers(ExecutorService dlPool, int workers,
            BlockingQueue<ServerConfigWithTestResult> passedPings, AtomicBoolean pingPhaseDone,
            Collection<ServerConfigWithTestResult> resultsDownloadWorks, Set<ServerConfig> dlFailed,
            AtomicInteger dlDone, AtomicReference<ServerConfigWithTestResult> currentBest,
            CountDownLatch dlWorkersLatch) {
        for (int i = 0; i < workers; i++) {
            dlPool.submit(() -> {
                try {
                    while (!(pingPhaseDone.get() && passedPings.isEmpty())) {
                        ServerConfigWithTestResult result = passedPings.poll(DL_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        if (result == null) continue;
                        ServerConfig serverConfig = result.getServerConfig().orElse(null);
                        if (serverConfig == null) continue;
                        double speed = SpeedTester.measureDownloadSpeed(serverConfig);
                        if (speed > 0) {
                            result.setSpeedMbPerSecond(speed);
                            resultsDownloadWorks.add(result);
                            updateBest(currentBest, result);
                        } else dlFailed.add(serverConfig);
                        dlDone.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    dlWorkersLatch.countDown();
                }
            });
        }
    }

    /** Create a shutdown hook that gracefully stops pools and writes partial results on Ctrl+C. */
    private static Thread createShutdownHook(ExecutorService pool, ExecutorService dlPool,
            Collection<ServerConfigWithTestResult> sharedResults, AtomicBoolean resultsWritten, File outDir) {
        return new Thread(() -> {
            if (!resultsWritten.compareAndSet(false, true)) return;
            System.out.println("\nShutting down... writing partial results.");
            pool.shutdown();
            dlPool.shutdown();
            try {
                if (!pool.awaitTermination(10, TimeUnit.SECONDS)) pool.shutdownNow();
                if (!dlPool.awaitTermination(10, TimeUnit.SECONDS)) dlPool.shutdownNow();
            } catch (InterruptedException e) {
                pool.shutdownNow();
                dlPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            if (!sharedResults.isEmpty()) {
                try {
                    outputBestServers(sharedResults, outDir);
                } catch (IOException e) {
                    System.err.println("Warning: Failed to write partial results: " + e.getMessage());
                }
            }
            try {
                saveServerConfigs();
            } catch (IOException e) {
                System.err.println("Warning: Failed to save server configs: " + e.getMessage());
            }
        });
    }

    /** Remove failed servers, save configs, and write best results. */
    private static void cleanupResults(Set<ServerConfig> pingFailed, Set<ServerConfig> dlFailed,
            Collection<ServerConfigWithTestResult> resultsDownloadWorks, AtomicBoolean resultsWritten, File outDir) throws IOException {
        serverConfigs.removeAll(pingFailed);
        serverConfigs.removeAll(dlFailed);
        System.out.println(serverConfigs.size() + " servers left");

        if (resultsWritten.compareAndSet(false, true)) {
            saveServerConfigs();
            if (resultsDownloadWorks.isEmpty()) {
                System.out.println("\nNo servers passed the speed test. Possible causes:");
                System.out.println("  - All servers may be temporarily unavailable");
                System.out.println("  - Your network may be blocking the connections");
                System.out.println("  - Try updating your subscriptions (fetch from URL)");
            }
            outputBestServers(resultsDownloadWorks, outDir);
        }
    }

    private static boolean testServers(File outDir) throws IOException {
        rebuildServerConfigs();

        ArrayList<ServerConfig> asList = new ArrayList<>(serverConfigs);
        System.out.println("Testing " + asList.size() + " servers");

        int workers = Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors()));
        AtomicInteger threadCount = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "ping-worker-" + threadCount.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        ExecutorService dlPool = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "dl-worker-" + threadCount.getAndIncrement());
            t.setDaemon(true);
            return t;
        });

        // Shared state
        final int pingTotal = asList.size();
        AtomicInteger pingDone = new AtomicInteger();
        AtomicInteger passedPingCount = new AtomicInteger();
        AtomicBoolean pingPhaseDone = new AtomicBoolean(false);
        Set<ServerConfig> pingFailed = ConcurrentHashMap.newKeySet();
        BlockingQueue<ServerConfigWithTestResult> passedPings = new LinkedBlockingQueue<>();
        CountDownLatch pingDoneLatch = new CountDownLatch(pingTotal);
        Collection<ServerConfigWithTestResult> resultsDownloadWorks = new ConcurrentLinkedQueue<>();
        Set<ServerConfig> dlFailed = ConcurrentHashMap.newKeySet();
        AtomicInteger dlDone = new AtomicInteger();
        AtomicReference<ServerConfigWithTestResult> currentBest = new AtomicReference<>();
        AtomicBoolean resultsWritten = new AtomicBoolean(false);

        // Phase 1: ping tests
        submitPingPhase(pool, asList, passedPings, pingDone, passedPingCount, pingDoneLatch, pingFailed);

        // Phase 2: download workers (start immediately, poll from ping queue)
        CountDownLatch dlWorkersLatch = new CountDownLatch(workers);
        startDownloadWorkers(dlPool, workers, passedPings, pingPhaseDone, resultsDownloadWorks, dlFailed, dlDone, currentBest, dlWorkersLatch);

        // Shutdown hook
        Thread shutdownHook = createShutdownHook(pool, dlPool, resultsDownloadWorks, resultsWritten, outDir);
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        // Status display
        long startTime = System.currentTimeMillis();
        AtomicBoolean stopStatus = new AtomicBoolean(false);
        Thread statusThread = new Thread(() -> renderStatus(stopStatus, pingPhaseDone, pingTotal, pingDone, passedPingCount, dlDone, currentBest, outDir, startTime), "status");
        statusThread.setDaemon(true);
        statusThread.start();

        // Wait for ping phase
        try {
            if (!pingDoneLatch.await(GLOBAL_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                System.err.println("Warning: ping phase timed out after " + (GLOBAL_TEST_TIMEOUT_MS / 1000) + "s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pingPhaseDone.set(true);

        // Wait for download phase
        try {
            if (!dlWorkersLatch.await(GLOBAL_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                System.err.println("Warning: download phase timed out after " + (GLOBAL_TEST_TIMEOUT_MS / 1000) + "s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        stopStatus.set(true);
        try {
            statusThread.join(STATUS_THREAD_JOIN_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Shutdown pools
        dlPool.shutdown();
        pool.shutdown();
        try {
            if (!pool.awaitTermination(POOL_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.err.println("Warning: ping pool did not terminate in time, forcing shutdown");
                pool.shutdownNow();
            }
            if (!dlPool.awaitTermination(POOL_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.err.println("Warning: download pool did not terminate in time, forcing shutdown");
                dlPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Cleanup
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM already shutting down
        }

        cleanupResults(pingFailed, dlFailed, resultsDownloadWorks, resultsWritten, outDir);
        return !resultsDownloadWorks.isEmpty();
    }

    private static void renderStatus(AtomicBoolean stop, AtomicBoolean pingPhaseDone, int pingTotal, AtomicInteger pingDone, AtomicInteger passedPingCount, AtomicInteger dlDone, AtomicReference<ServerConfigWithTestResult> currentBest, File outDir, long startTime) {
        ServerConfigWithTestResult lastPrinted = null;
        while (!stop.get()) {
            ServerConfigWithTestResult best = currentBest.get();
            String line = buildStatusLine(pingPhaseDone, pingTotal, pingDone, passedPingCount, dlDone, best, startTime);
            if (best != lastPrinted) {
                System.out.println();
                System.out.println(line);
                best.getServerConfig().ifPresent(sc -> {
                    System.out.println(QrHelper.renderQrAscii(sc.getRawUrl()));
                    System.out.println();
                    QrHelper.saveQrPng(sc.getRawUrl(), new File(outDir, "best.png"));
                });
                lastPrinted = best;
            } else System.out.print("\r" + line);
            try {
                Thread.sleep(STATUS_REFRESH_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println();
    }

    private static final int PROGRESS_BAR_WIDTH = 20;

    private static String buildStatusLine(AtomicBoolean pingPhaseDone, int pingTotal, AtomicInteger pingDone, AtomicInteger passedPingCount, AtomicInteger dlDone, ServerConfigWithTestResult best, long startTime) {
        int done = pingDone.get();
        int passed = passedPingCount.get();
        int dl = dlDone.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Ping ").append(done).append("/").append(pingTotal);
        // progress bar
        int pct = pingTotal > 0 ? (done * 100 / pingTotal) : 0;
        int filled = pingTotal > 0 ? (done * PROGRESS_BAR_WIDTH / pingTotal) : 0;
        sb.append(" [");
        for (int i = 0; i < PROGRESS_BAR_WIDTH; i++) {
            sb.append(i < filled ? '=' : ' ');
        }
        sb.append("] ").append(pct).append('%');
        sb.append("  Passed ").append(passed);
        sb.append("  Speed ").append(dl);
        if (pingPhaseDone.get()) sb.append("/").append(passed);
        if (best != null) {
            sb.append("  Best: ").append(String.format(Locale.ROOT, "%.2f", best.getSpeedMbPerSecond())).append(" MB/s (").append(best.getPing()).append(" ms)");
        }
        // Calculate ETA based on speed test progress
        int totalToTest = pingPhaseDone.get() ? passed : pingTotal;
        if (totalToTest > 0 && dl > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            long msPerServer = elapsed / dl;
            int remaining = totalToTest - dl;
            long etaMs = msPerServer * remaining;
            sb.append("  ETA ").append(formatDuration(etaMs));
        }
        while (sb.length() < STATUS_LINE_MIN_WIDTH) sb.append(' ');
        return sb.toString();
    }

    private static String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes > 0) return minutes + "m " + String.format(Locale.ROOT, "%02d", seconds) + "s";
        return seconds + "s";
    }

    private static void updateBest(AtomicReference<ServerConfigWithTestResult> currentBest, ServerConfigWithTestResult candidate) {
        currentBest.updateAndGet(cur -> {
            if (cur != null && cur.getSpeedMbPerSecond() >= candidate.getSpeedMbPerSecond()) return cur;
            return candidate;
        });
    }

    private static void outputBestServers(Collection<ServerConfigWithTestResult> results, File outDir) throws IOException {
        if (results == null || results.isEmpty()) {
            System.out.println("No server passed the download speed test - nothing written.");
            return;
        }

        List<ServerConfigWithTestResult> bySpeed = sortBySpeed(results);
        List<ServerConfigWithTestResult> bestSorted = computeBestServers(results);
        ResultReporter.writeBestServersToFile(bySpeed, bestSorted, outDir);
        ResultReporter.printBestServerSummary(bySpeed, bestSorted, outDir);
    }

    private static List<ServerConfigWithTestResult> sortBySpeed(Collection<ServerConfigWithTestResult> results) {
        List<ServerConfigWithTestResult> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.comparingDouble((ServerConfigWithTestResult r) -> r.getSpeedMbPerSecond()).reversed());
        return sorted;
    }

    private static List<ServerConfigWithTestResult> computeBestServers(Collection<ServerConfigWithTestResult> results) {
        Set<ServerConfigWithTestResult> bestSet = new LinkedHashSet<>();
        bestSet.addAll(getLowestPingResults(BEST_SERVERS_TOP_N, results));
        bestSet.addAll(getFastestDownloadResults(BEST_SERVERS_TOP_N, results));
        List<ServerConfigWithTestResult> bestSorted = new ArrayList<>(bestSet);
        bestSorted.sort((a, b) -> Double.compare(calcScore(b.getSpeedMbPerSecond(), b.getPing()), calcScore(a.getSpeedMbPerSecond(), a.getPing())));
        return bestSorted;
    }



    private static <T> Set<T> topN(Collection<T> items, int n, java.util.function.Predicate<T> filter, Comparator<T> comparator) {
        Set<T> top = new LinkedHashSet<>();
        if (items == null || n <= 0) return top;
        items.stream().filter(filter).sorted(comparator).limit(n).forEach(top::add);
        return top;
    }

    private static Set<ServerConfigWithTestResult> getFastestDownloadResults(int n, Collection<ServerConfigWithTestResult> results) {
        return topN(results, n, r -> r.getSpeedMbPerSecond() != null && r.getSpeedMbPerSecond() > 0, Comparator.comparingDouble((ServerConfigWithTestResult r) -> r.getSpeedMbPerSecond()).reversed());
    }

    private static Set<ServerConfigWithTestResult> getLowestPingResults(int n, Collection<ServerConfigWithTestResult> results) {
        return topN(results, n, r -> r.getPing() != null && r.getPing() >= 0, Comparator.comparingInt((ServerConfigWithTestResult r) -> r.getPing()));
    }

    /**
     * Calculates a composite score for ranking servers based on ping and download speed.
     *
     * <p>The formula is: {@code (SCORE_PING_CEILING - ping) * speedMbPerSecond}, where
     * {@code SCORE_PING_CEILING} is 5000 ms. This means:
     * <ul>
     *   <li>Lower ping yields a higher {@code pingScore} component (inverted relationship).</li>
     *   <li>Speed directly multiplies the ping score, so faster servers rank higher.</li>
     *   <li>The multiplicative weighting means a server with slightly higher ping but
     *       significantly greater speed can outrank a low-ping, slow server.</li>
     * </ul>
     *
     * <p>Servers with invalid or missing data (null/negative ping, ping above ceiling,
     * or negligible speed) receive a score of 0 and are effectively excluded from ranking.
     *
     * @param speedMbPerSecond the measured download speed in megabits per second
     * @param ping the measured round-trip latency in milliseconds, or {@code null} if unavailable
     * @return the computed score, or 0 if inputs are invalid
     */
    static double calcScore(Double speedMbPerSecond, Integer ping) {
        if (ping == null || ping < 1 || ping > SCORE_PING_CEILING) return 0;
        if (speedMbPerSecond == null || speedMbPerSecond < 0.001) return 0;
        int pingScore = SCORE_PING_CEILING - ping;
        return pingScore * speedMbPerSecond;
    }

    private static int getPing(ServerConfig serverConfig) {
        return getPing(serverConfig.getAddress(), serverConfig.getPort());
    }

    private static int getPing(String ipOrHost, int port) {
        long start = System.currentTimeMillis();

        try (Socket socket = new Socket()) {
            // Attempt to connect to the given host/port with the specified timeout
            socket.connect(new InetSocketAddress(ipOrHost, port), PING_TIMEOUT_MS);
            long end = System.currentTimeMillis();
            // Return the elapsed time as an integer (milliseconds)
            return (int) (end - start);
        } catch (IOException e) {
            // Any I/O error (including timeout, unknown host, connection refused) means unreachable
            return -1;
        }
    }

    private static void importFromUrl(String url) {
        String content;
        try {
            content = fetchWebContent(url);
        } catch (Exception e) {
            System.err.println("Failed to fetch server configs from " + url + ": " + e.getMessage());
            return;
        }
        if (content == null) System.err.println("Failed to fetch server configs from " + url);
        else if (content.isEmpty()) System.err.println("Subscription returned empty content from " + url);
        else {
            Set<ServerConfig> configs = Parser.parse(content);
            System.out.println("Got " + configs.size() + " server configs from " + url);
            int sizeBefore = serverConfigs.size();
            serverConfigs.addAll(configs);
            int increase = serverConfigs.size() - sizeBefore;
            int dupes = configs.size() - increase;
            System.out.println(increase + " imported ... " + dupes + " dupes ... total now: " + serverConfigs.size());
        }
    }

    static boolean isPrivateHost(String host) {
        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                    || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                    || addr.isMulticastAddress()
                    || addr.getHostAddress().startsWith("169.254.");
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }

    private static String fetchWebContent(String url) {
        if (url == null || !url.startsWith("https://")) {
            System.err.println("Invalid URL (must start with https://): " + url);
            return null;
        }
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host != null && isPrivateHost(host)) {
                System.err.println("Blocked fetch to private/internal address: " + url);
                return null;
            }
        } catch (java.net.URISyntaxException e) {
            System.err.println("Invalid URL: " + url + " (" + e.getMessage() + ")");
            return null;
        }
        return Util.retryNetwork(() -> {
            Connection conn = Jsoup.connect(url);
            conn.ignoreContentType(true);
            conn.maxBodySize(MAX_SUBSCRIPTION_BODY_BYTES);
            conn.timeout(SUBSCRIPTION_TIMEOUT_MS);
            Connection.Response response = conn.execute();
            byte[] bytes = response.bodyAsBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }, "fetch " + url, 3);
    }

    private static void fetchServerConfigs(Set<String> subscriptions) throws IOException {
        System.out.println("Got " + subscriptions.size() + " subscription sources to check");
        for (String url : subscriptions) {
            importFromUrl(url);
            saveServerConfigs();
        }

        // hardcoded check of https://freev2ray.cc/
        Set<String> freeV2RayUrls = FreeV2RayCcScraper.getFreev2rayCcUrls();
        for (String u : freeV2RayUrls) importFromUrl(u);
        saveServerConfigs();
    }

    private static void saveServerConfigs() throws IOException {
        String json = GSON.toJson(serverConfigs);
        Util.atomicWrite(serverListFile, json.getBytes(StandardCharsets.UTF_8));
        System.out.println(serverConfigs.size() + " server configs saved");
    }

    private static HashSet<ServerConfig> loadServerConfigs() {
        if (serverListFile.exists()) {
            try {
                String content = new String(Files.readAllBytes(serverListFile.toPath()), StandardCharsets.UTF_8);
                if (!isEmpty(content)) {
                    HashSet<ServerConfig> loaded = new Gson().fromJson(content, new TypeToken<HashSet<ServerConfig>>() {
                    }.getType());
                    if (loaded != null) {
                        loaded.removeIf(sc -> sc == null || sc.getAddress() == null
                                || sc.getPort() < 1 || sc.getPort() > 65535
                                || sc.getProtocol() == null);
                        return loaded;
                    }
                }
            } catch (IOException | JsonSyntaxException | JsonIOException e) {
                System.err.println("WARNING: Server list file is corrupt or unreadable: " + e.getMessage());
                System.err.println("Backing up corrupt file and continuing with an empty server list.");
                File backup = new File(serverListFile.getAbsolutePath() + ".corrupt");
                if (backup.exists()) backup.delete();
                serverListFile.renameTo(backup);
                System.err.println("Corrupt file backed up to: " + backup.getAbsolutePath());
            }

        }
        return new HashSet<>();
    }

    private static boolean hasFlag(String[] args, String flag) {
        return CliArgs.hasFlag(args, flag);
    }

    private static String argValue(String[] args, String flag) {
        return CliArgs.argValue(args, flag);
    }

    private static void printUsage() {
        CliArgs.printUsage(APP_VERSION);
    }
}
