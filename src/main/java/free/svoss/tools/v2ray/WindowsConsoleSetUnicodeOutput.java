package free.svoss.tools.v2ray;


import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

/**
 * Utility to enable Unicode console output on Windows.
 *
 * <p>Usage: Call {@link #enable()} once at start of your program.</p>
 *
 * <p>What it does:</p>
 * <ol>
 * <li>Sets Windows console output code page to UTF-8 (65001), similar to `chcp 65001` command</li>
 * <li>Resets Java's System.out to use UTF-8 encoding</li>
 * </ol>
 *
 * <p>Requirements: Java 8+ running on Windows</p>
 *
 * Inspired by: https://github.com/xyz-jphil/xyz-jphil-windows_console_set_unicode_output
 *
 */
public final class WindowsConsoleSetUnicodeOutput {

    /**
     * Result of attempting to enable Unicode console output.
     */
    public abstract static class EnableResult {

        /**
         * Successfully enabled Unicode output.
         */
        public static final class Success extends EnableResult {
            private Success() {
            }
        }

        /**
         * Unicode output was already enabled.
         */
        public static final class AlreadyEnabled extends EnableResult {
            private AlreadyEnabled() {
            }
        }

        /**
         * Failed to enable Unicode output.
         */
        public static final class Failure extends EnableResult {

            private final FailureReason reason;
            private final Throwable cause;

            private Failure(FailureReason reason, Throwable cause) {
                this.reason = reason;
                this.cause = cause;
            }

            /**
             * @return the reason for failure
             */
            public FailureReason getReason() {
                return reason;
            }

            /**
             * @return the underlying cause (may be null)
             */
            public Throwable getCause() {
                return cause;
            }
        }

        private EnableResult() {
        }
    }

    /**
     * Reasons why enabling Unicode output might fail.
     */
    public enum FailureReason {
        UNSUPPORTED_OS("Not running on Windows"),
        KERNEL32_NOT_FOUND("Windows kernel32.dll not found"),
        API_CALL_FAILED("Windows API call failed"),
        STREAM_RESET_FAILED("Failed to reset output streams");

        private final String description;

        FailureReason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Windows kernel32.dll functions used by this utility.
     */
    private interface Kernel32 extends StdCallLibrary {

        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        boolean SetConsoleOutputCP(int wCodePageID);
    }

    private static final int CP_UTF8 = 65001;

    private static volatile EnableResult lastResult;

    private WindowsConsoleSetUnicodeOutput() {
        // Utility class
    }

    /**
     * Enable Unicode console output. Safe to call multiple times.
     *
     * @return the result of the enable operation
     */
    public static EnableResult enable() {
        // Return cached result if already attempted
        if (lastResult instanceof EnableResult.Success ||
                lastResult instanceof EnableResult.AlreadyEnabled) {
            return new EnableResult.AlreadyEnabled();
        }

        // Check OS compatibility
        if (!isWindows()) {
            lastResult = new EnableResult.Failure(FailureReason.UNSUPPORTED_OS, null);
            return lastResult;
        }

        // Set console output to UTF-8 (code page 65001)
        boolean apiResult;
        try {
            apiResult = Kernel32.INSTANCE.SetConsoleOutputCP(CP_UTF8);
        } catch (Exception e) {
            lastResult = new EnableResult.Failure(FailureReason.API_CALL_FAILED, e);
            return lastResult;
        }

        if (!apiResult) {
            lastResult = new EnableResult.Failure(FailureReason.API_CALL_FAILED,
                    new RuntimeException("SetConsoleOutputCP returned false"));
            return lastResult;
        }

        // Reset Java's System.out and System.err to use UTF-8
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
            System.setErr(new PrintStream(System.err, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            lastResult = new EnableResult.Failure(FailureReason.STREAM_RESET_FAILED, e);
            return lastResult;
        }

        lastResult = new EnableResult.Success();
        return lastResult;
    }

    /**
     * Check if the current OS is Windows.
     *
     * @return true if running on Windows
     */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    /**
     * Get the last enable result, if any.
     *
     * @return the last result, or null if enable() has not been called
     */
    public static EnableResult getLastResult() {
        return lastResult;
    }

    /**
     * Format an EnableResult for display.
     */
    private static String formatResult(EnableResult result) {
        if (result instanceof EnableResult.Success) {
            return "SUCCESS - Unicode output enabled";
        }
        if (result instanceof EnableResult.AlreadyEnabled) {
            return "ALREADY ENABLED - Unicode output was previously enabled";
        }
        EnableResult.Failure failure = (EnableResult.Failure) result;
        return "FAILED - " + failure.getReason().getDescription() +
                (failure.getCause() != null ? " (" + failure.getCause().getMessage() + ")" : "");
    }
}

