package free.svoss.tools.v2ray;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that XrayCoreManager maps OS + architecture to the correct Xray-core
 * release asset name. Target: src/main/java/free/svoss/tools/v2ray/XrayCoreManager.java
 */
class XrayCoreManagerTest {

    @Test
    void windows64bit() {
        assertEquals("Xray-windows-64.zip", XrayCoreManager.assetNameFor("Windows 10", "amd64"));
        assertEquals("Xray-windows-64.zip", XrayCoreManager.assetNameFor("Windows 11", "x86_64"));
    }

    @Test
    void windows32bit() {
        assertEquals("Xray-windows-32.zip", XrayCoreManager.assetNameFor("Windows 10", "x86"));
        assertEquals("Xray-windows-32.zip", XrayCoreManager.assetNameFor("Windows 10", "i686"));
    }

    @Test
    void windowsArm64() {
        assertEquals("Xray-windows-arm64-v8a.zip", XrayCoreManager.assetNameFor("Windows 11", "aarch64"));
    }

    @Test
    void macosIntel() {
        assertEquals("Xray-macos-64.zip", XrayCoreManager.assetNameFor("Mac OS X", "x86_64"));
        assertEquals("Xray-macos-64.zip", XrayCoreManager.assetNameFor("macOS", "i386"));
    }

    @Test
    void macosArm() {
        assertEquals("Xray-macos-arm64-v8a.zip", XrayCoreManager.assetNameFor("Mac OS X", "aarch64"));
        assertEquals("Xray-macos-arm64-v8a.zip", XrayCoreManager.assetNameFor("Mac OS X", "arm64"));
    }

    @Test
    void linux64bit() {
        assertEquals("Xray-linux-64.zip", XrayCoreManager.assetNameFor("Linux", "amd64"));
        assertEquals("Xray-linux-64.zip", XrayCoreManager.assetNameFor("Linux", "x86_64"));
    }

    @Test
    void linux32bit() {
        assertEquals("Xray-linux-32.zip", XrayCoreManager.assetNameFor("Linux", "i686"));
        assertEquals("Xray-linux-32.zip", XrayCoreManager.assetNameFor("Linux", "x86"));
    }

    @Test
    void linuxArm() {
        assertEquals("Xray-linux-arm64-v8a.zip", XrayCoreManager.assetNameFor("Linux", "aarch64"));
        assertEquals("Xray-linux-arm64-v8a.zip", XrayCoreManager.assetNameFor("Linux", "arm64"));
        assertEquals("Xray-linux-arm32-v7a.zip", XrayCoreManager.assetNameFor("Linux", "armv7l"));
        assertEquals("Xray-linux-arm32-v7a.zip", XrayCoreManager.assetNameFor("Linux", "arm"));
    }

    @Test
    void linuxExoticArchitectures() {
        assertEquals("Xray-linux-riscv64.zip", XrayCoreManager.assetNameFor("Linux", "riscv64"));
        assertEquals("Xray-linux-ppc64le.zip", XrayCoreManager.assetNameFor("Linux", "ppc64le"));
        assertEquals("Xray-linux-ppc64.zip", XrayCoreManager.assetNameFor("Linux", "ppc64"));
        assertEquals("Xray-linux-s390x.zip", XrayCoreManager.assetNameFor("Linux", "s390x"));
        assertEquals("Xray-linux-loong64.zip", XrayCoreManager.assetNameFor("Linux", "loongarch64"));
        assertEquals("Xray-linux-mips64le.zip", XrayCoreManager.assetNameFor("Linux", "mips64le"));
        assertEquals("Xray-linux-mips64.zip", XrayCoreManager.assetNameFor("Linux", "mips64"));
        assertEquals("Xray-linux-mips32le.zip", XrayCoreManager.assetNameFor("Linux", "mipsel"));
        assertEquals("Xray-linux-mips32.zip", XrayCoreManager.assetNameFor("Linux", "mips"));
    }

    @Test
    void freebsd() {
        assertEquals("Xray-freebsd-64.zip", XrayCoreManager.assetNameFor("FreeBSD", "amd64"));
        assertEquals("Xray-freebsd-32.zip", XrayCoreManager.assetNameFor("FreeBSD", "i386"));
        assertEquals("Xray-freebsd-arm64-v8a.zip", XrayCoreManager.assetNameFor("FreeBSD", "aarch64"));
        assertEquals("Xray-freebsd-arm32-v7a.zip", XrayCoreManager.assetNameFor("FreeBSD", "armv7l"));
    }

    @Test
    void openbsd() {
        assertEquals("Xray-openbsd-64.zip", XrayCoreManager.assetNameFor("OpenBSD", "amd64"));
        assertEquals("Xray-openbsd-arm64-v8a.zip", XrayCoreManager.assetNameFor("OpenBSD", "aarch64"));
    }
}