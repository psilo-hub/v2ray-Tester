package free.svoss.tools.v2ray;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages discovery, download, and extraction of the Xray core binary.
 */
final class XrayCoreManager {

    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final int XRAY_EXECUTABLE_SEARCH_DEPTH = 3;
    private static final long MAX_DOWNLOAD_BYTES = 100L * 1024 * 1024;

    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    /** Cached path to the Xray executable after first successful lookup. */
    private static volatile File cachedCore;
    private static final Object coreLock = new Object();

    private XrayCoreManager() {
    }

    static File findOrDownloadCore() throws IOException {
        File cached = cachedCore;
        if (cached != null && cached.isFile())
            return cached;
        synchronized (coreLock) {
            cached = cachedCore;
            if (cached != null && cached.isFile())
                return cached;
            Optional<File> exe = findCore();
            if (exe.isPresent()) {
                cachedCore = exe.get();
                return exe.get();
            }
            File dir = coreDir();
            dir.mkdirs();
            File shippedZip = shippedZip();
            if (shippedZip != null)
                unzip(shippedZip, dir);
            else
                downloadXray(dir);
            Optional<File> exeAfterDownload = findXrayExecutable(dir);
            if (!exeAfterDownload.isPresent())
                throw new IOException("Xray core did not produce an executable in " + dir);
            cachedCore = exeAfterDownload.get();
            return exeAfterDownload.get();
        }
    }

    private static Optional<File> findCore() {
        Optional<File> env = fromPath(System.getenv("V2RAY_CORE"));
        if (env.isPresent())
            return env;
        Optional<File> prop = fromPath(System.getProperty("v2ray.core"));
        if (prop.isPresent())
            return prop;
        return findXrayExecutable(coreDir());
    }

    private static Optional<File> fromPath(String path) {
        if (path == null || path.trim().isEmpty())
            return Optional.empty();
        File f = new File(path);
        if (f.isFile())
            return Optional.of(f);
        if (f.isDirectory())
            return findXrayExecutable(f);
        return Optional.empty();
    }

    private static File coreDir() {
        return new File(System.getProperty("user.home"), ".v2ray" + File.separator + "core");
    }

    private static Optional<File> findXrayExecutable(File dir) {
        if (dir == null || !dir.isDirectory())
            return Optional.empty();
        String target = OS_NAME.contains("win") ? "xray.exe" : "xray";
        try (Stream<Path> walk = Files.walk(dir.toPath(), XRAY_EXECUTABLE_SEARCH_DEPTH)) {
            return walk.filter(p -> target.equals(p.getFileName().toString()))
                    .map(Path::toFile)
                    .filter(File::isFile)
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static File shippedZip() {
        try {
            File base = JarFolderTool.getRunningFromFolder();
            if (base == null || !base.isDirectory())
                return null;
            File named = new File(base, assetName());
            if (named.isFile())
                return named;
            try (Stream<Path> stream = Files.list(base.toPath())) {
                return stream.map(Path::toFile)
                        .filter(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".zip"))
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void downloadXray(File dir) throws IOException {
        String assetName = assetName();
        File zip = new File(dir, assetName);
        if (!zip.exists()) {
            String downloadUrl = latestAssetUrl(assetName);
            Boolean success = Util.retryNetwork(() -> {
                downloadFile(downloadUrl, zip);
                return true;
            }, "download Xray", 3);
            if (success == null) throw new IOException("Failed to download Xray after retries");
        }
        try {
            unzip(zip, dir);
        } catch (IOException e) {
            zip.delete();
            throw e;
        }
    }

    static String assetName() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return assetNameFor(OS_NAME, arch);
    }

    /** Maps the running OS and CPU architecture to the matching Xray-core release asset. */
    static String assetNameFor(String osName, String arch) {
        String os = osName.toLowerCase(Locale.ROOT);
        arch = arch.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            if (isArm64(arch))
                return "Xray-windows-arm64-v8a.zip";
            if (is32BitX86(arch))
                return "Xray-windows-32.zip";
            return "Xray-windows-64.zip";
        }
        if (os.contains("mac")) {
            if (isArm64(arch))
                return "Xray-macos-arm64-v8a.zip";
            return "Xray-macos-64.zip";
        }
        if (os.contains("freebsd")) {
            if (isArm64(arch))
                return "Xray-freebsd-arm64-v8a.zip";
            if (isArm32(arch))
                return "Xray-freebsd-arm32-v7a.zip";
            if (is32BitX86(arch))
                return "Xray-freebsd-32.zip";
            return "Xray-freebsd-64.zip";
        }
        if (os.contains("openbsd")) {
            if (isArm64(arch))
                return "Xray-openbsd-arm64-v8a.zip";
            if (isArm32(arch))
                return "Xray-openbsd-arm32-v7a.zip";
            if (is32BitX86(arch))
                return "Xray-openbsd-32.zip";
            return "Xray-openbsd-64.zip";
        }
        // Linux (and unknown OSes) fall back to the linux assets
        if (isArm64(arch))
            return "Xray-linux-arm64-v8a.zip";
        if (isArm32(arch))
            return "Xray-linux-arm32-v7a.zip";
        if (arch.contains("riscv64"))
            return "Xray-linux-riscv64.zip";
        if (arch.contains("ppc64le"))
            return "Xray-linux-ppc64le.zip";
        if (arch.contains("ppc64"))
            return "Xray-linux-ppc64.zip";
        if (arch.contains("s390x"))
            return "Xray-linux-s390x.zip";
        if (arch.contains("loongarch64") || arch.contains("loong64"))
            return "Xray-linux-loong64.zip";
        if (arch.contains("mips64le"))
            return "Xray-linux-mips64le.zip";
        if (arch.contains("mips64"))
            return "Xray-linux-mips64.zip";
        if (arch.contains("mips") && (arch.contains("le") || arch.endsWith("el")))
            return "Xray-linux-mips32le.zip";
        if (arch.contains("mips"))
            return "Xray-linux-mips32.zip";
        if (is32BitX86(arch))
            return "Xray-linux-32.zip";
        return "Xray-linux-64.zip";
    }

    private static boolean isArm64(String arch) {
        return arch.contains("aarch64") || arch.contains("arm64");
    }

    private static boolean isArm32(String arch) {
        return arch.startsWith("arm");
    }

    private static boolean is32BitX86(String arch) {
        return arch.equals("x86") || arch.equals("i386") || arch.equals("i486")
                || arch.equals("i586") || arch.equals("i686");
    }

    private static String latestAssetUrl(String assetName) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.github.com/repos/XTLS/Xray-core/releases/latest").openConnection();
        conn.setRequestProperty("User-Agent", "v2ray-tester");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        try {
            if (conn.getResponseCode() != 200)
                throw new IOException("GitHub API returned " + conn.getResponseCode());
            JsonObject root = JsonParser.parseReader(new InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray assets = root.getAsJsonArray("assets");
            for (JsonElement el : assets) {
                JsonObject asset = el.getAsJsonObject();
                if (assetName.equals(asset.get("name").getAsString()))
                    return asset.get("browser_download_url").getAsString();
            }
            throw new IOException("Xray asset not found: " + assetName);
        } finally {
            conn.disconnect();
        }
    }

    private static void downloadFile(String url, File target) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "v2ray-tester");
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(120_000);
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300)
                throw new IOException("download returned HTTP " + code);
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                long total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_DOWNLOAD_BYTES)
                        throw new IOException("download exceeds size limit (" + MAX_DOWNLOAD_BYTES + " bytes)");
                    out.write(buf, 0, n);
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void unzip(File zip, File destDir) throws IOException {
        Path root = destDir.toPath().toAbsolutePath().normalize();
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = root.resolve(entry.getName()).normalize();
                if (!out.startsWith(root))
                    throw new IOException("zip entry escapes target dir: " + entry.getName());
                if (entry.isDirectory())
                    Files.createDirectories(out);
                else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }
}
