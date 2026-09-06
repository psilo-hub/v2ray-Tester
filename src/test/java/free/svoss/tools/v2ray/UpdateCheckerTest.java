package free.svoss.tools.v2ray;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the M2 "stored-update fast path" in UpdateChecker.
 * Target: src/main/java/free/svoss/tools/v2ray/UpdateChecker.java
 * Session: ses_m2
 */
class UpdateCheckerTest {

    private static boolean invokeIsStoredUpdatePending(UpdateChecker.UpdateCheckData data) throws Exception {
        Method m = UpdateChecker.class.getDeclaredMethod("isStoredUpdatePending", UpdateChecker.UpdateCheckData.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, data);
    }

    private static String sha256OfEmbeddedChangelog() throws Exception {
        InputStream is = UpdateChecker.class.getResourceAsStream("/CHANGELOG.md");
        assertNotNull(is, "Embedded /CHANGELOG.md resource must exist");
        byte[] bytes;
        try (InputStream in = is) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[1024];
            int n;
            while ((n = in.read(tmp)) != -1) {
                buffer.write(tmp, 0, n);
            }
            bytes = buffer.toByteArray();
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            String h = Integer.toHexString(b & 0xFF);
            if (h.length() == 1) {
                hex.append('0');
            }
            hex.append(h);
        }
        return hex.toString();
    }

    @Test
    void nullChangelogHashMeansNoStoredUpdate() throws Exception {
        UpdateChecker.UpdateCheckData data = new UpdateChecker.UpdateCheckData();
        data.lastCheckTime = 0;
        data.changelogHash = null;
        assertFalse(invokeIsStoredUpdatePending(data));
    }

    @Test
    void storedHashEqualToEmbeddedMeansNoStoredUpdate() throws Exception {
        String embeddedHash = sha256OfEmbeddedChangelog();
        UpdateChecker.UpdateCheckData data = new UpdateChecker.UpdateCheckData();
        data.lastCheckTime = 0;
        data.changelogHash = embeddedHash;
        assertFalse(invokeIsStoredUpdatePending(data));
    }

    @Test
    void storedHashDifferentFromEmbeddedMeansUpdatePending() throws Exception {
        String embeddedHash = sha256OfEmbeddedChangelog();
        String differentHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        assertFalse(differentHash.equals(embeddedHash), "Test vector must actually differ from embedded hash");
        UpdateChecker.UpdateCheckData data = new UpdateChecker.UpdateCheckData();
        data.lastCheckTime = 0;
        data.changelogHash = differentHash;
        assertTrue(invokeIsStoredUpdatePending(data));
    }

    @Test
    void embeddedChangelogHashIsLowercaseHexOfLength64() throws Exception {
        String embeddedHash = sha256OfEmbeddedChangelog();
        assertEquals(64, embeddedHash.length());
        assertTrue(embeddedHash.matches("[0-9a-f]{64}"), "Expected lowercase hex of length 64, got: " + embeddedHash);
    }
}