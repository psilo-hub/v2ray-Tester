package free.svoss.tools.v2ray;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigWithTestResultTest {

    private ServerConfig makeConfig() {
        return new ServerConfig.Builder()
                .protocol("vmess")
                .address("1.2.3.4")
                .port(443)
                .id("00000000-0000-0000-0000-000000000001")
                .remark("test-server")
                .build();
    }

    @Test
    void constructFromServerConfigRoundTrips() {
        ServerConfig original = makeConfig();
        ServerConfigWithTestResult wrapper = new ServerConfigWithTestResult(original);
        Optional<ServerConfig> roundTripped = wrapper.getServerConfig();
        assertTrue(roundTripped.isPresent());
        assertEquals(original, roundTripped.get());
    }

    @Test
    void constructFromJsonStringParsesCorrectly() {
        ServerConfig original = makeConfig();
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().serializeNulls().create();
        String json = gson.toJson(original, ServerConfig.class);
        ServerConfigWithTestResult wrapper = new ServerConfigWithTestResult(json);
        assertTrue(wrapper.getServerConfig().isPresent());
        assertEquals(original, wrapper.getServerConfig().get());
    }

    @Test
    void constructFromNullJsonReturnsEmptyOptional() {
        ServerConfigWithTestResult wrapper = new ServerConfigWithTestResult((String) null);
        assertFalse(wrapper.getServerConfig().isPresent());
    }

    @Test
    void pingInitiallyNull() {
        ServerConfigWithTestResult wrapper = new ServerConfigWithTestResult(makeConfig());
        assertNull(wrapper.getPing());
    }

    @Test
    void speedInitiallyNull() {
        ServerConfigWithTestResult wrapper = new ServerConfigWithTestResult(makeConfig());
        assertNull(wrapper.getSpeedMbPerSecond());
    }

    @Test
    void setAndGetPing() {
        ServerConfigWithTestResult wrapper = new ServerConfigWithTestResult(makeConfig());
        wrapper.setPing(42);
        assertEquals(42, wrapper.getPing());
    }

    @Test
    void setAndGetSpeed() {
        ServerConfigWithTestResult wrapper = new ServerConfigWithTestResult(makeConfig());
        wrapper.setSpeedMbPerSecond(12.5);
        assertEquals(12.5, wrapper.getSpeedMbPerSecond());
    }

    @Test
    void sameConfigJsonAreEqual() {
        ServerConfig original = makeConfig();
        ServerConfigWithTestResult a = new ServerConfigWithTestResult(original);
        ServerConfigWithTestResult b = new ServerConfigWithTestResult(original);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentConfigsAreNotEqual() {
        ServerConfigWithTestResult a = new ServerConfigWithTestResult(makeConfig());
        ServerConfig other = new ServerConfig.Builder()
                .protocol("vless")
                .address("5.6.7.8")
                .port(8443)
                .id("00000000-0000-0000-0000-000000000002")
                .build();
        ServerConfigWithTestResult b = new ServerConfigWithTestResult(other);
        assertNotEquals(a, b);
    }

    @Test
    void equalsReflexive() {
        ServerConfigWithTestResult a = new ServerConfigWithTestResult(makeConfig());
        assertEquals(a, a);
    }

    @Test
    void equalsHandlesNull() {
        ServerConfigWithTestResult a = new ServerConfigWithTestResult(makeConfig());
        assertNotEquals(null, a);
    }
}
