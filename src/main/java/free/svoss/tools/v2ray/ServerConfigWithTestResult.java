package free.svoss.tools.v2ray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Objects;
import java.util.Optional;

/**
 * Wraps a {@link ServerConfig} with test results (ping and download speed).
 * Used during the testing pipeline to track per-server performance.
 */
public class ServerConfigWithTestResult {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final String serverConfigJson;
    private volatile Integer ping;
    private volatile Double speedMbPerSecond;
    private volatile ServerConfig cachedServerConfig;

    public ServerConfigWithTestResult(String serverConfigJson) {
        this.serverConfigJson = serverConfigJson;
        this.cachedServerConfig = gson.fromJson(serverConfigJson, ServerConfig.class);
    }

    public ServerConfigWithTestResult(ServerConfig serverConfig) {
        this.serverConfigJson = gson.toJson(serverConfig, ServerConfig.class);
        this.cachedServerConfig = serverConfig;
    }

    public Optional<ServerConfig> getServerConfig() {
        return Optional.ofNullable(cachedServerConfig);
    }

    public Integer getPing() {
        return ping;
    }

    public void setPing(Integer ping) {
        this.ping = ping;
    }

    public Double getSpeedMbPerSecond() {
        return speedMbPerSecond;
    }

    public void setSpeedMbPerSecond(Double speedMbPerSecond) {
        this.speedMbPerSecond = speedMbPerSecond;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerConfigWithTestResult)) return false;
        ServerConfigWithTestResult that = (ServerConfigWithTestResult) o;
        return Objects.equals(serverConfigJson, that.serverConfigJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverConfigJson);
    }
}
