package net.gameworld.mtls;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * What the pack ships in {@code config/gameworld-mtls/}: {@code servers.json} (which addresses
 * are the gate and the enroll entry of which host) and {@code server-ca.pem} (the only trust
 * anchor for gates). Nothing here is ever taken from a server.
 */
public final class GwConfig {
    public static final class Server {
        public String name = "Gameworld";
        public String host;
        public int gatePort = 25565;
        public int enrollPort = 25566;
        /** TLS name of the gate; default {@code gate.<host>}. */
        public String gateName;

        public String gateName() {
            return gateName != null && !gateName.isBlank() ? gateName.toLowerCase(Locale.ROOT) : "gate." + host;
        }
    }

    private static final class FileFormat {
        List<Server> servers = new ArrayList<>();
        int renewDays = 5;
    }

    public enum Role { NONE, GATE, ENROLL }

    public record Match(Role role, Server server) {
        static final Match NONE = new Match(Role.NONE, null);
    }

    private final List<Server> servers;
    private final int renewDays;
    private final List<X509Certificate> serverCAs;
    private final String error;

    private GwConfig(List<Server> servers, int renewDays, List<X509Certificate> cas, String error) {
        this.servers = servers;
        this.renewDays = renewDays;
        this.serverCAs = cas;
        this.error = error;
    }

    public static GwConfig load(Path dir) {
        List<Server> servers = new ArrayList<>();
        int renew = 5;
        List<X509Certificate> cas = new ArrayList<>();
        String err = null;
        try {
            Path sj = dir.resolve("servers.json");
            if (Files.isRegularFile(sj)) {
                FileFormat f = new Gson().fromJson(Files.readString(sj, StandardCharsets.UTF_8), FileFormat.class);
                if (f != null && f.servers != null) {
                    for (Server s : f.servers) {
                        if (s == null || s.host == null || s.host.isBlank()) continue;
                        s.host = s.host.trim().toLowerCase(Locale.ROOT);
                        servers.add(s);
                    }
                    renew = Math.max(1, Math.min(13, f.renewDays));
                }
            }
        } catch (IOException | JsonParseException e) {
            err = "servers.json: " + e.getMessage();
        }
        try {
            Path ca = dir.resolve("server-ca.pem");
            if (Files.isRegularFile(ca)) {
                try (var in = Files.newInputStream(ca)) {
                    Collection<? extends java.security.cert.Certificate> c = CertificateFactory.getInstance("X.509").generateCertificates(in);
                    for (var x : c) cas.add((X509Certificate) x);
                }
            }
            if (cas.isEmpty() && !servers.isEmpty()) err = "server-ca.pem missing or empty";
        } catch (IOException | GeneralSecurityException e) {
            err = "server-ca.pem: " + e.getMessage();
            cas.clear();
        }
        return new GwConfig(List.copyOf(servers), renew, List.copyOf(cas), err);
    }

    public List<Server> servers() { return servers; }
    public int renewDays() { return renewDays; }
    public List<X509Certificate> serverCAs() { return serverCAs; }
    /** Non-null if the pack config is broken; gate connections then fail closed. */
    public String error() { return error; }

    /** Classify a "host[:port]" address as typed in the server list. */
    public Match match(String address) {
        if (address == null) return Match.NONE;
        String a = address.trim().toLowerCase(Locale.ROOT);
        String host = a;
        int port = 25565;
        int colon = a.lastIndexOf(':');
        if (colon > 0 && a.indexOf(':') == colon) { // ignore IPv6 literals
            host = a.substring(0, colon);
            try {
                port = Integer.parseInt(a.substring(colon + 1));
            } catch (NumberFormatException e) {
                return Match.NONE;
            }
        }
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return match(host, port);
    }

    public Match match(String host, int port) {
        if (host == null) return Match.NONE;
        String h = host.toLowerCase(Locale.ROOT);
        for (Server s : servers) {
            if (!s.host.equals(h)) continue;
            if (port == s.gatePort) return new Match(Role.GATE, s);
            if (port == s.enrollPort) return new Match(Role.ENROLL, s);
        }
        return Match.NONE;
    }
}
