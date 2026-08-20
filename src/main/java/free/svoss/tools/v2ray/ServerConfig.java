package free.svoss.tools.v2ray;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable representation of a v2ray server configuration (vmess, vless, trojan, or shadowsocks).
 * Constructed via {@link Builder}.
 */
public final class ServerConfig implements Comparable<ServerConfig> {

    private final String protocol;
    private final String address;
    private final int port;
    private final String id;
    private final int alterId;
    private final String security;
    private final String encryption;
    private final String transport;
    private final String path;
    private final String host;
    /** Server Name Indication — hostname sent in TLS ClientHello for SNI-based routing. */
    private final String sni;
    private final String flow;
    private final String fingerprint;
    /** Application-Lower Protocol Negotiation — comma-separated list of ALPN protocol names. */
    private final String alpn;
    /** Public key — used in Reality/TLS to verify the server's identity. */
    private final String pbk;
    /** Short ID — used in Reality to distinguish among multiple configurations on the same server. */
    private final String sid;
    private final String packetEncoding;
    private final String remark;
    private final String rawUrl;
    private final Map<String, String> extra;

    private ServerConfig(Builder b) {
        this.protocol = b.protocol;
        this.address = b.address;
        this.port = b.port;
        this.id = b.id;
        this.alterId = b.alterId;
        this.security = b.security;
        this.encryption = b.encryption;
        this.transport = b.transport;
        this.path = b.path;
        this.host = b.host;
        this.sni = b.sni;
        this.flow = b.flow;
        this.fingerprint = b.fingerprint;
        this.alpn = b.alpn;
        this.pbk = b.pbk;
        this.sid = b.sid;
        this.packetEncoding = b.packetEncoding;
        this.remark = b.remark;
        this.rawUrl = b.rawUrl;
        this.extra = Collections.unmodifiableMap(new LinkedHashMap<>(b.extra));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getProtocol() { return protocol; }
    public String getAddress() { return address; }
    public int getPort() { return port; }
    public String getId() { return id; }
    public int getAlterId() { return alterId; }
    public String getSecurity() { return security; }
    public String getEncryption() { return encryption; }
    public String getTransport() { return transport; }
    public String getPath() { return path; }
    public String getHost() { return host; }
    public String getSni() { return sni; }
    public String getFlow() { return flow; }
    public String getFingerprint() { return fingerprint; }
    public String getAlpn() { return alpn; }
    public String getPbk() { return pbk; }
    public String getSid() { return sid; }
    public String getPacketEncoding() { return packetEncoding; }
    public String getRemark() { return remark; }
    public String getRawUrl() { return rawUrl; }
    public Map<String, String> getExtra() { return extra; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerConfig)) return false;
        ServerConfig that = (ServerConfig) o;
        return port == that.port
                && Objects.equals(protocol, that.protocol)
                && Objects.equals(address, that.address)
                ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol, address, port);
    }

    @Override
    public int compareTo(ServerConfig other) {
        int cmp = Objects.compare(protocol, other.protocol, Comparator.nullsLast(Comparator.naturalOrder()));
        if (cmp != 0) return cmp;
        cmp = Objects.compare(address, other.address, Comparator.nullsLast(Comparator.naturalOrder()));
        if (cmp != 0) return cmp;
        return Integer.compare(port, other.port);
    }

    @Override
    public String toString() {
        return "ServerConfig{protocol='" + protocol + "', address='" + address
                + "', port=" + port + ", id='" + id + "', remark='" + remark + "'}";
    }

    public static final class Builder {

        private String protocol;
        private String address;
        private int port = -1;
        private String id;
        private int alterId;
        private String security;
        private String encryption;
        private String transport;
        private String path;
        private String host;
        private String sni;
        private String flow;
        private String fingerprint;
        private String alpn;
        private String pbk;
        private String sid;
        private String packetEncoding;
        private String remark;
        private String rawUrl;
        private final Map<String, String> extra = new LinkedHashMap<>();

        public Builder protocol(String value) { this.protocol = value; return this; }
        public Builder address(String value) { this.address = value; return this; }
        public Builder port(int value) { this.port = value; return this; }
        public Builder id(String value) { this.id = value; return this; }
        public Builder alterId(int value) { this.alterId = value; return this; }
        public Builder security(String value) { this.security = value; return this; }
        public Builder encryption(String value) { this.encryption = value; return this; }
        public Builder transport(String value) { this.transport = value; return this; }
        public Builder path(String value) { this.path = value; return this; }
        public Builder host(String value) { this.host = value; return this; }
        public Builder sni(String value) { this.sni = value; return this; }
        public Builder flow(String value) { this.flow = value; return this; }
        public Builder fingerprint(String value) { this.fingerprint = value; return this; }
        public Builder alpn(String value) { this.alpn = value; return this; }
        public Builder pbk(String value) { this.pbk = value; return this; }
        public Builder sid(String value) { this.sid = value; return this; }
        public Builder packetEncoding(String value) { this.packetEncoding = value; return this; }
        public Builder remark(String value) { this.remark = value; return this; }
        public Builder rawUrl(String value) { this.rawUrl = value; return this; }
        public Builder extra(String key, String value) { this.extra.put(key, value); return this; }

        public ServerConfig build() {
            if (address == null || address.trim().isEmpty())
                throw new IllegalStateException("ServerConfig address must not be null or blank");
            if (port < 1 || port > 65535)
                throw new IllegalStateException("ServerConfig port must be between 1 and 65535, got: " + port);
            if (protocol == null || (!protocol.equals("vmess") && !protocol.equals("vless")
                    && !protocol.equals("trojan") && !protocol.equals("ss"))) {
                throw new IllegalStateException("ServerConfig protocol must be one of vmess, vless, trojan, ss; got: " + protocol);
            }
            if (id == null || id.trim().isEmpty())
                throw new IllegalStateException("ServerConfig id must not be null or blank");
            if (protocol.equals("vmess")) {
                try {
                    UUID.fromString(id);
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("ServerConfig id must be a valid UUID for " + protocol + ", got: " + id);
                }
            }
            return new ServerConfig(this);
        }
    }
}
