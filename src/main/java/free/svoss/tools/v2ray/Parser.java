package free.svoss.tools.v2ray;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static free.svoss.tools.v2ray.Util.isEmpty;

/**
 * Parses v2ray subscription content (vmess, vless, trojan, shadowsocks URLs) into {@link ServerConfig} instances.
 * Handles both plain-text and base64-encoded subscription formats.
 */
public final class Parser {

    private Parser() {
    }

    /**
     * Parses a subscription string into a set of {@link ServerConfig}.
     * <p>
     * The input may be either plain text (one server URL per line) or a single
     * base64-encoded blob that decodes to such text. Both padded/URL-safe base64
     * variants are accepted.
     */
    public static Set<ServerConfig> parse(String input) {
        String collapsed = input.replaceAll("\\s+", "");
        String text;
        if (collapsed.contains("://"))
            text = input; else {
            try {
                text = base64Decode(collapsed);
            } catch (IllegalArgumentException e) {
                System.err.println("Failed to decode base64 subscription content: " + e.getMessage());
                return new LinkedHashSet<>();
            }
        }

        Set<ServerConfig> result = new LinkedHashSet<>();
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty())
                continue;
            try {
                ServerConfig cfg = parseLine(trimmed);
                if (cfg != null)
                    result.add(cfg);
            } catch (RuntimeException e) {
                System.err.println("Warning: Skipping malformed line: " + e.getMessage());
            }
        }
        return result;
    }

    private static ServerConfig parseLine(String line) {
        if (line.startsWith("vmess://"))
            return parseVmess(line.substring("vmess://".length()), line);
        if (line.startsWith("vless://"))
            return parseUriBased("vless", line.substring("vless://".length()), line);
        if (line.startsWith("trojan://"))
            return parseUriBased("trojan", line.substring("trojan://".length()), line);
        if (line.startsWith("ss://"))
            return parseSs(line.substring("ss://".length()), line);
        return null;
    }

    private static ServerConfig parseVmess(String payload, String rawUrl) {
        String json = base64Decode(payload);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        ServerConfig.Builder b = ServerConfig.builder()
                .protocol("vmess")
                .address(stringField(obj, "add"))
                .port(intField(obj, "port"))
                .id(stringField(obj, "id"))
                .alterId(intField(obj, "aid"))
                .rawUrl(rawUrl);

        String securityVal = null;
        String scyVal = null;
        String tlsVal = null;

        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            String key = e.getKey();
            String value = e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString();
            switch (key) {
                case "add": case "port": case "id": case "aid":
                    break;
                case "net":
                    b.transport(value);
                    break;
                case "host":
                    b.host(value);
                    break;
                case "path":
                    b.path(value);
                    break;
                case "sni":
                    b.sni(value);
                    break;
                case "alpn":
                    b.alpn(value);
                    break;
                case "fp":
                    b.fingerprint(value);
                    break;
                case "flow":
                    b.flow(value);
                    break;
                case "security":
                    securityVal = value;
                    break;
                case "scy":
                    scyVal = value;
                    break;
                case "tls":
                    tlsVal = value;
                    break;
                case "ps":
                case "remark":
                    b.remark(value);
                    break;
                case "type":
                    if (value.isEmpty() || "none".equals(value.toLowerCase(Locale.ROOT)))
                        b.extra(key, value);
                    else
                        b.transport(value);
                    break;
                default:
                    b.extra(key, value);
            }
        }

        String security = securityVal;
        if (isEmpty(security)) {
        if (!isEmpty(scyVal) && !"auto".equals(scyVal.toLowerCase(Locale.ROOT)))
            security = scyVal;
        else if (!isEmpty(tlsVal) && !"false".equals(tlsVal.toLowerCase(Locale.ROOT)))
            security = tlsVal;
        }
        b.security(security);

        return b.build();
    }

    private static ServerConfig parseUriBased(String protocol, String rest, String rawUrl) {
        int hashIdx = rest.indexOf('#');
        String main = hashIdx >= 0 ? rest.substring(0, hashIdx) : rest;
        String remark = hashIdx >= 0 ? urlDecode(rest.substring(hashIdx + 1)) : null;

        int atIdx = main.indexOf('@');
        if (atIdx < 0)
            throw new IllegalArgumentException("missing '@' in " + protocol + " url");
        String id = urlDecode(main.substring(0, atIdx));
        String hostPortQuery = main.substring(atIdx + 1);

        int qIdx = hostPortQuery.indexOf('?');
        String hostPort = qIdx >= 0 ? hostPortQuery.substring(0, qIdx) : hostPortQuery;
        String query = qIdx >= 0 ? hostPortQuery.substring(qIdx + 1) : null;

        HostPort hp = parseHostPort(hostPort);
        Map<String, String> params = parseQuery(query);

        ServerConfig.Builder b = ServerConfig.builder()
                .protocol(protocol)
                .address(hp.host)
                .port(hp.port)
                .id(id)
                .remark(remark)
                .rawUrl(rawUrl);

        for (Map.Entry<String, String> e : params.entrySet()) {
            switch (e.getKey()) {
                case "security": b.security(e.getValue()); break;
                case "encryption": b.encryption(e.getValue()); break;
                case "type": b.transport(e.getValue()); break;
                case "path": b.path(e.getValue()); break;
                case "host": b.host(e.getValue()); break;
                case "sni": b.sni(e.getValue()); break;
                case "flow": b.flow(e.getValue()); break;
                case "fp": b.fingerprint(e.getValue()); break;
                case "alpn": b.alpn(e.getValue()); break;
                case "pbk": b.pbk(e.getValue()); break;
                case "sid": b.sid(e.getValue()); break;
                case "packetEncoding": b.packetEncoding(e.getValue()); break;
                default: b.extra(e.getKey(), e.getValue());
            }
        }
        return b.build();
    }

    private static ServerConfig parseSs(String rest, String rawUrl) {
        int hashIdx = rest.indexOf('#');
        String main = hashIdx >= 0 ? rest.substring(0, hashIdx) : rest;
        String remark = hashIdx >= 0 ? urlDecode(rest.substring(hashIdx + 1)) : null;

        String creds;
        String host;
        int port;
        int atIdx = main.indexOf('@');
        if (atIdx >= 0) {
            creds = base64Decode(main.substring(0, atIdx));
            String hp = main.substring(atIdx + 1);
            int colon = hp.lastIndexOf(':');
            host = hp.substring(0, colon);
            try {
                port = Integer.parseInt(hp.substring(colon + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid port in ss url: " + hp.substring(colon + 1));
            }
        } else {
            String decoded = base64Decode(main);
            int at2 = decoded.indexOf('@');
            int colon = decoded.lastIndexOf(':');
            creds = decoded.substring(0, at2);
            host = decoded.substring(at2 + 1, colon);
            try {
                port = Integer.parseInt(decoded.substring(colon + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid port in ss url: " + decoded.substring(colon + 1));
            }
        }

        int c = creds.indexOf(':');
        String method = c >= 0 ? creds.substring(0, c) : null;
        String password = c >= 0 ? creds.substring(c + 1) : creds;

        return ServerConfig.builder()
                .protocol("ss")
                .address(host)
                .port(port)
                .id(password)
                .encryption(method)
                .remark(remark)
                .rawUrl(rawUrl)
                .build();
    }

    private static HostPort parseHostPort(String hostPort) {
        if (hostPort.startsWith("[")) {
            int end = hostPort.indexOf(']');
            if (end < 0)
                throw new IllegalArgumentException("invalid IPv6 host: " + hostPort);
            String host = hostPort.substring(1, end);
            String rest = hostPort.substring(end + 1);
            int port = rest.startsWith(":") ? Integer.parseInt(rest.substring(1)) : 443;
            return new HostPort(host, port);
        }
        int colon = hostPort.lastIndexOf(':');
        if (colon < 0)
            return new HostPort(hostPort, 443);
        return new HostPort(hostPort.substring(0, colon), Integer.parseInt(hostPort.substring(colon + 1)));
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (isEmpty(query))
            return params;
        for (String pair : query.split("&")) {
            if (pair.isEmpty())
                continue;
            int eq = pair.indexOf('=');
            if (eq < 0)
                params.put(urlDecode(pair), "");
            else
                params.put(urlDecode(pair.substring(0, eq)), urlDecode(pair.substring(eq + 1)));
        }
        return params;
    }

    private static String stringField(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull())
            return null;
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    private static int intField(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull())
            return 0;
        try {
            return e.getAsInt();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static String base64Decode(String s) {
        String b64 = s.replace('-', '+').replace('_', '/');
        int pad = (4 - b64.length() % 4) % 4;
        StringBuilder sb = new StringBuilder(b64);
        for (int i = 0; i < pad; i++) {
            sb.append('=');
        }
        byte[] bytes = Base64.getDecoder().decode(sb.toString());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            return s; // UTF-8 is always supported
        }
    }

    private static final class HostPort {
        final String host;
        final int port;

        HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
