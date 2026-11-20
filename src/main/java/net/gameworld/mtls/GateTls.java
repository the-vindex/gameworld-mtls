package net.gameworld.mtls;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.security.GeneralSecurityException;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.net.ssl.CertPathTrustManagerParameters;

/**
 * Client TLS towards a gate. The only trust anchors are the Server-CA certificates shipped in
 * the pack (never the JVM's CA store, never anything from enrollment), and the gate must present
 * the name {@code gate.<host>} (standard HTTPS-style endpoint identification).
 */
public final class GateTls {
    private GateTls() {}

    public static SslHandler newHandler(ByteBufAllocator alloc, List<X509Certificate> serverCAs, CardStore.Card card,
                                        String gateName, int port) throws GeneralSecurityException, javax.net.ssl.SSLException {
        if (serverCAs.isEmpty()) throw new GeneralSecurityException("no Server-CA in the pack");
        Set<TrustAnchor> anchors = new HashSet<>();
        for (X509Certificate ca : serverCAs) anchors.add(new TrustAnchor(ca, null));
        PKIXBuilderParameters pkix = new PKIXBuilderParameters(anchors, null);
        pkix.setRevocationEnabled(false); // 14-day cards and a short gate cert; revocation is the allow list
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(new CertPathTrustManagerParameters(pkix));

        SslContext ctx = SslContextBuilder.forClient()
                .sslProvider(SslProvider.JDK)
                .protocols("TLSv1.3", "TLSv1.2")
                .trustManager(tmf)
                .keyManager(card.key(), card.cert())
                .build();
        SslHandler h = ctx.newHandler(alloc, gateName, port);
        SSLEngine engine = h.engine();
        SSLParameters p = engine.getSSLParameters();
        p.setEndpointIdentificationAlgorithm("HTTPS"); // the gate's cert must name gateName
        engine.setSSLParameters(p);
        h.setHandshakeTimeoutMillis(10_000);
        return h;
    }
}
