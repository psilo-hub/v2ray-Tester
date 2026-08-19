package free.svoss.tools.v2ray;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Locale;

import static free.svoss.tools.v2ray.Util.isEmpty;

/**
 * Builds Xray JSON configuration from a {@link ServerConfig}.
 */
final class XrayConfigBuilder {

    private XrayConfigBuilder() {
    }

    static String buildConfig(ServerConfig cfg, int socksPort) {
        JsonObject config = new JsonObject();

        JsonObject log = new JsonObject();
        log.addProperty("loglevel", "warning");
        config.add("log", log);

        JsonObject inbound = new JsonObject();
        inbound.addProperty("listen", "127.0.0.1");
        inbound.addProperty("port", socksPort);
        inbound.addProperty("protocol", "socks");
        JsonObject inboundSettings = new JsonObject();
        inboundSettings.addProperty("udp", false);
        inbound.add("settings", inboundSettings);
        inbound.addProperty("tag", "speed-in");
        JsonArray inbounds = new JsonArray();
        inbounds.add(inbound);
        config.add("inbounds", inbounds);

        JsonObject outbound = new JsonObject();
        String protocol = cfg.getProtocol();
        JsonObject settings = new JsonObject();
        if ("vless".equals(protocol)) {
            outbound.addProperty("protocol", "vless");
            settings.add("vnext", vnext(cfg, false));
        } else if ("vmess".equals(protocol)) {
            outbound.addProperty("protocol", "vmess");
            settings.add("vnext", vnext(cfg, true));
        } else if ("trojan".equals(protocol)) {
            outbound.addProperty("protocol", "trojan");
            JsonObject server = new JsonObject();
            server.addProperty("address", cfg.getAddress());
            server.addProperty("port", cfg.getPort());
            server.addProperty("password", cfg.getId());
            JsonArray servers = new JsonArray();
            servers.add(server);
            settings.add("servers", servers);
        } else if ("ss".equals(protocol)) {
            outbound.addProperty("protocol", "shadowsocks");
            JsonObject server = new JsonObject();
            server.addProperty("address", cfg.getAddress());
            server.addProperty("port", cfg.getPort());
            String method = isEmpty(cfg.getEncryption()) ? "aes-256-gcm" : cfg.getEncryption();
            server.addProperty("method", method);
            server.addProperty("password", cfg.getId());
            JsonArray servers = new JsonArray();
            servers.add(server);
            settings.add("servers", servers);
        } else
            return null;
        outbound.add("settings", settings);
        outbound.add("streamSettings", buildStreamSettings(cfg));

        JsonArray outbounds = new JsonArray();
        outbounds.add(outbound);
        config.add("outbounds", outbounds);

        return config.toString();
    }

    private static JsonArray vnext(ServerConfig cfg, boolean vmess) {
        JsonObject vnext = new JsonObject();
        vnext.addProperty("address", cfg.getAddress());
        vnext.addProperty("port", cfg.getPort());
        JsonObject user = new JsonObject();
        user.addProperty("id", cfg.getId());
        if (vmess) {
            user.addProperty("alterId", cfg.getAlterId());
            String security = isEmpty(cfg.getSecurity()) ? "auto" : cfg.getSecurity();
            user.addProperty("security", security);
        } else {
            String encryption = isEmpty(cfg.getEncryption()) ? "none" : cfg.getEncryption();
            user.addProperty("encryption", encryption);
            if (!isEmpty(cfg.getFlow()))
                user.addProperty("flow", cfg.getFlow());
        }
        JsonArray users = new JsonArray();
        users.add(user);
        vnext.add("users", users);
        JsonArray arr = new JsonArray();
        arr.add(vnext);
        return arr;
    }

    static JsonObject buildStreamSettings(ServerConfig cfg) {
        JsonObject stream = new JsonObject();

        String network = isEmpty(cfg.getTransport()) ? "tcp" : cfg.getTransport().toLowerCase(Locale.ROOT);
        if (!"ws".equals(network) && !"grpc".equals(network) && !"h2".equals(network))
            network = "tcp";
        stream.addProperty("network", network);

        String security = isEmpty(cfg.getSecurity()) ? "none" : cfg.getSecurity().toLowerCase(Locale.ROOT);
        if ("reality".equals(security)) {
            stream.addProperty("security", "reality");
            JsonObject reality = new JsonObject();
            if (!isEmpty(cfg.getSni()))
                reality.addProperty("serverName", cfg.getSni());
            if (!isEmpty(cfg.getFingerprint()))
                reality.addProperty("fingerprint", cfg.getFingerprint());
            if (!isEmpty(cfg.getPbk()))
                reality.addProperty("publicKey", cfg.getPbk());
            if (!isEmpty(cfg.getSid()))
                reality.addProperty("shortId", cfg.getSid());
            reality.addProperty("spiderX", "/");
            stream.add("realitySettings", reality);
        } else if ("tls".equals(security)) {
            stream.addProperty("security", "tls");
            JsonObject tls = new JsonObject();
            if (!isEmpty(cfg.getSni()))
                tls.addProperty("serverName", cfg.getSni());
            if (!isEmpty(cfg.getFingerprint()))
                tls.addProperty("fingerprint", cfg.getFingerprint());
            if (!isEmpty(cfg.getAlpn())) {
                JsonArray alpn = new JsonArray();
                for (String s : cfg.getAlpn().split(",")) {
                    if (!s.trim().isEmpty())
                        alpn.add(s.trim());
                }
                tls.add("alpn", alpn);
            }
            stream.add("tlsSettings", tls);
        } else
            stream.addProperty("security", "none");

        if ("ws".equals(network)) {
            JsonObject ws = new JsonObject();
            if (!isEmpty(cfg.getPath()))
                ws.addProperty("path", cfg.getPath());
            if (!isEmpty(cfg.getHost())) {
                JsonObject headers = new JsonObject();
                headers.addProperty("Host", cfg.getHost());
                ws.add("headers", headers);
            }
            stream.add("wsSettings", ws);
        } else if ("grpc".equals(network)) {
            JsonObject grpc = new JsonObject();
            if (!isEmpty(cfg.getPath()))
                grpc.addProperty("serviceName", cfg.getPath());
            stream.add("grpcSettings", grpc);
        }

        return stream;
    }
}
