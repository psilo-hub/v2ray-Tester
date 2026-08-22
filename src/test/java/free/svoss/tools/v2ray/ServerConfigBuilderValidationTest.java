package free.svoss.tools.v2ray;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigBuilderValidationTest {

    private static ServerConfig.Builder baseBuilder() {
        return ServerConfig.builder()
                .protocol("vmess")
                .address("1.2.3.4")
                .port(443)
                .id("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void validConfigBuildsSuccessfully() {
        assertDoesNotThrow(() -> baseBuilder().build());
    }

    @Test
    void nullAddressThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> baseBuilder().address(null).build());
        assertTrue(ex.getMessage().contains("address"));
    }

    @Test
    void blankAddressThrows() {
        assertThrows(IllegalStateException.class,
                () -> baseBuilder().address("   ").build());
    }

    @Test
    void emptyAddressThrows() {
        assertThrows(IllegalStateException.class,
                () -> baseBuilder().address("").build());
    }

    @Test
    void portTooLowThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> baseBuilder().port(0).build());
        assertTrue(ex.getMessage().contains("port"));
    }

    @Test
    void portTooHighThrows() {
        assertThrows(IllegalStateException.class,
                () -> baseBuilder().port(65536).build());
    }

    @Test
    void negativePortThrows() {
        assertThrows(IllegalStateException.class,
                () -> baseBuilder().port(-1).build());
    }

    @Test
    void nullProtocolThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> baseBuilder().protocol(null).build());
        assertTrue(ex.getMessage().contains("protocol"));
    }

    @Test
    void invalidProtocolThrows() {
        assertThrows(IllegalStateException.class,
                () -> baseBuilder().protocol("wireguard").build());
    }

    @Test
    void nullIdThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> baseBuilder().id(null).build());
        assertTrue(ex.getMessage().contains("id"));
    }

    @Test
    void blankIdThrows() {
        assertThrows(IllegalStateException.class,
                () -> baseBuilder().id("   ").build());
    }

    @Test
    void vmessNonUuidIdThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> baseBuilder().protocol("vmess").id("not-a-uuid").build());
        assertTrue(ex.getMessage().contains("UUID"));
    }

    @Test
    void vlessNonUuidIdAccepted() {
        assertDoesNotThrow(() -> baseBuilder().protocol("vless").id("not-a-uuid").build());
    }

    @Test
    void trojanPasswordIdAccepted() {
        assertDoesNotThrow(() ->
                baseBuilder().protocol("trojan").id("my-password-123").build());
    }

    @Test
    void ssPasswordIdAccepted() {
        assertDoesNotThrow(() ->
                baseBuilder().protocol("ss").id("aes-256-gcm:password").build());
    }

    @Test
    void validProtocolsAccepted() {
        for (String proto : new String[]{"vmess", "vless", "trojan", "ss", "hysteria2"}) {
            String id = (proto.equals("vmess") || proto.equals("vless"))
                    ? "550e8400-e29b-41d4-a716-446655440000"
                    : "password";
            assertDoesNotThrow(() ->
                    baseBuilder().protocol(proto).id(id).build());
        }
    }

    @Test
    void boundaryPortsAccepted() {
        assertDoesNotThrow(() -> baseBuilder().port(1).build());
        assertDoesNotThrow(() -> baseBuilder().port(65535).build());
    }
}
