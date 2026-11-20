package net.gameworld.mtls;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;

/**
 * Minimal PKCS#10 certification request for an EC P-256 key, signed with ECDSA-SHA256.
 * Minecraft ships no BouncyCastle, so the DER is built by hand. The subject is empty: the enroll
 * service ignores everything in the CSR except the (verified) public key.
 */
public final class Pkcs10 {
    private Pkcs10() {}

    // 1.2.840.10045.4.3.2 ecdsa-with-SHA256
    private static final byte[] ECDSA_SHA256 = {0x06, 0x08, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x04, 0x03, 0x02};

    public static byte[] create(KeyPair kp) throws GeneralSecurityException {
        byte[] info = seq(cat(
                new byte[]{0x02, 0x01, 0x00},          // version 0
                new byte[]{0x30, 0x00},                // subject: empty Name
                kp.getPublic().getEncoded(),           // SubjectPublicKeyInfo (X.509 encoding)
                new byte[]{(byte) 0xa0, 0x00}));       // attributes [0] IMPLICIT SET OF, empty
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(kp.getPrivate());
        s.update(info);
        byte[] sig = s.sign();                         // already a DER ECDSA-Sig-Value
        return seq(cat(info, seq(ECDSA_SHA256), bitString(sig)));
    }

    static byte[] seq(byte[] content) { return tlv(0x30, content); }

    static byte[] bitString(byte[] content) {
        byte[] b = new byte[content.length + 1];
        System.arraycopy(content, 0, b, 1, content.length); // 0 unused bits
        return tlv(0x03, b);
    }

    static byte[] tlv(int tag, byte[] content) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(content.length + 6);
        o.write(tag);
        int n = content.length;
        if (n < 0x80) {
            o.write(n);
        } else if (n < 0x100) {
            o.write(0x81);
            o.write(n);
        } else if (n < 0x10000) {
            o.write(0x82);
            o.write(n >> 8);
            o.write(n);
        } else {
            throw new IllegalArgumentException("DER length too large");
        }
        o.writeBytes(content);
        return o.toByteArray();
    }

    static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.writeBytes(p);
        return o.toByteArray();
    }
}
