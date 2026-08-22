package free.svoss.tools.v2ray;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Hysteria2ParserTest {

    @Test
    void parseMinimalHysteria2() {
        String url = "hysteria2://letmein@example.com:8443";
        Set<ServerConfig> configs = Parser.parse(url);
        assertEquals(1, configs.size());
        ServerConfig cfg = configs.iterator().next();
        assertEquals("hysteria2", cfg.getProtocol());
        assertEquals("letmein", cfg.getId());
        assertEquals("example.com", cfg.getAddress());
        assertEquals(8443, cfg.getPort());
    }

    @Test
    void defaultPortIs443() {
        ServerConfig cfg = Parser.parse("hysteria2://pw@1.2.3.4").iterator().next();
        assertEquals(443, cfg.getPort());
    }

    @Test
    void hy2AliasParsesIdentically() {
        ServerConfig cfg = Parser.parse("hy2://pw@5.6.7.8:36712").iterator().next();
        assertEquals("hysteria2", cfg.getProtocol());
        assertEquals("pw", cfg.getId());
        assertEquals("5.6.7.8", cfg.getAddress());
        assertEquals(36712, cfg.getPort());
    }

    @Test
    void parseFullParams() {
        String url = "hysteria2://pw@example.com:443?sni=example.com&insecure=1"
                + "&obfs=salamander&obfs-password=obfspass&pinSHA256=AABBCC#My%20Node";
        ServerConfig cfg = Parser.parse(url).iterator().next();
        assertEquals("example.com", cfg.getSni());
        assertEquals("My Node", cfg.getRemark());
        assertEquals("1", cfg.getExtra().get("insecure"));
        assertEquals("salamander", cfg.getExtra().get("obfs"));
        assertEquals("obfspass", cfg.getExtra().get("obfs-password"));
        assertEquals("AABBCC", cfg.getExtra().get("pinSHA256"));
        assertNull(cfg.getSecurity());
        assertNull(cfg.getTransport());
    }

    @Test
    void allowInsecureAliasGoesToExtra() {
        ServerConfig cfg = Parser.parse("hysteria2://pw@1.2.3.4?allowInsecure=1").iterator().next();
        assertEquals("1", cfg.getExtra().get("allowInsecure"));
    }

    @Test
    void parseIpv6Host() {
        ServerConfig cfg = Parser.parse("hy2://pw@[2001:db8::1]:8443").iterator().next();
        assertEquals("2001:db8::1", cfg.getAddress());
        assertEquals(8443, cfg.getPort());
    }

    @Test
    void missingAtSignFailsGracefully() {
        List<String> failures = new ArrayList<>();
        Set<ServerConfig> configs = Parser.parse("hysteria2://no-at-sign.example.com:443", failures);
        assertTrue(configs.isEmpty());
        assertFalse(failures.isEmpty());
    }

    @Test
    void badPortFailsGracefully() {
        List<String> failures = new ArrayList<>();
        Set<ServerConfig> configs = Parser.parse("hysteria2://pw@1.2.3.4:notaport", failures);
        assertTrue(configs.isEmpty());
        assertFalse(failures.isEmpty());
    }

    @Test
    void emptyAuthFailsGracefully() {
        List<String> failures = new ArrayList<>();
        Set<ServerConfig> configs = Parser.parse("hysteria2://@1.2.3.4:443", failures);
        assertTrue(configs.isEmpty());
        assertFalse(failures.isEmpty());
    }

    @Test
    void portHoppingKeepsFirstPortAndPreservesList() {
        String url = "hysteria2://pw@example.com:443,5000-6000";
        ServerConfig cfg = Parser.parse(url).iterator().next();
        assertEquals(443, cfg.getPort());
        assertEquals("example.com:443,5000-6000", cfg.getExtra().get("ports"));
    }

    @Test
    void mixedSubscriptionWithTrojan() {
        String input = "trojan://pw@1.2.3.4:443\nhy2://pw@5.6.7.8:36712";
        Set<ServerConfig> configs = Parser.parse(input);
        assertEquals(2, configs.size());
    }

    @Test
    void rawUrlRoundTrip() {
        String url = "hysteria2://letmein@example.com:8443?sni=example.com#Node";
        ServerConfig cfg = Parser.parse(url).iterator().next();
        assertEquals(url, cfg.getRawUrl());
    }
}
