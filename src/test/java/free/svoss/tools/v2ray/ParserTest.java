package free.svoss.tools.v2ray;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    private static final String VALID_UUID = "550e8400-e29b-41d4-a716-446655440000";

    private static String base64Encode(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    // --- vmess ---

    @Test
    void parseMinimalVmess() {
        String json = "{\"v\":\"2\",\"add\":\"1.2.3.4\",\"port\":\"443\","
                + "\"id\":\"" + VALID_UUID + "\",\"aid\":\"0\","
                + "\"net\":\"tcp\",\"type\":\"none\",\"host\":\"\",\"path\":\"\","
                + "\"tls\":\"\",\"sni\":\"\"}";
        String url = "vmess://" + base64Encode(json);
        Set<ServerConfig> configs = Parser.parse(url);
        assertEquals(1, configs.size());
        ServerConfig cfg = configs.iterator().next();
        assertEquals("vmess", cfg.getProtocol());
        assertEquals("1.2.3.4", cfg.getAddress());
        assertEquals(443, cfg.getPort());
        assertEquals(VALID_UUID, cfg.getId());
        assertEquals("tcp", cfg.getTransport());
    }

    @Test
    void parseVmessWithTls() {
        String json = "{\"v\":\"2\",\"add\":\"example.com\",\"port\":\"443\","
                + "\"id\":\"" + VALID_UUID + "\",\"aid\":\"0\","
                + "\"net\":\"ws\",\"type\":\"none\",\"host\":\"example.com\","
                + "\"path\":\"/ws\",\"tls\":\"tls\",\"sni\":\"example.com\","
                + "\"fp\":\"chrome\",\"alpn\":\"h2\"}";
        String url = "vmess://" + base64Encode(json);
        Set<ServerConfig> configs = Parser.parse(url);
        ServerConfig cfg = configs.iterator().next();
        assertEquals("tls", cfg.getSecurity());
        assertEquals("ws", cfg.getTransport());
        assertEquals("/ws", cfg.getPath());
        assertEquals("example.com", cfg.getHost());
        assertEquals("example.com", cfg.getSni());
        assertEquals("chrome", cfg.getFingerprint());
        assertEquals("h2", cfg.getAlpn());
    }

    @Test
    void parseVmessWithRemark() {
        String json = "{\"v\":\"2\",\"add\":\"1.2.3.4\",\"port\":\"443\","
                + "\"id\":\"" + VALID_UUID + "\",\"aid\":\"0\","
                + "\"net\":\"tcp\",\"type\":\"none\",\"host\":\"\",\"path\":\"\","
                + "\"tls\":\"\",\"sni\":\"\",\"ps\":\"My Server\"}";
        String url = "vmess://" + base64Encode(json);
        ServerConfig cfg = Parser.parse(url).iterator().next();
        assertEquals("My Server", cfg.getRemark());
    }

    @Test
    void parseVmessWithAlterId() {
        String json = "{\"v\":\"2\",\"add\":\"1.2.3.4\",\"port\":\"443\","
                + "\"id\":\"" + VALID_UUID + "\",\"aid\":\"2\","
                + "\"net\":\"tcp\",\"type\":\"none\"}";
        String url = "vmess://" + base64Encode(json);
        ServerConfig cfg = Parser.parse(url).iterator().next();
        assertEquals(2, cfg.getAlterId());
    }

    // --- vless ---

    @Test
    void parseMinimalVless() {
        String url = "vless://" + VALID_UUID + "@1.2.3.4:443";
        Set<ServerConfig> configs = Parser.parse(url);
        assertEquals(1, configs.size());
        ServerConfig cfg = configs.iterator().next();
        assertEquals("vless", cfg.getProtocol());
        assertEquals(VALID_UUID, cfg.getId());
        assertEquals("1.2.3.4", cfg.getAddress());
        assertEquals(443, cfg.getPort());
    }

    @Test
    void parseVlessWithQueryParams() {
        String url = "vless://" + VALID_UUID + "@example.com:443?security=tls&type=ws"
                + "&path=/vless&host=example.com&sni=example.com&fp=chrome#My%20Vless";
        ServerConfig cfg = Parser.parse(url).iterator().next();
        assertEquals("tls", cfg.getSecurity());
        assertEquals("ws", cfg.getTransport());
        assertEquals("/vless", cfg.getPath());
        assertEquals("example.com", cfg.getHost());
        assertEquals("example.com", cfg.getSni());
        assertEquals("chrome", cfg.getFingerprint());
        assertEquals("My Vless", cfg.getRemark());
    }

    @Test
    void parseVlessWithReality() {
        String url = "vless://" + VALID_UUID + "@example.com:443?security=reality"
                + "&sni=example.com&fp=chrome&pbk=abc123&sid=def456&flow=xtls-rprx-vision";
        ServerConfig cfg = Parser.parse(url).iterator().next();
        assertEquals("reality", cfg.getSecurity());
        assertEquals("abc123", cfg.getPbk());
        assertEquals("def456", cfg.getSid());
        assertEquals("xtls-rprx-vision", cfg.getFlow());
    }

    // --- trojan ---

    @Test
    void parseMinimalTrojan() {
        String url = "trojan://password123@1.2.3.4:443";
        Set<ServerConfig> configs = Parser.parse(url);
        assertEquals(1, configs.size());
        ServerConfig cfg = configs.iterator().next();
        assertEquals("trojan", cfg.getProtocol());
        assertEquals("password123", cfg.getId());
        assertEquals("1.2.3.4", cfg.getAddress());
        assertEquals(443, cfg.getPort());
    }

    @Test
    void parseTrojanWithParams() {
        String url = "trojan://pw@example.com:443?security=tls&type=grpc"
                + "&sni=example.com&fp=chrome&alpn=h2#Trojan%20GRPC";
        ServerConfig cfg = Parser.parse(url).iterator().next();
        assertEquals("tls", cfg.getSecurity());
        assertEquals("grpc", cfg.getTransport());
        assertEquals("example.com", cfg.getSni());
        assertEquals("Trojan GRPC", cfg.getRemark());
    }

    // --- shadowsocks ---

    @Test
    void parseSsWithExplicitAt() {
        String creds = base64Encode("aes-256-gcm:mypassword");
        String url = "ss://" + creds + "@1.2.3.4:8388";
        Set<ServerConfig> configs = Parser.parse(url);
        assertEquals(1, configs.size());
        ServerConfig cfg = configs.iterator().next();
        assertEquals("ss", cfg.getProtocol());
        assertEquals("1.2.3.4", cfg.getAddress());
        assertEquals(8388, cfg.getPort());
        assertEquals("aes-256-gcm", cfg.getEncryption());
        assertEquals("mypassword", cfg.getId());
    }

    @Test
    void parseSsFullyEncoded() {
        String inner = "aes-256-gcm:mypassword@1.2.3.4:8388";
        String url = "ss://" + base64Encode(inner);
        Set<ServerConfig> configs = Parser.parse(url);
        ServerConfig cfg = configs.iterator().next();
        assertEquals("ss", cfg.getProtocol());
        assertEquals("1.2.3.4", cfg.getAddress());
        assertEquals(8388, cfg.getPort());
    }

    @Test
    void parseSsWithRemark() {
        String creds = base64Encode("aes-256-gcm:mypassword");
        String url = "ss://" + creds + "@1.2.3.4:8388#My%20SS";
        ServerConfig cfg = Parser.parse(url).iterator().next();
        assertEquals("My SS", cfg.getRemark());
    }

    // --- base64 subscription ---

    @Test
    void parseBase64Subscription() {
        String line1 = "trojan://pw@1.2.3.4:443";
        String line2 = "ss://" + base64Encode("aes-256-gcm:pass@5.6.7.8:8388");
        String encoded = base64Encode(line1 + "\n" + line2);
        Set<ServerConfig> configs = Parser.parse(encoded);
        assertEquals(2, configs.size());
    }

    @Test
    void parseBase64WithUrlSafeChars() {
        String line = "trojan://pw@1.2.3.4:443";
        String encoded = Base64.getUrlEncoder().encodeToString(line.getBytes(StandardCharsets.UTF_8));
        Set<ServerConfig> configs = Parser.parse(encoded);
        assertEquals(1, configs.size());
    }

    // --- IPv6 ---

    @Test
    void parseVlessWithIpv6() {
        String url = "vless://" + VALID_UUID + "@[::1]:443";
        ServerConfig cfg = Parser.parse(url).iterator().next();
        assertEquals("::1", cfg.getAddress());
        assertEquals(443, cfg.getPort());
    }

    @Test
    void parseTrojanWithIpv6() {
        String url = "trojan://pw@[2001:db8::1]:8443?security=tls";
        ServerConfig cfg = Parser.parse(url).iterator().next();
        assertEquals("2001:db8::1", cfg.getAddress());
        assertEquals(8443, cfg.getPort());
    }

    // --- edge cases ---

    @Test
    void emptyInputReturnsEmptySet() {
        assertTrue(Parser.parse("").isEmpty());
    }

    @Test
    void blankLinesSkipped() {
        String url = "\n\ntrojan://pw@1.2.3.4:443\n\n";
        assertEquals(1, Parser.parse(url).size());
    }

    @Test
    void malformedVlessSkippedGracefully() {
        String input = "vless://no-at-sign\nvless://" + VALID_UUID + "@1.2.3.4:443";
        Set<ServerConfig> configs = Parser.parse(input);
        assertEquals(1, configs.size());
    }

    @Test
    void unknownProtocolReturnsEmpty() {
        String input = "wireguard://something";
        assertTrue(Parser.parse(input).isEmpty());
    }

    @Test
    void invalidBase64ReturnsEmptySet() {
        assertTrue(Parser.parse("!!!not-base64!!!").isEmpty());
    }

    @Test
    void multipleLinesParsed() {
        String input = "trojan://pw@1.1.1.1:443\n"
                + "ss://" + base64Encode("aes-256-gcm:pass@2.2.2.2:8388") + "\n"
                + "vmess://" + base64Encode("{\"v\":\"2\",\"add\":\"3.3.3.3\",\"port\":\"443\","
                + "\"id\":\"" + VALID_UUID + "\",\"aid\":\"0\"}");
        Set<ServerConfig> configs = Parser.parse(input);
        assertEquals(3, configs.size());
    }

    @Test
    void defaultPortIs443() {
        String url = "trojan://pw@1.2.3.4";
        ServerConfig cfg = Parser.parse(url).iterator().next();
        assertEquals(443, cfg.getPort());
    }

    @Test
    void nonDefaultPortParsed() {
        String url = "trojan://pw@1.2.3.4:8443";
        ServerConfig cfg = Parser.parse(url).iterator().next();
        assertEquals(8443, cfg.getPort());
    }

    @Test
    void rawUrlPreserved() {
        String url = "trojan://pw@1.2.3.4:443#Test";
        ServerConfig cfg = Parser.parse(url).iterator().next();
        assertEquals(url, cfg.getRawUrl());
    }
}
