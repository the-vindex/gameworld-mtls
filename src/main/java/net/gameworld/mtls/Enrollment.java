package net.gameworld.mtls;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.resources.ResourceLocation;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import javax.security.auth.x500.X500Principal;

/**
 * The client half of {@code gameworld:enroll} (payload version 1):
 * <pre>
 *   query 1  [1, 1, uuid(16)]      answer [1, 1, CSR DER]
 *   query 2  [1, 2, card DER]      answer [1, 2, 1=installed | 0=failed]
 * </pre>
 * Answered only on connections to the pack's enroll address; everywhere else the query gets the
 * vanilla "unknown" answer. Trust never comes from here: the card is only ever used as our
 * client certificate, and the gate is verified against the pack's Server-CA.
 */
public final class Enrollment {
    public static final ResourceLocation CHANNEL = ResourceLocation.fromNamespaceAndPath("gameworld", "enroll");
    public static final int MAX_QUERY = 16 * 1024;
    private static final byte VERSION = 1, OP_CSR = 1, OP_CARD = 2;

    private Enrollment() {}

    /** Raw query bytes, captured instead of being discarded by vanilla. */
    public record Query(byte[] data) implements CustomQueryPayload {
        @Override public ResourceLocation id() { return CHANNEL; }
        @Override public void write(FriendlyByteBuf buf) { buf.writeBytes(data); }
    }

    public record Answer(byte[] data) implements CustomQueryAnswerPayload {
        @Override public void write(FriendlyByteBuf buf) { buf.writeBytes(data); }
    }

    /** Per-connection state, held by the connection (see ConnectionMixin). */
    public static final class State {
        KeyPair pending;
        String host;
        String uuid;
        public volatile boolean installed;
    }

    /**
     * @param match classification of the connection this query arrived on
     * @return the answer bytes, or null for "unknown" (vanilla behaviour)
     */
    public static byte[] answer(GwConfig.Match match, State st, byte[] q) {
        if (match == null || match.role() != GwConfig.Role.ENROLL || q.length < 2 || q[0] != VERSION) return null;
        String myUuid = CardStore.dashless(Minecraft.getInstance().getUser().getProfileId());
        try {
            if (q[1] == OP_CSR && q.length == 18) {
                String verified = HexFormat.of().formatHex(q, 2, 18);
                if (!verified.equals(myUuid)) {
                    GameworldMtls.LOGGER.warn("[gameworld-mtls] enroll: server verified a different account; not answering");
                    return null;
                }
                KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
                g.initialize(new ECGenParameterSpec("secp256r1"));
                st.pending = g.generateKeyPair();
                st.host = match.server().host;
                st.uuid = myUuid;
                byte[] csr = Pkcs10.create(st.pending);
                byte[] out = new byte[csr.length + 2];
                out[0] = VERSION;
                out[1] = OP_CSR;
                System.arraycopy(csr, 0, out, 2, csr.length);
                GameworldMtls.LOGGER.info("[gameworld-mtls] enroll: sent a key request for {}", myUuid);
                return out;
            }
            if (q[1] == OP_CARD) {
                boolean ok = install(st, Arrays.copyOfRange(q, 2, q.length), myUuid);
                return new byte[]{VERSION, OP_CARD, (byte) (ok ? 1 : 0)};
            }
        } catch (Exception e) {
            GameworldMtls.LOGGER.error("[gameworld-mtls] enroll failed", e);
            if (q[1] == OP_CARD) return new byte[]{VERSION, OP_CARD, 0};
        }
        return null;
    }

    private static boolean install(State st, byte[] der, String myUuid) throws Exception {
        if (st.pending == null || !myUuid.equals(st.uuid)) {
            GameworldMtls.LOGGER.warn("[gameworld-mtls] enroll: card without a pending key request");
            return false;
        }
        X509Certificate cert = CardStore.parseCert(der);
        if (!cert.getPublicKey().equals(st.pending.getPublic())) {
            GameworldMtls.LOGGER.warn("[gameworld-mtls] enroll: card is not for our key");
            return false;
        }
        if (!myUuid.equals(commonName(cert.getSubjectX500Principal()))) {
            GameworldMtls.LOGGER.warn("[gameworld-mtls] enroll: card names another account");
            return false;
        }
        cert.checkValidity();
        GameworldMtls.store().install(st.host, st.uuid, st.pending.getPrivate(), cert);
        st.installed = true;
        st.pending = null;
        GameworldMtls.LOGGER.info("[gameworld-mtls] enroll: card installed for {} on {}, valid until {}", myUuid, st.host,
                Instant.ofEpochMilli(cert.getNotAfter().getTime()));
        return true;
    }

    static String commonName(X500Principal p) {
        for (String part : p.getName(X500Principal.RFC2253).split(",")) {
            if (part.startsWith("CN=")) return part.substring(3);
        }
        return null;
    }
}
