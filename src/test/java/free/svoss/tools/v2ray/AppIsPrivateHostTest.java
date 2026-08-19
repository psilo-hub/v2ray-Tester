package free.svoss.tools.v2ray;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppIsPrivateHostTest {

    @Test
    void loopbackIpv4IsPrivate() {
        assertTrue(App.isPrivateHost("127.0.0.1"));
    }

    @Test
    void loopbackHostnameIsPrivate() {
        assertTrue(App.isPrivateHost("localhost"));
    }

    @Test
    void classAAddressesArePrivate() {
        assertTrue(App.isPrivateHost("10.0.0.1"));
        assertTrue(App.isPrivateHost("10.255.255.255"));
    }

    @Test
    void classBAddressesArePrivate() {
        assertTrue(App.isPrivateHost("172.16.0.1"));
        assertTrue(App.isPrivateHost("172.31.255.255"));
    }

    @Test
    void classCAddressesArePrivate() {
        assertTrue(App.isPrivateHost("192.168.0.1"));
        assertTrue(App.isPrivateHost("192.168.1.100"));
    }

    @Test
    void linkLocalAddressIsPrivate() {
        assertTrue(App.isPrivateHost("169.254.1.1"));
    }

    @Test
    void multicastAddressIsPrivate() {
        assertTrue(App.isPrivateHost("224.0.0.1"));
        assertTrue(App.isPrivateHost("239.255.255.250"));
    }

    @Test
    void publicAddressIsNotPrivate() {
        assertFalse(App.isPrivateHost("8.8.8.8"));
    }

    @Test
    void publicHostnameIsNotPrivate() {
        assertFalse(App.isPrivateHost("example.com"));
    }

    @Test
    void unknownHostReturnsFalse() {
        assertFalse(App.isPrivateHost("this.host.does.not.exist.invalid"));
    }
}
