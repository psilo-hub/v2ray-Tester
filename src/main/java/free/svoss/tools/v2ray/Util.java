package free.svoss.tools.v2ray;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;

public class Util {

    static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    static void atomicWrite(File target, byte[] content) throws IOException {
        File tmp = new File(target.getParent(), target.getName() + ".tmp");
        Files.write(tmp.toPath(), content);
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Retry a network operation with exponential backoff. */
    static <T> T retryNetwork(Callable<T> task, String description, int maxAttempts) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return task.call();
            } catch (java.io.IOException e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    long delay = (long) Math.pow(2, attempt - 1) * 1000;
                    System.err.println("Attempt " + attempt + "/" + maxAttempts + " failed for " + description + ": " + e.getMessage() + " (retrying in " + delay + "ms)");
                    try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            } catch (Exception e) {
                System.err.println("Non-retryable error for " + description + ": " + e.getMessage());
                return null;
            }
        }
        System.err.println("All " + maxAttempts + " attempts failed for " + description + ": " + (lastException != null ? lastException.getMessage() : "unknown error"));
        return null;
    }
}
