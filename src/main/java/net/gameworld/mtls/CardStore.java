package net.gameworld.mtls;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player cards on disk: {@code <base>/<host>/<uuid>/card.pem}, one file holding the private key
 * (PKCS#8) and the certificate, so key and card are always replaced together by one atomic
 * rename. The old card stays in place until the new one is completely written.
 */
public final class CardStore {
    public record Card(PrivateKey key, X509Certificate cert) { // gitleaks:allow (type signature)
        public Instant notAfter() { return cert.getNotAfter().toInstant(); }
        public boolean validAt(Instant t) {
            return !t.isBefore(cert.getNotBefore().toInstant()) && t.isBefore(notAfter());
        }
        public Duration remaining(Instant t) { return Duration.between(t, notAfter()); }
    }

    private static final Pattern UUID32 = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern HOST = Pattern.compile("[a-z0-9]([a-z0-9.-]{0,251}[a-z0-9])?");
    private static final Pattern PEM = Pattern.compile("-----BEGIN ([A-Z ]+)-----([A-Za-z0-9+/=\\s]+)-----END \\1-----");
    private static final int MAX_FILE = 64 * 1024;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("gameworld-mtls");
    private final Path base;

    public CardStore(Path base) { this.base = base; }

    public static String dashless(java.util.UUID u) {
        return u.toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    Path dir(String host, String uuid) {
        String h = host.toLowerCase(Locale.ROOT);
        if (!HOST.matcher(h).matches() || h.contains("..")) throw new IllegalArgumentException("bad host");
        if (!UUID32.matcher(uuid).matches()) throw new IllegalArgumentException("bad uuid");
        return base.resolve(h).resolve(uuid);
    }

    public Path cardFile(String host, String uuid) { return dir(host, uuid).resolve("card.pem"); }

    /** The stored card, or null if there is none or it can't be read. */
    public Card load(String host, String uuid) {
        Path f = cardFile(host, uuid);
        try {
            if (!Files.isRegularFile(f) || Files.size(f) > MAX_FILE) return null;
            return parse(Files.readString(f, StandardCharsets.US_ASCII));
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            LOG.warn("[gameworld-mtls] unreadable card {}: {}", f, e.toString());
            return null;
        }
    }

    public static Card parse(String pem) throws GeneralSecurityException {
        PrivateKey key = null;
        X509Certificate cert = null;
        Matcher m = PEM.matcher(pem);
        while (m.find()) {
            byte[] der = Base64.getMimeDecoder().decode(m.group(2));
            switch (m.group(1)) {
                case "PRIVATE KEY" -> key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
                case "CERTIFICATE" -> {
                    if (cert == null) cert = parseCert(der);
                }
                default -> { }
            }
        }
        if (key == null || cert == null) throw new GeneralSecurityException("card file needs a key and a certificate");
        return new Card(key, cert);
    }

    public static X509Certificate parseCert(byte[] der) throws GeneralSecurityException {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }

    /** Write key + card atomically (tmp file, fsync, rename). */
    public void install(String host, String uuid, PrivateKey key, X509Certificate cert) throws IOException, GeneralSecurityException { // gitleaks:allow (type signature)
        Path d = dir(host, uuid);
        Files.createDirectories(d);
        String text = pem("PRIVATE KEY", key.getEncoded()) + pem("CERTIFICATE", cert.getEncoded());
        Path tmp = d.resolve("card.pem.tmp");
        Files.deleteIfExists(tmp);
        try {
            Files.createFile(tmp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException e) { // Windows: no POSIX permissions; the profile dir is private anyway
            Files.createFile(tmp);
        }
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ch.write(java.nio.ByteBuffer.wrap(text.getBytes(StandardCharsets.US_ASCII)));
            ch.force(true);
        }
        // read it back before it replaces anything
        Card check = parse(Files.readString(tmp, StandardCharsets.US_ASCII));
        if (!check.cert().equals(cert)) throw new IOException("card did not read back");
        Path target = d.resolve("card.pem");
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }
}
