package net.gameworld.mtls;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class Pkcs10Test {
    /** Minimal DER walker: returns {tagOffset, contentOffset, contentLength}. */
    static int[] tlv(byte[] b, int off) {
        int len = b[off + 1] & 0xff, hdr = 2;
        if (len == 0x81) { len = b[off + 2] & 0xff; hdr = 3; }
        else if (len == 0x82) { len = ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff); hdr = 4; }
        return new int[]{off, off + hdr, len};
    }

    @Test
    void csrIsWellFormedAndSignedByTheKey() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = g.generateKeyPair();
        byte[] csr = Pkcs10.create(kp);

        int[] outer = tlv(csr, 0);
        assertEquals(0x30, csr[0]);
        assertEquals(csr.length, outer[1] + outer[2], "outer SEQUENCE covers everything");
        int[] info = tlv(csr, outer[1]);
        assertEquals(0x30, csr[info[0]]);
        int infoEnd = info[1] + info[2];
        int[] alg = tlv(csr, infoEnd);
        assertEquals(0x30, csr[alg[0]]);
        int[] sig = tlv(csr, alg[1] + alg[2]);
        assertEquals(0x03, csr[sig[0]]);
        assertEquals(0, csr[sig[1]], "no unused bits");
        assertEquals(csr.length, sig[1] + sig[2]);

        byte[] infoDer = Arrays.copyOfRange(csr, info[0], infoEnd);
        byte[] sigDer = Arrays.copyOfRange(csr, sig[1] + 1, sig[1] + sig[2]);
        Signature v = Signature.getInstance("SHA256withECDSA");
        v.initVerify(kp.getPublic());
        v.update(infoDer);
        assertTrue(v.verify(sigDer), "signature over CertificationRequestInfo verifies");

        byte[] spki = kp.getPublic().getEncoded();
        assertTrue(indexOf(infoDer, spki) > 0, "SubjectPublicKeyInfo is in the request");

        // Leave one for an external check (openssl req -verify) when asked.
        String out = System.getProperty("gw.csrOut");
        if (out != null) Files.write(Path.of(out), csr);
    }

    static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
}
