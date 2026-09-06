package free.svoss.tools.v2ray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class UpdateChecker {

    private static final String CHANGELOG_URL = "https://github.com/psilo-hub/v2ray-Tester/raw/refs/heads/main/src/main/resources/CHANGELOG.md";
    private static final String RELEASE_URL = "https://github.com/psilo-hub/v2ray-Tester/releases/latest";
    private static final long ONE_WEEK_MS = 7 * 24 * 60 * 60 * 1000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final class UpdateCheckData {
        long lastCheckTime;
        String changelogHash;
    }

    private UpdateChecker() {
    }

    public static void checkForUpdates() {
        try {
            File v2rayDir = new File(System.getProperty("user.home") + File.separator + ".v2ray");
            File updateFile = new File(v2rayDir, "updateCheck.json");

            if (!updateFile.exists()) {
                if (!v2rayDir.exists() && !v2rayDir.mkdirs()) {
                    System.err.println("Warning: Could not create .v2ray directory");
                    return;
                }
                UpdateCheckData defaultData = new UpdateCheckData();
                defaultData.lastCheckTime = 0;
                defaultData.changelogHash = null;
                writeUpdateData(updateFile, defaultData);
            }

            UpdateCheckData data = readUpdateData(updateFile);

            if (data.lastCheckTime != 0
                    && (System.currentTimeMillis() - data.lastCheckTime) < ONE_WEEK_MS) {
                return;
            }

            performUpdateCheck(updateFile, data);
        } catch (Exception e) {
            System.err.println("Warning: Update check failed: " + e.getMessage());
        }
    }

    private static void performUpdateCheck(File updateFile, UpdateCheckData data) {
        System.out.println("\nChecking for updates\n");
        String embeddedChangelog = readEmbeddedChangelog();
        if (embeddedChangelog == null) {
            System.err.println("\nFailed to load embedded CHANGELOG\n");
            return;
        }

        String embeddedHash = sha256(embeddedChangelog);

        String remoteChangelog = RawGithubFetcher.getAsString(CHANGELOG_URL);
        if (remoteChangelog == null) {
            System.err.println("\nFailed to fetch remote CHANGELOG\n");
            // Network failed — do NOT update timestamp so we retry next startup.
            return;
        }

        String remoteHash = sha256(remoteChangelog);

        if (!embeddedHash.equals(remoteHash)) {
            System.out.println();
            System.out.println("*** A new version of v2ray-tester is available! ***");
            System.out.println("Download the latest release from:");
            System.out.println("  " + RELEASE_URL);
            System.out.println();
        }else System.out.println("\n✅ We're up-to-date ✅\n");


        data.lastCheckTime = System.currentTimeMillis();
        data.changelogHash = remoteHash;

        try {
            writeUpdateData(updateFile, data);
        } catch (Exception e) {
            System.err.println("Warning: Could not write update check data: " + e.getMessage());
        }
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(b & 0xFF);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String readEmbeddedChangelog() {
        try (InputStream is = UpdateChecker.class.getResourceAsStream("/CHANGELOG.md")) {
            if (is == null) {
                return null;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] tmp = new byte[1024];
            int n;
            while ((n = is.read(tmp)) != -1) {
                buffer.write(tmp, 0, n);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return null;
        }
    }

    private static UpdateCheckData readUpdateData(File f) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            String json = new String(bytes, StandardCharsets.UTF_8);
            UpdateCheckData data = GSON.fromJson(json, UpdateCheckData.class);
            if (data != null) {
                return data;
            }
        } catch (Exception ignored) {
        }
        UpdateCheckData fallback = new UpdateCheckData();
        fallback.lastCheckTime = 0;
        fallback.changelogHash = null;
        return fallback;
    }

    private static void writeUpdateData(File f, UpdateCheckData data) throws IOException {
        String json = GSON.toJson(data);
        Util.atomicWrite(f, json.getBytes(StandardCharsets.UTF_8));
    }
}
