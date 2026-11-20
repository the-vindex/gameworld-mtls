package net.gameworld.mtls;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/** Test cards made with the JDK's keytool at test time (no key material in the repo). */
final class TestKeys {
    record Pair(PrivateKey key, X509Certificate cert) {} // gitleaks:allow (type signature, not a secret)

    static Pair card(Path dir, String alias, String cn) throws Exception {
        Path ks = dir.resolve(alias + ".p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", alias, "-keyalg", "EC", "-groupname", "secp256r1",
                "-sigalg", "SHA256withECDSA", "-dname", "CN=" + cn + ",O=Gameworld players", "-validity", "14",
                "-keystore", ks.toString(), "-storetype", "PKCS12", "-storepass", "changeit")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) throw new IllegalStateException("keytool: " + out);
        KeyStore k = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(ks)) {
            k.load(in, "changeit".toCharArray());
        }
        return new Pair((PrivateKey) k.getKey(alias, "changeit".toCharArray()), (X509Certificate) k.getCertificate(alias));
    }

    private TestKeys() {}
}
